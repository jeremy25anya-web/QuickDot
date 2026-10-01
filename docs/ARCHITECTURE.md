# Architecture

Living document — update as stages land. See `SPEC.md` for the authoritative
product/behavior spec; this file tracks the *as-built* system.

## Topology

```
   Browser
     │  https
     ▼
 ┌───────────────────────────┐        ┌──────────────────────────────┐
 │  Frontend (Angular)       │  wss   │  Backend (Spring Boot, Java)  │
 │  static build             │───────▶│  long-running process         │
 │  Cloudflare Workers       │        │  Fly.io (always-on machine)   │
 │  quickdot.amartian.ca     │        │  api.quickdot.amartian.ca     │
 └───────────────────────────┘        └──────────────────────────────┘
```

- No database, no Redis, no object storage. All room state and in-flight
  content live in backend process memory only, per spec §2.
- Single Fly.io machine (v1 explicitly forgoes horizontal scaling).

## Repository layout

```
quickdot/
  backend/        Spring Boot app (modular monolith, packages by feature)
    room/         room registry, lifecycle, TTL              [Stage 1]
    ws/           websocket handler, session tracking, protocol,
                  origin lock                                [Stage 2/6]
    relay/        text + file relay, chunking, rate limits,
                  size/type/MIME enforcement                 [Stage 3-4]
    audit/        metadata-only logging (IP hashing, file digest) [Stage 6]
    web/          health check, config, CORS, security headers [Stage 0/6]
  frontend/       Angular app (standalone components)
  docs/
    ARCHITECTURE.md   this file
    DECISIONS.md      ADR log
    KNOWLEDGE.md      ledger — Jeremy triages manually
    IDEAS.md          deferred / v2 ideas
    DEPLOY.md         deploy runbook (manual steps requiring account access)
  CLAUDE.md           agent working rules
  legal/
    PRIVACY-POLICY.md
    TERMS-OF-SERVICE.md
  ACCESSIBILITY.md
  .github/workflows/  CI (build+test) and deploy (gated, opt-in)
```

## Backend

- Java 21, Spring Boot 4.1 (see ADR-0001), Maven (wrapper committed, no local
  Maven install required).
- `GET /healthz` (`web` package) — plain 200 JSON, independent of Actuator, so
  it stays stable regardless of what Actuator exposes. Actuator's own
  `/actuator/health` is also present (management endpoint exposure limited to
  `health` only) but is not the contract the spec/Fly health checks use.
- `server.port` reads `${PORT:8080}` so it works both locally and on Fly
  (which injects `PORT`).

### Room domain [Stage 1]

- `room.Room` — code, `createdAt`, `expiresAt` (fixed at construction — never
  extended by later joins, per spec §4), a `ConcurrentHashMap` of
  participants, and an `itemCount` counter for the per-room item cap
  (enforced starting Stage 4).
- `room.Participant` — currently just `id` + `ip`. `name` and the WebSocket
  session attach in Stage 2 once there's a transport to hang them off; adding
  those fields now would be speculative.
- `room.RoomCodeGenerator` — 6-char codes from the no-ambiguous-chars alphabet
  (`ABCDEFGHJKMNPQRSTUVWXYZ23456789`), matching both the spec and the
  prototype's `genCode()`.
- `room.RoomRegistry` (`@Service`) — the single `ConcurrentHashMap<code, Room>`
  registry. `createRoom`/`findActiveRoom`/`join`/`leave`, plus a
  `@Scheduled` `sweep()` (default every 10s) that evicts expired rooms and
  rooms empty past the 30s grace window. Takes a `Clock` bean
  (`web.AppConfig`) rather than `Instant.now()` directly, so tests can drive
  time deterministically instead of sleeping.
- Room creation/participant IP-based rate limiting (spec §6: ≤20 rooms/IP/hr,
  ≤5 concurrent rooms/IP) is deliberately **not** implemented yet — it's
  Stage 6 (Hardening) scope, since it needs the IP-tracking layer that stage
  introduces. `Participant.ip` is captured now so that layer has something to
  key off later.
- `room.RoomController` — `POST /api/rooms` → `{code, expiresAt}`. Not in the
  spec's own protocol description but required for the app to function at
  all (see ADR-0007); this is also where the Stage 6 per-IP creation limit
  will attach.

### WebSocket presence [Stage 2]

