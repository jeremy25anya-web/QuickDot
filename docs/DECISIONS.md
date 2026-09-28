# Decisions (ADR log)

Append an entry per non-trivial decision. Newest at the bottom.

---

## ADR-0001: Spring Boot 4.x instead of 3.x

**Date:** 2026-08-15
**Stage:** 0

**Context:** SPEC.md pins Spring Boot 3.x, reasoning that it "matches
job-search target." As of this build, Spring Boot 3.x is no longer offered by
start.spring.io (metadata reports a minimum compatible version of 4.0.0) and
is EOL upstream.

**Decision:** Build on Spring Boot 4.1.0 (current stable) instead. The spec's
own stated reason for pinning a version — matching what's current/marketable
— argues *for* moving off an EOL line, not for staying on it.

**Consequences:** Dependency artifact IDs differ slightly from 3.x (e.g. the
web starter is `spring-boot-starter-webmvc`, not `spring-boot-starter-web`).
Java 21 baseline is unaffected. No other spec requirement depends on the 3.x
line specifically.

---

## ADR-0002: Backend deploy target scaffolded, not executed

**Date:** 2026-08-15
**Stage:** 0

**Context:** Stage 0's acceptance criteria requires both subdomains to serve
live HTTPS traffic in prod. Doing that requires Fly.io and Cloudflare account
logins (interactive OAuth), which the agent cannot perform. The user chose to
handle the actual authenticated deploy steps themselves rather than share
credentials.

**Decision:** Ship a complete, ready-to-run deploy scaffold (`backend/Dockerfile`,
`backend/fly.toml`, CI workflows) and a step-by-step runbook
(`docs/DEPLOY.md`) instead of attempting the deploy directly. Stage 0 is not
marked done until the user confirms both subdomains are live and `/healthz`
returns 200 in prod, per the spec's own "done when" criteria.

**Consequences:** Stage 0 has two parts: agent-completed scaffolding (done)
and user-executed deploy + DNS (pending, tracked in KNOWLEDGE.md). The
`deploy-backend.yml` CI workflow is wired but gated behind a `FLY_DEPLOY_ENABLED`
repo variable so it no-ops until the user opts in, rather than failing on every
push to main.

---

## ADR-0003: Fly.io region — Toronto (yyz), always-on single machine

**Date:** 2026-08-15
**Stage:** 0

**Context:** Spec requires an always-on machine (no scale-to-zero cold start)
and calls out Fly.io as the primary target, Ontario as the operator's
jurisdiction.

