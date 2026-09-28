# Knowledge ledger

Running log of things the agent surfaces for Jeremy to triage manually —
open questions, blockers, and items needing a human decision or human hands
(account logins, DNS, legal review). Not an ADR log (see `DECISIONS.md` for
decisions already made); this is for things still **open**.

Mark items `[ ]` open / `[x]` resolved. Don't delete resolved items — they're
a record of what was asked and answered.

---

## Open

- [ ] **Placeholders to fill before launch** (spec, final section):
      `{{CONTACT_EMAIL}}`, `{{ABUSE_EMAIL}}`, `{{EFFECTIVE_DATE}}`,
      `{{BUSINESS_STRUCTURE}}` (sole proprietorship vs incorporated), and the
      final Fly.io region (currently defaulted to `yyz`/Toronto — see
      ADR-0003). These appear in `legal/PRIVACY-POLICY.md` and
      `legal/TERMS-OF-SERVICE.md`.
- [ ] **Legal review outstanding.** Both `legal/PRIVACY-POLICY.md` and
      `legal/TERMS-OF-SERVICE.md` already exist as solid drafts but explicitly
      flag themselves as "not legal advice" — the spec's Definition of Done
      requires both be lawyer-reviewed before launch.
- [ ] **Sentry DSNs needed.** Code is wired and scrubbed (ADR-0017) but
      inert until Jeremy creates the account + two projects (`quickdot-api`,
      `quickdot-web`; guide in `docs/DEPLOY.md` §6) and supplies the DSNs:
      backend → `flyctl secrets set SENTRY_DSN=...`, frontend →
      `environment.ts` `sentryDsn`. Then trigger one test error per side to
      confirm events arrive scrubbed.
- [ ] **§6 gap: per-IP room-creation limits not implemented** (found
      2026-09-28 by `tools/loadtest/loadtest.py --creation-limits`: 21/21
      creations from one IP succeeded). Spec requires ≤20 rooms per IP per
      hour and ≤5 concurrent rooms per IP, enforced server-side
      (`RoomController` currently has no check). Blocks Stage 6 sign-off.
- [ ] **Client IP is probably the Fly proxy, not the user.**
      `RoomController` uses `request.getRemoteAddr()` and the WS handler uses
      `session.getRemoteAddress()`, with no forwarded-header handling
      (`server.forward-headers-strategy` unset). Behind Fly's proxy these
      most likely return the proxy's address, so audit-log IP hashes are the
      same for everyone and any per-IP limit would throttle all users as
      one. Needs fixing before the per-IP limits above; fix options are
      Fly's `Fly-Client-IP` header or `server.forward-headers-strategy`.
      Unverified in prod (needs a look at `audit.log` via `flyctl ssh`).
- [ ] **Stage 6 (Hardening) is partially done, not signed off.** Origin lock
      (WS + CORS), security headers, metadata-only audit logging with 30-day
      retention, and the frontend reconnect UI are all implemented and
      tested (see ADR-0012 through ADR-0014) — including an end-to-end
      hostile-input pass (`OriginCheckIntegrationTest` +
      `QuickDotWebSocketHandlerTest`'s existing oversized/blocked-type/flood
      cases). What's still open: Sentry DSNs (above), the per-IP creation
      limits and client-IP fix (above), and load-testing the §6 limits against a real deployed
      instance under concurrent traffic (now unblocked — Stage 0 is live as
      of 2026-09-28). Per `CLAUDE.md`'s stage
      discipline, don't mark Stage 6 done until both land.

## Resolved

- [x] Stage 0 cloud deploy — resolved 2026-09-28 (was deferred on a Fly.io
      payment method, ADR-0004). Backend on Fly (`quickdot-api`, one
      machine, ADR-0015), frontend on Cloudflare Workers static assets
      (ADR-0016). Verified: `https://api.quickdot.amartian.ca/healthz` →
      200 `{"status":"ok"}`; `https://quickdot.amartian.ca/` and
      `/r/ABCD` → 200 serving the app. `api.quickdot` is now a DNS-only
      CNAME to Fly, so the local-dev Cloudflare Tunnel route no longer
      applies to that hostname.
- [x] Spring Boot 3.x vs 4.x — resolved 2026-08-15, see ADR-0001.
- [x] Local toolchain (Java 21) — resolved 2026-08-15, Temurin 21 installed via
      winget; Maven not installed globally, using the committed Maven wrapper
      (`backend/mvnw`) instead.
- [x] Reference prototype `quickdot.html` — added to the repo root
      2026-09-04 and ported to the Angular frontend in Stage 5 (see ADR-0010,
      ADR-0011).
- [x] Stage 5 (Angular UI) — completed 2026-09-04, manually verified
      end-to-end against a 30-item QA checklist (landing, create/join,
      presence, messaging, files, per-message menu, lifecycle, safety
      limits, edge cases) — all passed.
