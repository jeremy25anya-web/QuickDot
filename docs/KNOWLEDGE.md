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
- [ ] **Confirm Sentry events arrive scrubbed.** DSNs supplied 2026-09-28:
      backend `SENTRY_DSN` staged as a Fly secret (live on next deploy),
      frontend DSN in `environment.ts`. After deploy, trigger one test error
      per side and check in Sentry that no URL room codes, IPs or messages
      came through.
- [ ] **Stage 6 (Hardening) is partially done, not signed off.** Origin lock
      (WS + CORS), security headers, metadata-only audit logging with 30-day
      retention, and the frontend reconnect UI are all implemented and
      tested (see ADR-0012 through ADR-0014) — including an end-to-end
      hostile-input pass (`OriginCheckIntegrationTest` +
      `QuickDotWebSocketHandlerTest`'s existing oversized/blocked-type/flood
      cases). Load test run against prod 2026-09-28 (backend deploy
      `01M3MZR3VVEPECT7DXV3WG7MXZ`): all 10 §6 checks pass, including
      per-IP room limits with a spoofed `Fly-Client-IP` header ignored (so
      Fly overwrites client-supplied values); 3 concurrent 25 MB transfers
      complete at 5.1-5.9 MB/s each, `/healthz` 17/17 OK (median 83 ms, max
      553 ms), no OOM or restart events on the 512 MB machine. What's still
      open: confirming Sentry events arrive scrubbed (above), and checking
      `audit.log` on the machine shows distinct IP hashes and no content
      (`flyctl ssh console -a quickdot-api -C "tail /app/logs/audit.log"`).
      Per `CLAUDE.md`'s stage discipline, don't mark Stage 6 done until
      both land.

## Resolved

- [x] **Per-IP room-creation limits + real client IP** — resolved
      2026-09-28 (ADR-0018). `web.ClientIpResolver` reads `Fly-Client-IP`
      when `APP_TRUST_FLY_CLIENT_IP=true` (fly.toml); `room.RoomCreationLimiter`
      enforces ≤5 open rooms and ≤20 creations/hour per hashed IP, 429
      `ROOM_LIMIT`. Load test passes against prod (2026-09-28), including
      the spoofed-header check. Audit-log IP hashes
      written before this deploy are all the Fly proxy's and can't be used
      to tell clients apart.

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
