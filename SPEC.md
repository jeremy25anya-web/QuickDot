# QuickDot — Build Specification (v1)

> An Amartian product. This is the authoritative build spec for the production
> version of QuickDot. It is written to be handed to Claude Code. Follow the
> staged plan; log every non-trivial decision as an ADR in `DECISIONS.md` and
> update `ARCHITECTURE.md` / `KNOWLEDGE.md` as you go, per the Amartian repo
> convention.

---

## 1. What QuickDot is

The fastest way to move information between two people or two devices. Open a
room, share a short code or link, and exchange text, links, images, or files in
real time. Close the room (or let it expire) and everything is gone. No
accounts, no downloads, no history.

**Product promise:** remove friction from sharing information, and store nothing.

### Design principles
1. **Ephemeral by default.** Content is relayed, never persisted. When a room
   ends, it leaves no trace on the server.
2. **No identity.** No sign-up, no login, no personal profile. Participants get
   a throwaway fun name per room.
3. **Robust, not fragile.** Every limit is enforced on the **server**; the
   client is never trusted. Abuse is designed against from day one.
4. **Calm and minimal.** The UI is quiet and precise. One accent color. The
   reference implementation is the approved front-end prototype (`quickdot.html`).

### Non-goals for v1 (explicitly out of scope)
- Accounts, contacts, or persistent message history.
- Rooms with more than a small handful of participants.
- Cross-device sync of a single user's clipboard.
- WebRTC peer-to-peer transfer (v2 candidate).
- Redis / horizontal scaling (v2 — v1 is a single instance).
- SMS or email notifications.
- Mobile native apps.

---

## 2. Architecture & hosting

QuickDot cannot deploy as a pure static site, because the backend must hold open
WebSocket connections in a long-running process. The topology is two pieces:

```
   Browser
     │  https
     ▼
 ┌───────────────────────────┐        ┌──────────────────────────────┐
 │  Frontend (Angular)       │  wss   │  Backend (Spring Boot, Java)  │
 │  static build             │───────▶│  long-running process         │
 │  Cloudflare Pages         │        │  Fly.io (always-on machine)   │
 │  quickdot.amartian.ca     │        │  api.quickdot.amartian.ca     │
 └───────────────────────────┘        └──────────────────────────────┘
        Cloudflare DNS + Web Analytics (no cookies)
```

- **Frontend:** Angular static bundle on **Cloudflare Pages**, subdomain
  `quickdot.amartian.ca` (CNAME on existing Cloudflare zone).
- **Backend:** Spring Boot on **Fly.io**, subdomain `api.quickdot.amartian.ca`.
  Use a small **always-on** machine — do **not** use a scale-to-zero / free tier
  that cold-starts, because a 30s+ JVM wake on first visit is a bad portfolio
  impression. (Railway is an acceptable simpler alternative; a Hetzner VPS is the
  more-ops-credible alternative. Keep the Dockerfile host-agnostic so this is
  swappable.)
- **Analytics:** Cloudflare Web Analytics — no cookies, no consent banner
  required, aggregate only.
- **No database, no Redis, no object storage in v1.** All room state and all
  in-flight content live in server memory only.

---

## 3. Tech stack

| Layer      | Choice | Notes |
|------------|--------|-------|
| Language   | Java 21 | Matches job-search target (Spring Boot backend). |
| Framework  | Spring Boot 3.x | |
| Realtime   | **Raw Spring WebSocket** (`WebSocketHandler`) with a small JSON control protocol + binary frames | Chosen over STOMP: simpler, lower-level, and a better learning/interview story for a relay tool. STOMP is a v2 option if a broker is ever needed. |
| Build      | Maven | Common in the enterprise Java market being targeted. Gradle is acceptable if preferred. |
| MIME sniff | Apache Tika (or equivalent magic-byte sniffer) | For server-side file-type verification. |
| Frontend   | Angular (latest), standalone components, TypeScript | This is the frontend study project — port the prototype UI faithfully. |
| Monitoring | Sentry (free tier) | Errors only, no PII. |

### Repository layout (monorepo)
```
quickdot/
  backend/        Spring Boot app (modular monolith, packages by feature)
    room/         room registry, lifecycle, TTL
    ws/           websocket handler, session tracking, protocol
    relay/        text + file relay, chunking
    safety/       rate limiting, size/type/MIME enforcement, origin/CORS
    web/          health check, config, security headers
  frontend/       Angular app
  docs/
    ARCHITECTURE.md
    DECISIONS.md      (ADR log — append an entry per real decision)
    KNOWLEDGE.md      (ledger; Jeremy triages manually)
    IDEAS.md
  CLAUDE.md           (agent working rules)
  legal/
    PRIVACY-POLICY.md
    TERMS-OF-SERVICE.md
  ACCESSIBILITY.md
```

