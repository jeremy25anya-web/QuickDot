# Agent working rules — QuickDot

`SPEC.md` is the authoritative build spec. Read it first. This file is the
process contract for working in this repo.

## Stage discipline

The build is staged (spec §8). Do not start a stage until the previous one's
"Done when" criteria are met. When a stage's acceptance criteria require
something only the human can do (account-linked deploys, DNS, legal review),
scaffold everything possible, document the remaining manual steps in
`docs/KNOWLEDGE.md`, and treat the stage as incomplete until confirmed —
don't mark it done on the strength of local scaffolding alone.

## Non-negotiables from the spec

- **The client is never trusted.** Every limit in spec §6 must be enforced
  server-side, even if the client also checks it.
- **Nothing is persisted.** No database, no Redis, no object storage, no
  writing message/file content to disk, ever. In-memory only.
- **Never log content.** Metadata only (timestamp, room code, hashed/truncated
  IP, file hash+size+type). 30-day retention, then purge.
- **No accounts, no identity.** Server-assigned fun names only.

## Process

- Log every non-trivial decision as an ADR in `docs/DECISIONS.md` (append,
  don't rewrite history).
- Keep `docs/ARCHITECTURE.md` current as the *as-built* system — spec is the
  intent, this is the reality.
- Surface open questions, blockers, and human-only action items in
  `docs/KNOWLEDGE.md` rather than guessing silently on things that matter.
- Deferred/v2 ideas go in `docs/IDEAS.md`, not into v1 scope.

## Practical notes

- Backend: Java 21 / Spring Boot 4.1 (see ADR-0001 for why not 3.x). Use the
  committed Maven wrapper (`backend/mvnw` / `mvnw.cmd`) — no global Maven
  install assumed.
- Frontend: Angular standalone components, no NgModules.
- Local dev note: port 8080 may already be bound by other software on this
  machine — check before assuming a bind failure means the app is broken.