**Decision:** `primary_region = "yyz"` (Toronto), `min_machines_running = 1`,
`auto_stop_machines = false`, `shared-cpu-1x` / 512MB. This is the smallest
config that satisfies "always-on" and keeps data closest to the operator's
jurisdiction (relevant given the room state and IP-based rate limiting all
live in that single instance's memory).

**Consequences:** Single point of failure by design (v1 explicitly forgoes
horizontal scaling / Redis — see spec §10). Region and size are easy to change
later in `fly.toml` if load-testing (Stage 6) says otherwise.

---

## ADR-0004: Temporary local-dev + Cloudflare Tunnel hosting during funding gap

**Date:** 2026-08-17
**Stage:** 0

**Context:** `flyctl launch` failed with "requested machine count exceeds
organization limit" — the Fly org has no payment method on file, and Fly
requires one before it will place machines even within free-tier allowances.
Resolving this is expected to take ~1-2 weeks. Blocking all of Stages 1-5 on
that timeline isn't worth it when local development can proceed in the
meantime, and cross-device testing (e.g. checking the UI on a phone, sharing
a room with someone else for a real WebSocket test) still needs *some*
publicly reachable hostname in the interim.

**Decision:** Continue backend + frontend development locally
(`mvn spring-boot:run` / `ng serve`), and use a Cloudflare Tunnel — a named
tunnel routed to `api.quickdot.amartian.ca`, the same hostname planned for
Fly production — for any cross-device or shared testing needed before the
Fly deploy is unblocked. Fly.io remains the production target; this is a
timing workaround, not a target change, and `SPEC.md` is unchanged.

**Consequences:** No code change needed to move between localhost, the
tunnel, and eventual Fly production — the frontend's API/WS base URL is an
environment config (`frontend/src/environments/`), and the tunnel targets
the exact hostname Fly will eventually serve. Cloudflare Tunnel requires no
credit card, supports WebSockets by default, and reuses the `amartian.ca`
zone already on Cloudflare — no new accounts needed. Tradeoff: the tunnel is
only live while the dev machine and `cloudflared` are running, so it's a dev
convenience, not a substitute for the always-on Fly deploy Stage 0 actually
requires to be marked done. See `docs/DEPLOY.md` §1 for setup and
`docs/KNOWLEDGE.md` for current status.

---

## ADR-0005: IP-based room rate limits deferred to Stage 6, not built in Stage 1

**Date:** 2026-09-04
**Stage:** 1

**Context:** Spec §6 lists per-IP room creation limits (≤20/hour) and
concurrent-room limits (≤5/IP) as server-enforced. Stage 1's own "done when"
criteria (spec §8) only asks for create/join/expire correctness under unit
tests, and the spec's staged plan puts general abuse hardening in Stage 6.
Building IP-based rate limiting now would mean building a rate-limiter
component with no consumer yet exercising it under real traffic patterns.

**Decision:** Implement the room domain (`RoomRegistry`, `Room`,
`Participant`) in Stage 1 without IP-based creation/concurrency limits.
`Participant.ip` is still captured at join time so Stage 6 has the field to
key a limiter off; the limiter itself is Stage 6 scope.

**Consequences:** Until Stage 6, `RoomRegistry.createRoom()` has no per-IP
cap — acceptable for local development but not for anything internet-facing.
This must land before Stage 7 sign-off (spec §9 requires all §6 limits
enforced server-side). Tracked as a Stage 6 task, not re-added to
`KNOWLEDGE.md` since it's already accounted for in the spec's own stage plan.

---

## ADR-0006: Spring Boot 4.1 ships Jackson 3.x — `tools.jackson.*`, not `com.fasterxml.jackson.*`

**Date:** 2026-09-04
**Stage:** 2

**Context:** Writing the WebSocket protocol handling (Stage 2) needed
`ObjectMapper`/`JsonNode` for the JSON control messages in spec §5. The
`dependency:tree` output shows `spring-boot-starter-webmvc` pulls in
`tools.jackson.core:jackson-databind:3.1.4`, not the `com.fasterxml.jackson.core`
groupId used by Jackson 2.x — Spring Boot 4.1 has moved to Jackson 3, which
renamed its Java package root from `com.fasterxml.jackson.*` to
`tools.jackson.*` (only `jackson-annotations` stays under `com.fasterxml`).
This wasn't previously called out — ADR-0001 flagged Spring Boot 4.x's Maven
artifact-id changes but not this package rename, since it hadn't been hit yet.

**Decision:** Import `tools.jackson.databind.ObjectMapper` /
`tools.jackson.databind.JsonNode` throughout the backend, not the `com.fasterxml`
equivalents. Also use Jackson 3's renamed `JsonNode.asString(default)` instead
of the now-deprecated `asText(default)`.

**Consequences:** Any future code, snippet, or dependency added by pattern-matching
against Jackson 2.x tutorials/docs (the overwhelming majority of what's publicly
documented) will need this substitution. Records serialize out of the box with
Jackson 3's default `ObjectMapper` — no extra module needed, verified via the
`ws` package's unit tests.

---

## ADR-0007: Added `POST /api/rooms` — not in spec §5, but required for the app to function

**Date:** 2026-09-04
**Stage:** 1 (retrofit) / 3

**Context:** Spec §5 defines the WebSocket protocol assuming a room already
exists, and spec §4's domain model covers room creation at the registry
level, but no part of the spec defines how a room actually gets minted before
a creator has a code to share. The prototype (`quickdot.html`) generates codes
entirely client-side with no backend, which doesn't apply once creation must
be server-authoritative (the client is never trusted — `CLAUDE.md`). Without
some HTTP entry point, `RoomRegistry.createRoom()` is unreachable from outside
the process.

**Decision:** Add `RoomController` (`room` package) exposing
`POST /api/rooms` → `{code, expiresAt}`, calling `RoomRegistry.createRoom()`
directly. This is also the natural home for the per-IP room-creation rate
limit deferred to Stage 6 (ADR-0005) — that limit attaches to this endpoint.

**Consequences:** The Angular "Create a room" flow (Stage 5) calls this
endpoint, then opens the WebSocket connection with the returned code. No spec
change needed — this fills an implementation gap the spec's staged
description didn't spell out, rather than contradicting it.

---

## ADR-0008: MIME sniffing via a curated dangerous-type set, not an extension↔MIME cross-reference

**Date:** 2026-09-04
**Stage:** 4

**Context:** Spec §6 requires rejecting a file "if the sniffed type is
executable or contradicts a claimed-safe type" — e.g. an `.exe` renamed to
`.png` to dodge the extension blocklist. `org.apache.tika.Tika.detect(byte[])`
(tika-core 4.0.0, magic-byte detection only — no `tika-parsers` needed)
reliably identifies these from a small header sample: confirmed empirically
that an MZ header detects as `application/x-msdownload`, an ELF header as
`application/x-executable`, and a `#!/bin/bash` shebang as `application/x-sh`.
Building a full extension↔MIME cross-reference matrix (to catch every
possible "declared extension doesn't match sniffed type" mismatch) would be
substantially more code for marginal additional coverage beyond "is this
thing actually an executable/script."

**Decision:** `relay.MimeSniffer` detects the MIME type from magic bytes only
(never the filename or declared type) and rejects it against a curated
`DANGEROUS_MIME_TYPES` set (PE/ELF/Mach-O executables, shell scripts, JARs).
The sniff runs once, on the first chunk of a transfer (spec: `file-start`
carries no bytes, so that's the first point any exist to sniff), and that one
chunk is the only bytes ever held in memory — full-file buffering is never
needed since the check only needs a small header sample.

**Consequences:** This catches the classic disguise attack (executable
renamed to an allowed extension) but doesn't catch every declared-vs-sniffed
MIME *mismatch* that isn't inherently dangerous (e.g. a `.txt` that's
actually a `.png` — mislabeled but harmless). Acceptable for v1: the safety
goal is blocking executables/scripts, not perfect type-declaration honesty.

---

## ADR-0009: `@SpringBootTest` needs `RANDOM_PORT`, not the default MOCK environment

**Date:** 2026-09-04
**Stage:** 4

**Context:** Adding `ServletServerContainerFactoryBean` (`ws.WebSocketConfig`,
to size the WebSocket frame buffers for one 256 KB chunk) broke
`QuickdotApplicationTests.contextLoads` with `IllegalStateException:
Attribute 'jakarta.websocket.server.ServerContainer' not found in
ServletContext`. That bean only resolves against a real JSR-356 container —
the default `@SpringBootTest` web environment (`MOCK`) never starts one.

**Decision:** Changed `QuickdotApplicationTests` to
`@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`, which boots
an actual embedded Tomcat and satisfies the attribute lookup.

**Consequences:** The context-load smoke test is slightly slower (a real
server starts) but now actually proves the app boots the way it will in
production, which `MOCK` never fully did anyway.

---

## ADR-0010: Permissive CORS on `/api/**`, same rationale as the WebSocket origin policy

**Date:** 2026-09-04
**Stage:** 5

**Context:** Stage 5 wired the Angular `RoomApiService` to `POST /api/rooms`.
In local dev the Angular dev server (`localhost:4200`) and the backend
(`localhost:8080`) are different origins, so the browser blocked the request
until CORS was configured — the same cross-origin situation `ws.WebSocketConfig`
already documented for the WebSocket handshake (`setAllowedOriginPatterns("*")`,
Stage 2), just hitting a plain REST endpoint instead of a socket upgrade.

**Decision:** Added `AppConfig.addCorsMappings` (`web` package),
allowing all origins on `/api/**` for `GET`/`POST`. Marked with the same
`TODO(Stage 6)` convention as the WebSocket config.

**Consequences:** `/api/rooms` is callable cross-origin from anywhere until
Stage 6 locks both this and the WebSocket origin check to the exact
production frontend origin (spec §6) — tracked there, not re-added to
`KNOWLEDGE.md` since it's already spec'd as Stage 6 scope.

---

## ADR-0011: Stage 5 UI ships zoneless, room code in the URL path instead of a hash

**Date:** 2026-09-04
**Stage:** 5

**Context:** The Angular 22 scaffold from Stage 0 has no `zone.js` dependency
(confirmed: absent from `package.json` and `angular.json`'s build options),
meaning change detection runs on signals only, with no zone patching
`setTimeout`/`WebSocket` callbacks. Separately, the prototype (`quickdot.html`)
identifies a room via a URL *hash* (`#CODE`) purely because it needed
something to survive a refresh with zero backend involvement (it also used
`BroadcastChannel` to simulate a server, which doesn't apply once there's a
real one).

**Decision:** (1) All room state in `core/room.service.ts` is a signal, and
every async entry point (WebSocket message/close handlers, the TTL
`setInterval`) writes to signals directly rather than relying on zone-triggered
change detection. (2) The room code lives in the Angular route path
(`/r/:code`), not a hash — this makes "auto-rejoin on refresh" (spec Stage 5)
fall out of normal routing for free, and gives the code a real, linkable URL
segment.

**Consequences:** Any future component touching room state must use signals
(a plain mutated class field won't trigger a re-render). Refreshing `/r/:code`
rejoins as a brand-new participant (new id, new fun name) rather than
resuming a prior session — consistent with spec §1's "no accounts, no
identity" and with the protocol (spec §5) having no session-resume concept.

---

## ADR-0012: One `app.allowed-origin` property drives both the WebSocket origin check and CORS

**Date:** 2026-09-04
**Stage:** 6

**Context:** Stage 6 requires locking the WebSocket handshake's `Origin`
check and the `/api/**` CORS policy to the exact production frontend origin
(spec §6), replacing the `*`/permissive settings ADR-0010 and Stage 2's
`WebSocketConfig` used as a stopgap. Both checks need to agree on the same
origin, and that origin differs by environment (Angular dev server locally,
the real Cloudflare Pages domain in production).

**Decision:** A single `app.allowed-origin` property (`WebSocketConfig` and
`AppConfig` both read it) defaults to `http://localhost:4200` in
`application.properties` and is overridden via the `APP_ALLOWED_ORIGIN` env
var everywhere else — Fly.io sets it via `flyctl secrets set` (see
`docs/DEPLOY.md`). `setAllowedOrigins(...)` (exact match) replaces
`setAllowedOriginPatterns("*")`.

**Consequences:** Local `mvnw spring-boot:run` + `ng serve` keeps working
with zero setup, matching `CLAUDE.md`'s emphasis on testing the golden path
locally. Forgetting to set `APP_ALLOWED_ORIGIN` in a real deployment means
the deployed frontend can't connect at all (fails loud, not open) — verified
end-to-end in `OriginCheckIntegrationTest` (a real embedded server + a real
WebSocket client, since the origin check runs in Spring's handshake
interceptor before the handler ever sees the connection — this couldn't be
covered by the existing mocked-session unit tests in
`QuickDotWebSocketHandlerTest`).

---

## ADR-0013: Metadata audit log — hashed IP, running file digest, own rolling appender

**Date:** 2026-09-04
**Stage:** 6

**Context:** Spec §6 requires metadata-only logging for abuse response:
"timestamp, room code, a hashed/truncated IP, file hash + size + type,"
never message/file content, with 30-day retention then purge. Nothing about
this can reuse Spring's default request logging (it would either log too
little to be useful for abuse response, or risk logging content/full IPs).

**Decision:** A dedicated `audit.AuditLog` component logs three metadata
events (`room_created`, `participant_joined`, `file_relayed`) through an
SLF4J logger named `ca.amartian.quickdot.audit`, routed by
`logback-spring.xml` to its own `RollingFileAppender`
(`logs/audit.log`, daily rotation, `maxHistory=30`, `additivity="false"` so
it never also lands in the general application log). IPs are hashed via
`audit.IpHasher` — SHA-256(salt + ip), truncated to 12 hex chars — using a
salt from `app.ip-hash-salt` (env `APP_IP_HASH_SALT` in every non-dev
environment; must stay stable across deploys so repeat-offender correlation
within the retention window keeps working). File hashes come from a running
`MessageDigest` now carried on `relay.FileTransfer`, updated per chunk
alongside the existing byte counter (`FileTransfer.addBytes` takes the chunk
bytes, not just a length, precisely so it can feed the digest) and finalized
once at `file-end` — no additional buffering, since a digest is O(1) memory
regardless of file size.

**Consequences:** `FileTransfer`'s constructor and `addBytes` signature
changed (now takes the declared MIME type and the chunk bytes respectively)
— both call sites were internal to Stage 4's code plus its tests, updated
alongside this. The raw (non-hashed) IP still lives in `room.Participant` in
memory for the life of a connection (Stage 1 design, unchanged) — only the
audit *log* ever sees the hashed form; the raw address is never written to
disk anywhere.

---

## ADR-0014: Reconnect UI retries with capped backoff, keeps the feed, no give-up state

**Date:** 2026-09-04
**Stage:** 6

**Context:** Spec §7 asks for "WebSocket disconnect/reconnect handling with
a visible 'reconnecting…' state." An unexpected socket drop (backend
restart, network blip) previously left `RoomService.connectionState` stuck
at `'closed'` with no recovery — the Stage 5 UI had no way to distinguish
"the room is gone" (server rejected the connection: `ROOM_NOT_FOUND`/
`ROOM_FULL`) from "the connection dropped but the room might still be
there."

**Decision:** `RoomService` now tracks `intentionalClose` and a reconnect
attempt counter. An unrequested socket close/error (i.e., not from `leave()`
and not following a fatal server error) sets `connectionState` to
`'reconnecting'` and retries the same room code after a capped
exponential backoff (1s/2s/4s/8s, then holding at 8s). A successful
reconnect does **not** clear `items`/`participants` the way a fresh
`connect()` does — only the socket is replaced, so the visible chat history
survives a blip. There's no separate "give up" state or retry limit;
retries continue until either the connection recovers or the room's TTL
elapses (the existing TTL-expiry effect already navigates away at that
point), which avoids inventing a second termination condition.

**Consequences:** A reconnect always gets a new participant id/name from the
server — consistent with ADR-0011 (no session-resume concept in spec §5).
The composer's Send/Attach buttons are disabled whenever
`connectionState() !== 'open'` so a message typed during a reconnect gap
isn't silently dropped by `sendText`/`sendFile`'s existing `isOpen()` guard.

## ADR-0015: Fly.io backend locked to one always-on shared-cpu-1x/512MB machine

**Date:** 2026-09-27
**Stage:** 0 (deploy)

**Context:** QuickDot is a portfolio tool with low, bursty traffic. Fly.io
bills per machine-second, per GB of egress (by region tier), and for extras
like dedicated IPv4 and volumes. A default `fly launch` provisions two
machines for HA, and autostart/autoscaling settings can spin up more — both
make the monthly bill unpredictable for no real benefit at this scale.
Spec §2 also already assumes a single instance (all room state is in memory,
so a second machine would split rooms anyway).

**Decision:** `backend/fly.toml` pins the backend to a single always-on
machine: `[[vm]] size = "shared-cpu-1x", memory = "512mb"`,
`auto_stop_machines = false`, `auto_start_machines = false`,
`min_machines_running = 1`, region `yyz` (North America egress tier). No
`[mounts]` (no volumes — nothing is persisted) and no dedicated IPv4 (the
included shared IPv4 + IPv6 suffice). The heap is capped with
`JAVA_TOOL_OPTIONS = "-XX:MaxRAMPercentage=70"` so the JVM sizes itself to
the 512MB container instead of OOMing. fly.toml has no max-machine-count
key, so the one-machine cap is enforced procedurally: the app is created
with `flyctl apps create` (never `fly launch`), deployed with
`flyctl deploy --ha=false`, and verified with `flyctl machines list`
(docs/DEPLOY.md §2).

**Consequences:** Target cost ~$4-7 USD/month, predictable. No redundancy:
a host failure or deploy means brief downtime and drops all live rooms —
acceptable given rooms are ephemeral by design. With `auto_start_machines =
false`, if Fly stops the machine for host maintenance the proxy won't wake
it; the machine's restart policy still covers process crashes. Scaling up
later is a deliberate config change plus a new ADR, not something that can
happen by accident.

## ADR-0016: Frontend hosted on Cloudflare Workers static assets, not Pages

**Date:** 2026-09-28
**Stage:** 0

**Context:** Spec §2 names Cloudflare Pages for the frontend. Connecting
the repo through Cloudflare's current dashboard Git flow created a Worker
(`quickdot.jeremy25anya.workers.dev`) rather than a Pages project, which
served an empty 404 on every path because it had no assets configuration.
Cloudflare now directs new static sites to Workers static assets and ships
new features there; Pages remains supported.

**Decision:** Keep the Worker. `frontend/wrangler.jsonc` points it at
`./dist/frontend/browser` with `not_found_handling:
"single-page-application"` so `/r/:code` deep links resolve to
`index.html`. Build settings: root `frontend`, `npm run build`,
`npx wrangler deploy`, `NODE_VERSION=24`. Custom domain
`quickdot.amartian.ca` is attached to the Worker.

**Consequences:** Functionally equivalent to Pages for this app — same
edge network, $0 (static asset requests are free and unmetered), same
custom-domain/TLS handling. SPA fallback is explicit config instead of
Pages' implicit behavior. Stage 7's custom 404 page will need to account
for this: with SPA fallback on, unknown paths render the Angular app, so the
404 should be an Angular route (the existing `**` wildcard) rather than a
static `404.html`. Spec §2's intent (static frontend on Cloudflare's edge)
is met; only the product name differs.