- `ws.QuickDotWebSocketHandler` (`extends TextWebSocketHandler`) — one
  connection per participant at `/ws/{roomCode}`. On connect: looks up the
  room, joins it via `RoomRegistry`, sends `welcome` (self + current
  participants + the room's `expiresAt`), then broadcasts `joined` to
  everyone else. On disconnect: leaves the room and broadcasts `left`.
  Rejects with an `error` message (`ROOM_NOT_FOUND` / `ROOM_FULL`) and closes
  if the room doesn't exist or is full. Binary frames aren't handled yet —
  that's Stage 4, at which point this handler will need to stop extending
  `TextWebSocketHandler`.
- `ws.RoomCodeHandshakeInterceptor` — pulls the room code out of the request
  path into a session attribute before handshake, since Spring's WebSocket
  registry doesn't do MVC-style `@PathVariable` extraction.
- `ws.SessionRegistry` — live `WebSocketSession` by participant id, plus
  last-pong timestamps. Deliberately separate from `RoomRegistry` — the room
  domain stays transport-agnostic; this is the only place that knows about
  `WebSocketSession`.
- `ws.FunNameGenerator` — adjective+creature names, word lists copied
  verbatim from the prototype's `ADJ`/`CREA` arrays (spec §5).
- `ws.KeepAliveScheduler` — `@Scheduled` ping every `ws.ping-interval-seconds`
  (default 20s); drops any session that hasn't ponged within 2 intervals.
  Dropping just calls `session.close()` — cleanup and the `left` broadcast
  happen through the handler's normal `afterConnectionClosed`, so there's one
  disconnect code path, not two.
- `ws.WebSocketConfig` — registers the handler at `/ws/*` with
  `setAllowedOriginPatterns("*")`. **Temporary**: spec §6 requires locking
  this to the exact production origin; it's permissive now only because local
  dev and the Cloudflare Tunnel put the Angular dev server on a different
  origin than the backend. Must be tightened in Stage 6 — tracked via a
  `TODO(Stage 6)` comment at the call site, not yet in `KNOWLEDGE.md` since
  it's already spec'd as Stage 6 scope.
- **Jackson 3, not 2** (see ADR-0006): Spring Boot 4.1 ships
  `tools.jackson.databind`, not `com.fasterxml.jackson.databind`. Every
  JSON-touching class in this repo imports from `tools.jackson.*`.

### Text relay [Stage 3]

- `relay.TextRateLimiter` — sliding-window per-participant limit (default 30
  messages / 10s, spec §6). Lives in `relay`, not `ws`, since chunked file
  relay (Stage 4) will need its own rate limiting alongside it.
- The handler's `"text"` case in `handleTextMessage`: checks the rate limiter,
  then `Room.tryIncrementItemCount(maxItems)` (the per-room 200-item cap,
  spec §6 — enforced now because it's a direct use of the counter Stage 1
  already built, not new infrastructure), then relays
  `ws.protocol.OutgoingTextMessage` to every *other* participant. The sender
  is not echoed back — matches the prototype, which relies on the sender's
  own optimistic local UI update rather than a server round-trip.

### File relay + safety [Stage 4]

- `ws.QuickDotWebSocketHandler` now extends `AbstractWebSocketHandler`
  directly (not `TextWebSocketHandler`) and overrides both
  `handleTextMessage` (for `file-start`/`file-end`, JSON) and
  `handleBinaryMessage` (for `file-chunk`).
- `relay.ChunkFrame` — the binary wire format for a chunk: 4-byte transferId
  length + transferId UTF-8 + 8-byte sequence (long) + payload. Encode/decode
  are symmetric; used identically for client→server and the relayed
  server→client copy.
- `relay.FileTransfer` / `relay.FileTransferRegistry` — in-memory per-upload
  state (sender, declared size, bytes received so far, last sequence, "have
  I sniffed the first chunk yet"). Never buffers file bytes itself — only
  counters. `hasActiveUpload(participantId)` enforces the 1-concurrent-upload
  per connection limit (spec §6).
- `relay.BlockedExtensions` — the spec §6 extension blocklist, checked at
  `file-start` before any bytes are accepted.
- `relay.MimeSniffer` — wraps `org.apache.tika.Tika` (tika-core only, no
  parsers) for magic-byte detection. Runs once, on the first chunk of a
  transfer (the earliest point actual bytes exist — `file-start` carries
  none), against a curated dangerous-type set rather than a full
  extension↔MIME matrix (see ADR-0008). This is the one place a chunk is
  briefly inspected in memory; it's never retained or written to disk, and
  the "never buffer the whole file" rule still holds since only that single
  ≤256 KB chunk is touched.
- `relay.UploadRateLimiter` — sliding window scoped **per room** (spec §6:
  ≤10 uploads/room/minute), unlike `TextRateLimiter` which is per connection.
- Flow: `file-start` validates (size, extension, concurrent-upload,
  room upload-rate, room item-cap) and either relays the start to other
  participants or sends an `error` to the sender and creates no transfer
  state. `file-chunk` re-validates cumulative size against both the declared
  size and the hard 25 MB cap, sniffs only the first chunk, and relays
  straight through — no reassembly, no disk. `file-end` relays and frees the
  upload slot. Any rejection mid-transfer removes the transfer server-side
  and errors the sender; peers simply see the transfer stop (spec doesn't
  define a cancel/abort broadcast, so none was added).
- `ws.WebSocketConfig` now also declares a `ServletServerContainerFactoryBean`
  sizing the container's max binary/text message buffers to fit one 256 KB
  chunk plus framing overhead (spec §5). This bean needs a real JSR-356
  container, which is why `QuickdotApplicationTests` runs with
  `webEnvironment = RANDOM_PORT` instead of the default mock environment
  (ADR-0009).

### Hardening [Stage 6]

- **Origin lock**: `ws.WebSocketConfig.setAllowedOrigins(...)` and
  `web.AppConfig.addCorsMappings` both read one `app.allowed-origin`
  property (ADR-0012) instead of the Stage 2/5 `*`/permissive stopgap.
  Defaults to the Angular dev origin locally; `APP_ALLOWED_ORIGIN` overrides
  it everywhere else (`docs/DEPLOY.md`). Verified end-to-end (not just unit
  tests against the handler) in `ws.OriginCheckIntegrationTest`, since the
  check runs in Spring's handshake interceptor before the handler runs.
- **Security headers**: `web.SecurityHeadersFilter`, a plain
  `jakarta.servlet.Filter` `@Component`, sets `Content-Security-Policy`
  (`default-src 'none'`, since this backend never serves HTML — only JSON
  and WebSocket upgrades), `Strict-Transport-Security`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `X-Frame-Options: DENY`, and a minimal `Permissions-Policy` on every
  response (spec §6).
- **Metadata audit log** (`audit` package, ADR-0013): `audit.AuditLog` logs
  `room_created`, `participant_joined`, and `file_relayed` events (timestamp,
  room code, hashed IP, and for files, a SHA-256 + size + MIME type) through
  a dedicated `ca.amartian.quickdot.audit` SLF4J logger. `audit.IpHasher`
  salts and truncates IPs (`app.ip-hash-salt` / `APP_IP_HASH_SALT`) so the
  raw address is never written to disk. `logback-spring.xml` routes that
  logger to its own rolling file (`logs/audit.log`, daily rotation,
  30-day `maxHistory`, matching spec §6's stated retention) separate from
  general application logs. `relay.FileTransfer` carries a running
  `MessageDigest` (updated per chunk, finalized once at `file-end`) so the
  file hash never requires buffering the file itself.
- **Error monitoring (Sentry, ADR-0017)**: backend uses
  `sentry-spring-boot-4-starter` + `sentry-logback` (ERROR logs → events,
  WARN → breadcrumbs; the audit logger never reaches Sentry because of
  `additivity="false"`). `monitoring.SentryScrubber` is the before-send and
  before-breadcrumb hook: drops request/user/server name, replaces every
  exception message with `[redacted]`, and rewrites room-code paths
  (`/ws/`, `/r/`, `/api/rooms/`) to `:code`. Frontend mirrors this in
  `core/sentry.ts` (`@sentry/angular`, Angular `ErrorHandler`), with console,
  DOM-click and session integrations removed. Both sides are disabled when
  the DSN is empty (`SENTRY_DSN` Fly secret / `environment.sentryDsn`).
- **Load/hostile-input test**: `tools/loadtest/loadtest.py` attacks each
  §6 limit from a real client and streams full-size files through N rooms
  while sampling `/healthz`. Prod run 2026-09-28: all 10 §6 checks pass;
  3 × 25 MB concurrent transfers at 5.1-5.9 MB/s each, `/healthz` median
  83 ms / max 553 ms, no OOM on the 512 MB machine. Uses certifi's CA
  bundle when installed (this dev machine's Windows store fails LE chains).
- **Client IP + per-IP room limits (ADR-0018)**: `web.ClientIpResolver`
  returns Fly's `Fly-Client-IP` header when `app.trust-fly-client-ip` is on
  (only in fly.toml), else the socket peer. Used by `RoomController` and,
  via `RoomCodeHandshakeInterceptor`'s `clientIp` session attribute, by the
  WebSocket handler, so audit hashes and limits see the real user.
  `room.RoomCreationLimiter` wraps room creation: ≤5 unexpired rooms per
  hashed IP (`Room.creatorKey`) and ≤20 creations per sliding hour, HTTP
  429 `{code: "ROOM_LIMIT", message}` otherwise. Idle keys are evicted every
  10 min. The landing page shows the server's message on a 429.

## Frontend

- Angular (standalone components, no NgModules), SCSS, `ssr=false` (static
  build only — this is a Cloudflare Workers static-assets deploy, no Angular
  Universal/SSR server). No `zone.js` — the app is zoneless, so all reactive
  state is Angular signals (WebSocket callbacks and the TTL interval write to
  signals directly; there's no zone patching to fall back on).
- Build output: `frontend/dist/frontend/browser` — served by Cloudflare
  Workers static assets per `frontend/wrangler.jsonc`, with SPA fallback so
  `/r/:code` deep links return `index.html` (ADR-0016).

### Room UI [Stage 5]

- `core/room.service.ts` (`providedIn: 'root'`) — owns the single active
  room's `WebSocket` and all its state as signals (`you`, `participants`,
  `items`, `expiresAt`, `connectionState`, `fatalError`, `uploading`). One
  room is active app-wide at a time, matching the backend's
  one-connection-per-participant model; `connect(code)` resets prior state.
  Mirrors `ws.protocol.*` message shapes exactly (`core/protocol.ts`) and the
  binary chunk framing (`core/chunk-frame.ts` mirrors
  `relay.ChunkFrame`) byte-for-byte.
- `core/room-api.service.ts` — `POST /api/rooms` via `HttpClient`.
- Routing carries the room code (`/r/:code`), not a URL hash like the
  prototype — this gets "auto-rejoin on refresh" for free (reloading a
  `/r/:code` tab just reconnects, as a new participant; there's no session
  token in spec §5 to resume an old one, which matches "no accounts, no
  identity").
- File send: chunks the `File` into 256 KB slices client-side (matching
  `relay.file.chunk-size-bytes`), sends `file-start`, then one binary
  `ChunkFrame` per slice, then `file-end`. File receive: buffers chunks by
  `transferId` in memory only until `file-end`, then assembles one `Blob` —
  never rendered progressively, since neither the spec nor the prototype
  define a partial-transfer UI state.
- Per-message ⋯ menu (copy/download/report; Report drafts a metadata-only
  `mailto:` to the abuse inbox via `core/report.ts`, ADR-0021) is positioned with CSS
  (`position:absolute` within the message bubble) rather than the
  prototype's JS bounding-rect calculation — same behavior, no
  `ElementRef`/measurement code needed.
- Client-side file-size/extension checks (`core/limits.ts`) mirror the
  server's spec §6 limits for immediate UX feedback; the server re-checks
  everything regardless (the client is never trusted).
- Global design tokens (the prototype's `:root` CSS variables, Google Fonts)
  live in `src/styles.scss` / `src/index.html`; per-page styles are ported
  into each component's `.scss` faithfully from `quickdot.html`.

### Reconnect UI [Stage 6]

- `core/room.service.ts` tracks `intentionalClose` and a reconnect-attempt
  counter (ADR-0014). An unrequested socket close/error — not `leave()`, not
  a fatal `ROOM_NOT_FOUND`/`ROOM_FULL` rejection — sets `connectionState` to
  `'reconnecting'` and retries the same room code with capped exponential
  backoff (1s/2s/4s/8s, then holding at 8s), without clearing `items` or
  `participants` the way a fresh `connect()` does.
- `room/room.html` shows a blinking "Reconnecting…" label in place of the
  presence line while `connectionState() === 'reconnecting'`, and disables
  the composer's Send/Attach buttons whenever `connectionState() !== 'open'`
  so a message typed mid-reconnect isn't silently swallowed.
- No separate "give up" state: retries continue until either the connection
  recovers or the room's TTL elapses (the Stage 5 TTL-expiry effect already
  navigates away at that point).

## Deploy

- Backend: Docker multi-stage build (`backend/Dockerfile`) → Fly.io
  (`backend/fly.toml`), always-on single machine, Toronto region (ADR-0003).
- Backend live at `api.quickdot.amartian.ca` (DNS-only CNAME →
  `quickdot-api.fly.dev`, Fly-issued cert); one machine, cost-locked per
  ADR-0015.
- Frontend: Cloudflare Workers static assets (not Pages — ADR-0016),
  Git-connected Worker `quickdot` (root `frontend/`, build `npm run build`,
  deploy `npx wrangler deploy`), live at `quickdot.amartian.ca` and
  `quickdot.jeremy25anya.workers.dev`.
- See `docs/DEPLOY.md` for the exact one-time setup steps (account-linked,
  must be run by the account owner).
