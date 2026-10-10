# Knowledge ledger

Running log of things the agent surfaces for Jeremy to triage manually —
open questions, blockers, and items needing a human decision or human hands
(account logins, DNS, legal review). Not an ADR log (see `DECISIONS.md` for
decisions already made); this is for things still **open**.

Mark items `[ ]` open / `[x]` resolved. Don't delete resolved items — they're
a record of what was asked and answered.

---

## Open

- [ ] **Enable Cloudflare Web Analytics** (Stage 7). No code needed: the
      site is served through Cloudflare, so the beacon can be injected
      automatically. Cloudflare dashboard → Analytics & Logs → Web Analytics
      → Add a site → `quickdot.amartian.ca` → automatic setup. It's
      cookieless, which is what the Privacy Policy already promises. Tell
      Claude once it's on so the Stage 7 checklist can be ticked.
- [ ] **Human accessibility checks** (Stage 7, `ACCESSIBILITY.md` → Status):
      keyboard-only walkthrough, a screen-reader pass (NVDA on Windows is
      free), 200% zoom, target spacing on a phone. Since the v2 redesign
      (ADR-0022, 2026-10-09) also cover dark mode, the room ⋯ menu (layout
      and theme switches), two-tap leave, and "End room for everyone".

- [ ] **Legal review outstanding.** Both `legal/PRIVACY-POLICY.md` and
      `legal/TERMS-OF-SERVICE.md` already exist as solid drafts but explicitly
      flag themselves as "not legal advice" — the spec's Definition of Done
      requires both be lawyer-reviewed before launch.

## Resolved

- [x] **Responsive layout bugs** — 2026-10-10 (ADR-0023). The landing
      logo/kicker overlap found during README screenshots, plus six more
      found by a 13-viewport sweep, are fixed and re-verified by the same
      sweep. Still worth a look on a real iPhone: the composer no longer
      zooms on focus.

- [x] **v2 redesign shipped** — 2026-10-09 (ADR-0022). Frontend live via
      Cloudflare; backend deployed by hand with
      `flyctl deploy --ha=false --remote-only` because the GitHub deploy
      workflow is still gated off (`FLY_DEPLOY_ENABLED` unset). Verified in
      prod: host token issued, wrong token rejected (`NOT_HOST`), the right
      token ends the room for everyone and later joins get `ROOM_NOT_FOUND`.

- [x] **Legal placeholders filled** — 2026-09-28. Operator brand
      "Amartian Studios", contact hello@amartian.ca, abuse
      abuse@amartian.ca, effective date September 28, 2026, in both
      `legal/PRIVACY-POLICY.md` and `legal/TERMS-OF-SERVICE.md`. Region
      settled as `yyz` (deployed, ADR-0015). The business-structure
      placeholder was replaced by the brand name rather than "sole
      proprietorship"/"incorporated"; worth confirming in the legal review.

- [x] **Stage 6 (Hardening) signed off 2026-09-28.** Origin lock, CORS,
      security headers, reconnect UI, metadata-only audit log (ADR-0012 to
      0014), Sentry scrubbed on both halves (ADR-0017), per-IP room limits
      and real client IP (ADR-0018). Prod load test passed all 10 §6 checks
      with no OOM. Jeremy confirmed the Sentry test event arrived scrubbed
      and `audit.log` shows hashed IPs with no content.

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
