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
 │  Cloudflare Pages         │        │  Fly.io (always-on machine)   │
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
    ws/           websocket handler, session tracking, protocol [Stage 2]
    relay/        text + file relay, chunking                [Stage 3-4]
    safety/       rate limiting, size/type/MIME enforcement, origin/CORS [Stage 4/6]
    web/          health check, config, security headers     [Stage 0]
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

## Frontend

- Angular (standalone components, no NgModules), SCSS, `ssr=false` (static
  build only — this is a Cloudflare Pages static deploy, no Angular
  Universal/SSR server).
- Build output: `frontend/dist/frontend/browser` — this is the directory
  Cloudflare Pages should serve.

## Deploy

- Backend: Docker multi-stage build (`backend/Dockerfile`) → Fly.io
  (`backend/fly.toml`), always-on single machine, Toronto region (ADR-0003).
- Frontend: Cloudflare Pages, connected directly to the git repo (build
  command `npm run build` in `frontend/`, output directory
  `dist/frontend/browser`).
- See `docs/DEPLOY.md` for the exact one-time setup steps (account-linked,
  must be run by the account owner).