---

## 4. Domain model (in-memory)

- **Room**
  - `code` — 6 chars from `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no ambiguous chars).
  - `createdAt`, `expiresAt = createdAt + 30 min` (**fixed at creation**).
  - `participants` — map of sessionId → Participant.
  - `itemCount` — running count for the per-room item cap.
- **Participant**
  - `id` (server-generated), `name` (server-assigned fun name), `wsSession`, `ip`.
- **Item** (text or file) — **transient**, relayed to other participants, never
  stored after relay. Only metadata may be logged (see §6).

The room registry is a single `ConcurrentHashMap<code, Room>` with a scheduled
sweeper that evicts expired rooms and empty rooms past the grace window.

---

## 5. WebSocket protocol

One connection per participant: `wss://api.quickdot.amartian.ca/ws/{roomCode}`.
The **server** is authoritative for presence, names, and the room clock — a late
joiner does not compute its own expiry, the server sends it.

**Control messages (JSON, text frames):**

| Direction | type | Payload |
|-----------|------|---------|
| server→client | `welcome` | `{ you:{id,name}, participants:[{id,name}], expiresAt }` |
| server→client | `joined` | `{ id, name }` (someone else joined) |
| server→client | `left` | `{ id }` |
| client→server | `text` | `{ text }` |
| server→client | `text` | `{ from:{id,name}, id, text }` |
| client→server | `file-start` | `{ transferId, name, mime, size }` |
| client→server | `file-chunk` | binary frame, prefixed/framed with `transferId` + sequence |
| client→server | `file-end` | `{ transferId }` |
| server→client | `file-*` | relayed equivalents carrying `from` |
| both | `ping` / `pong` | keepalive |
| server→client | `error` | `{ code, message }` (e.g. `FILE_TOO_LARGE`, `TYPE_BLOCKED`, `RATE_LIMITED`) |

**File transfer:** chunked (256 KB chunks). The server relays chunks
straight through to other participants without buffering the whole file, and
rejects the transfer as soon as declared `size` > 25 MB or cumulative bytes
exceed the cap. Configure the WebSocket container's max binary/text buffer sizes
to accommodate one chunk plus overhead.

Fun names are generated server-side from two word lists (adjective + creature),
matching the prototype. Keep the lists in one place; the "alien mode" variant is
a straight list swap.

---

## 6. Safety & abuse hardening (the core backend work)

**Everything the client checks is re-checked on the server. The client is never
trusted.** These are the "robust, not fragile" requirements.

### Enforced limits (defaults — tune after load-testing)

| Rule | Default |
|------|---------|
| Max file size | 25 MB (hard reject) |
| Chunk size | 256 KB |
| Blocked extensions | `exe bat cmd com msi scr ps1 sh bash vbs vbe jar apk deb dmg pkg app gadget cpl msc wsf hta reg` |
| MIME verification | Magic-byte sniff; reject if sniffed type is executable or contradicts a claimed-safe type |
| Room creation | ≤ 20 per IP per hour |
| Concurrent rooms per IP | ≤ 5 |
| Max participants per room | 8 |
| Max items per room | 200 |
| Text messages | ≤ 30 per connection per 10s, then throttle |
| Uploads | ≤ 10 per room per minute; 1 concurrent upload per connection |
| Room TTL | 30 min from creation (global, not per-joiner) |
| Idle timeout | ping every 20s; drop after ~2 missed pongs |
| Empty-room grace | reclaim 30s after last participant leaves |

### Network hardening
- **WebSocket origin check**: reject handshakes whose `Origin` is not
  `https://quickdot.amartian.ca`.
- **CORS** locked to the frontend origin for any REST endpoints.
- **Security headers** on all responses: `Content-Security-Policy`,
  `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` /
  `frame-ancestors 'none'`, a minimal `Permissions-Policy`.

### Logging & abuse response
- **Never log message or file content.** Metadata only: timestamp, room code,
  a hashed/truncated IP, file hash + size + type. Retention: 30 days, then purge.
- **Abuse/takedown path:** publish `{{ABUSE_EMAIL}}` (e.g. `abuse@amartian.ca`)
  and honor reports promptly.
