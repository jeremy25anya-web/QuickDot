# Knowledge ledger

Running log of things the agent surfaces for Jeremy to triage manually —
open questions, blockers, and items needing a human decision or human hands
(account logins, DNS, legal review). Not an ADR log (see `DECISIONS.md` for
decisions already made); this is for things still **open**.

Mark items `[ ]` open / `[x]` resolved. Don't delete resolved items — they're
a record of what was asked and answered.

---

## Open

- [ ] **Stage 0 cloud deploy = DEFERRED (blocked on Fly.io payment method,
      expected to resolve in ~1-2 weeks as of 2026-08-17).** Scaffolding
      (Dockerfile, fly.toml, Angular shell, CI) is done and builds locally;
      `flyctl launch` failed with "requested machine count exceeds
      organization limit," which traces back to no card on file for the Fly
      org. Fly.io remains the production target (see ADR-0004, SPEC.md
      unchanged) — this is a timing gap, not a target change. In the
      meantime, development is proceeding locally on Stages 1-5, optionally
      exposed via Cloudflare Tunnel for cross-device testing (see
      `docs/DEPLOY.md` §1). Once a payment method is added, resume at
      `docs/DEPLOY.md` §2: `fly launch` + `fly deploy` for the backend,
      connect the repo to Cloudflare Pages for the frontend, add both CNAME
      records. Stage 0 is not "done" per spec until both subdomains serve
      HTTPS and `/healthz` returns 200 in prod.
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
- [ ] **Sentry account/DSN** not yet set up — needed for Stage 6/7 wiring
      (frontend + backend, errors only, no PII).

## Resolved

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
