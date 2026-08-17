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