- **CSAM — mandatory-reporting note (Canada):** operators have legal reporting
  obligations. v1 ships the reporting path + contact and the metadata needed to
  respond to law enforcement. Automated hash-scanning (e.g. PhotoDNA) is **not**
  in v1 — this gap is documented deliberately and is the top v2 safety item. Do
  not represent the service as scanning content until it does.

---

## 7. Reliability & polish
- WebSocket disconnect/reconnect handling with a visible "reconnecting…" state.
- Real error and empty states (never a blank screen — see the prototype copy).
- `GET /healthz` → 200 for uptime checks.
- Favicon, Open Graph / social preview tags, a real 404 page.
- Sentry wired on both frontend and backend (no PII in events).

---

## 8. Staged build plan

Build in isolated stages. Each stage has acceptance criteria and ends with an
ADR entry. Do not start a stage until the previous one meets its criteria.

**Stage 0 — Scaffold & deploy skeleton.** Monorepo, Spring Boot app with
`/healthz`, Dockerfile, Angular app shell, CI, deploy backend to Fly.io and
frontend to Cloudflare Pages with the two subdomains live.
_Done when:_ both subdomains serve over HTTPS and `/healthz` returns 200 in prod.

**Stage 1 — Room domain.** In-memory registry, code generation, create/join,
`expiresAt` fixed at creation, scheduled sweeper for expiry + empty rooms.
_Done when:_ rooms can be created/joined and expire correctly under unit tests.

**Stage 2 — Presence & clock.** WebSocket connect, server-assigned fun names,
participant list, join/left broadcasts, `welcome` with server `expiresAt`,
ping/pong keepalive + idle drop.
_Done when:_ two clients see each other join/leave and share one countdown.

**Stage 3 — Text relay.** Text/link messages relayed with sender identity;
per-connection message rate limit.
_Done when:_ two clients exchange text in real time; flooding is throttled.

**Stage 4 — File relay + safety.** Chunked binary transfer relayed in memory;
server-side size cap, extension blocklist, MIME sniff, upload rate limits.
_Done when:_ ≤25 MB allowed files transfer; a renamed `.exe`, an oversized file,
and an upload flood are all rejected server-side with correct `error` codes.

**Stage 5 — Angular UI.** Faithful port of the prototype: landing (create/join),
room (live-link signature, code + copy code/link, countdown, feed, composer with
attach + paste), fun names, per-message ⋯ menu (copy / download / report),
auto-rejoin on refresh, wipe on close.
_Done when:_ the app matches the prototype's behavior and states end-to-end.

**Stage 6 — Hardening.** Origin check, CORS, security headers, reconnect UI,
metadata-only logging with retention, Sentry, load-test the limits and tune.
_Done when:_ a hostile-input pass (bad origin, oversized/oversent, wrong types)
fails safely and nothing sensitive is logged.

**Stage 7 — Launch readiness.** Wire in Privacy Policy + Terms pages, Cloudflare
Web Analytics, favicon/OG/404, and complete the accessibility pass in
`ACCESSIBILITY.md` (contrast fixes, 44px targets, screen-reader announcements).
_Done when:_ the Definition of Done below is fully met.

---

## 9. Definition of done (launch gate)
- [ ] Two different devices exchange text, images, and files in real time.
- [ ] All §6 limits enforced and verified server-side.
- [ ] Content is never written to disk or logged; metadata retention works.
- [ ] Origin/CORS/security headers in place; abuse contact published.
- [ ] Privacy Policy + Terms live and linked; both reviewed by a lawyer.
- [ ] WCAG 2.0 AA pass complete (see `ACCESSIBILITY.md`), including the
      contrast fixes below.
- [ ] Always-on backend (no cold start); `/healthz` monitored; Sentry live.
- [ ] Favicon, OG tags, 404 present.

---

## 10. Deferred to v2 (log in `IDEAS.md`)
Redis + horizontal scaling · WebRTC P2P transfer + TURN · PhotoDNA/hash-based
content scanning · optional room passwords · larger group rooms · SMS/email
share · Quebec Law 25 review if marketing there.

---

## Placeholders to fill before launch
`{{CONTACT_EMAIL}}` · `{{ABUSE_EMAIL}}` · `{{EFFECTIVE_DATE}}` ·
`{{BUSINESS_STRUCTURE}}` (sole proprietorship vs incorporated) · final Fly.io
region.