## ADR-0017: Sentry scrubs in-process; exception messages never leave

**Date:** 2026-09-28
**Stage:** 6

**Context:** Spec §3/§7 want Sentry on both halves, "errors only, no PII",
and the non-negotiables forbid ever logging content. Default Sentry SDKs
attach request URLs, headers, user IPs, console output and DOM-click
selectors, and exception messages can quote their input (a browser's
`JSON.parse` error echoes the text; parser libraries do the same). Room codes
appear in URLs (`/r/CODE`, `/ws/CODE`) and in Spring's WebSocket error log
line, and a code is a live credential for its room for up to 30 minutes.

**Decision:** Scrub in our own code before anything is sent, rather than
relying on Sentry's server-side scrubbing (which is on too, as a backstop).
Backend `monitoring.SentryScrubber` and frontend `core/sentry.ts` both:
drop request and user data; replace every exception message with
`[redacted]` (type + stack trace are kept, which is usually enough to find
the bug); rewrite room-code path segments to `:code` in messages and
breadcrumbs. No tracing, replay or session pings. Frontend console and
DOM-click breadcrumbs are removed (click breadcrumbs capture attributes like
`title`, which can be a file name). Backend log events come only from ERROR
logs; the audit logger is excluded by `additivity="false"`. An empty DSN
disables the SDK, so dev and tests send nothing.

