# Deploy runbook (Stage 0)

Fly.io + Cloudflare Pages remain the production target (see `SPEC.md`).
Cloud deploy is currently **deferred** — blocked on a Fly.io payment method,
see ADR-0004 — so development is happening locally, optionally exposed for
cross-device testing via a Cloudflare Tunnel. Section 1 below is the current
path; sections 2-4 are the production path to pick back up once billing is
sorted.

These steps require your own Fly.io / Cloudflare / GitHub accounts — the
agent cannot complete them (no interactive OAuth). Run them yourself, ideally
via the `!` prefix in the Claude Code session so the output lands in context
and Claude can help debug failures.

## 1. Local dev + Cloudflare Tunnel (current)

Run the backend and frontend dev servers locally:

```
! cd backend && ./mvnw spring-boot:run
! cd frontend && ng serve
```

The frontend's API/WS base URL is an environment config
(`frontend/src/environments/`), not hardcoded, so which backend it talks to
is a build/serve flag, not a code change:

- **Same-machine dev** (default `ng serve`): uses `environment.development.ts`
  → `http://localhost:8080` / `ws://localhost:8080`.
- **Cross-device** (testing from a phone or another machine on the network):
  localhost isn't reachable from there. Run the backend through the tunnel
  too (below) and serve/build the frontend with `--configuration=production`
  so it uses `environment.ts`'s `api.quickdot.amartian.ca` — which the named
  tunnel is routing to your local backend.

**Quick throwaway URL** (no Cloudflare account needed, URL changes every run):
```
! cloudflared tunnel --url http://localhost:8080
```

**Named tunnel on our real hostname** (free, no credit card, WebSockets on by
default):
```
! cloudflared tunnel login
! cloudflared tunnel create quickdot-dev
! cloudflared tunnel route dns quickdot-dev api.quickdot.amartian.ca
```
`config.yml` ingress:
```yaml
ingress:
  - hostname: api.quickdot.amartian.ca
    service: http://localhost:8080
  - service: http_status:404
```
Run it:
```
! cloudflared tunnel run quickdot-dev
```

> **Note:** `tunnel route dns` creates a **proxied** CNAME (orange cloud) —
> that's correct for a Cloudflare Tunnel and not the "DNS only" case from the
> Fly section below. Don't "fix" it to grey cloud; that's a different
> mechanism for a different backend.

> **Note:** the tunnel is only up while your dev machine and `cloudflared`
> are running — fine for development, not a substitute for the always-on
> Fly deploy.

## 2. Backend → Fly.io (DEFERRED — production, do when payment method is available)

Install flyctl if you don't have it:
```
winget install --id Fly-io.flyctl -e
```

Then, from `backend/`:
```
! flyctl auth login
! flyctl launch --copy-config --no-deploy
```
`--copy-config` uses the committed `backend/fly.toml` as-is (don't let
`launch` regenerate it). When prompted for an app name, either accept
`quickdot-api` or pick your own — if you change it, update `app =` in
`backend/fly.toml` to match.

Deploy:
```
! flyctl deploy --remote-only
```
`--remote-only` builds the Docker image on Fly's remote builder, so you don't
need Docker installed locally.

Verify:
```
! curl -i https://quickdot-api.fly.dev/healthz
```
(swap the hostname if you picked a different app name) — expect `HTTP/2 200`
and `{"status":"ok"}`.

## 3. Point `api.quickdot.amartian.ca` at Fly

In the Cloudflare dashboard for the `amartian.ca` zone, add:

| Type  | Name          | Target                     | Proxy status |
|-------|---------------|-----------------------------|--------------|
| CNAME | `api.quickdot`| `quickdot-api.fly.dev`      | **DNS only** (grey cloud) |

Then register the custom hostname with Fly so it issues a cert for it:
```
! flyctl certs add api.quickdot.amartian.ca -a quickdot-api
```
Wait for `flyctl certs show api.quickdot.amartian.ca -a quickdot-api` to
report the cert as issued, then re-verify:
```
! curl -i https://api.quickdot.amartian.ca/healthz
```

> Proxy status must be **DNS only**, not Cloudflare-proxied. This isn't about
> WebSockets — Cloudflare's proxy supports WebSocket upgrades fine. The
> reason is that Fly issues and terminates its own TLS certificate for the
> hostname; DNS-only is Fly's documented simplest path to certificate
> issuance and avoids a double layer of TLS termination.

## 4. Frontend → Cloudflare Pages

**Prerequisite:** Cloudflare Pages' git integration connects to an existing
GitHub remote — the repo must have a remote and `main` pushed before you can
connect it. This repo already has `origin` set to
`https://github.com/jeremy25anya-web/QuickDot.git` with `main` pushed; for a
fresh repo that would be:
```
! git remote add origin <repo-url>
! git push -u origin main
```

In the Cloudflare dashboard: **Workers & Pages → Create → Pages → Connect to
Git**, select this repo, and configure:

| Setting | Value |
|---|---|
| Production branch | `main` |
| Build command | `npm run build` |
| Root directory | `frontend` |
| Build output directory | `dist/frontend/browser` |

Build output directory is relative to Root directory — with Root =
`frontend`, that's `dist/frontend/browser`, not
`frontend/dist/frontend/browser`. (`frontend` is the Angular project name in
`angular.json`; confirm with a local `ng build` if that ever changes.)

Add the custom domain `quickdot.amartian.ca` in the Pages project's **Custom
domains** tab — Cloudflare will add the DNS record for you automatically
since the zone is already on Cloudflare.

Verify:
```
! curl -i https://quickdot.amartian.ca
```
Expect `HTTP/2 200`.

## 5. Stage 0 sign-off

Once both checks above return 200, tell Claude — Stage 0's "done when" is
met and the build can move to Stage 1. Until then, treat Stage 0 as
in-progress (tracked in `docs/KNOWLEDGE.md`).