**Consequences:** Some errors will be harder to diagnose without their
messages; if that bites, add targeted, content-free context (error codes,
sizes) at the throw site rather than loosening the scrubber. Our own log
messages still reach Sentry verbatim (after code redaction), so the existing
"never log content" rule is what keeps them clean.

## ADR-0018: Client IP from Fly-Client-IP (opt-in); per-IP room limits in the controller path

**Date:** 2026-09-28
**Stage:** 6

**Context:** The load test showed spec §6's per-IP room-creation limits
(≤20/hour, ≤5 concurrent) were never implemented. Separately, client IPs
came from the socket peer with no forwarded-header handling. Behind Fly's
proxy that's most likely the proxy's own address, so every audit-log IP hash
would be the same and any per-IP limit would throttle all users as one.

**Decision:** `web.ClientIpResolver` uses Fly's `Fly-Client-IP` header,
which Fly's proxy sets to the address it accepted the connection from. We
chose it over `server.forward-headers-strategy` + `X-Forwarded-For` because
it's a single value with no proxy-chain parsing. The header is trusted only
when `APP_TRUST_FLY_CLIENT_IP=true`, set in `fly.toml`. Everywhere else it's
ignored, so a client can't spoof it to dodge limits. The load test's
`--creation-limits` check sends a random spoofed header on every request, so
running it against production also shows whether Fly overwrites
client-supplied values.

`room.RoomCreationLimiter` holds the create path under one lock: reject if
the hashed IP has ≥5 unexpired rooms (`Room.creatorKey`), or ≥20 creations
in a sliding hour. Otherwise it records the creation and creates the room.
Rejections are HTTP 429 `{code: "ROOM_LIMIT", message}`. Keys are hashed
IPs, never raw addresses.

**Consequences:** "Concurrent" counts rooms until they expire or are swept.
A room that's never joined isn't reclaimed by the empty-room grace (that
starts when the last participant *leaves*), so it counts for its full 30-min
TTL. Normal use (create, then join immediately) isn't affected. Users behind
one NAT (an office, campus or carrier NAT) share the limits. Audit-log hashes
written before this deploy are all the proxy's and can't be used to tell
clients apart.

## ADR-0019: Close moves to the room header; file names truncate on their own line

**Date:** 2026-09-28
**Stage:** 5 (UI follow-up)

**Context:** On mobile, a long unbroken file name ran past the file chip and
was clipped by the screen edge instead of truncating. The chip's flex chain
had no `min-width: 0`, so it was held at the name's full width. The name and
size were also inline spans, and `text-overflow: ellipsis` has no effect on
an inline element. Separately, the prototype's bare "Close" button shared the
composer row, where it looked stranded and didn't say what it closed.

**Decision:** Add `min-width: 0` at each level (item, chip, text column) and
make the name and size block-level lines: the name truncates on line one,
"size · tap to save" sits on line two. Move Close out of the composer into
the header next to the countdown, labelled "Close room" (tooltip: "Close room
and wipe everything"). This is a deliberate departure from `quickdot.html`'s
layout: the header holds room-level actions (code, clock, close), and the
bottom bar is only for sending (attach, input, send).

**Consequences:** The composer is a single group. Close is further from the
thumb on mobile, which is acceptable for a destructive, infrequent action.
The pill's tap target is extended to ~44px with a pseudo-element, ahead of
the Stage 7 accessibility pass.
