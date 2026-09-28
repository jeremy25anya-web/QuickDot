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

> **⚠ Do NOT run `fly launch` / `flyctl launch`.** The committed
> `backend/fly.toml` is the config — `launch` regenerates it, and a
> launch-created app's first deploy provisions **two** machines for high
> availability, doubling the cost. This app is deliberately locked to a
> single always-on `shared-cpu-1x`/512MB machine (ADR-0015).

Log in from a **separate, regular terminal** — `flyctl auth login` needs an
interactive TTY and fails under Claude Code's `!` prefix ("requires an
interactive terminal"). The token it saves is picked up by later `!`
commands; confirm with:
```
flyctl auth login            # in your own PowerShell window, not via `!`
! flyctl auth whoami
```

Then, from `backend/`, create the empty app (no machines, no config
changes) under the name in `fly.toml`:
```
! flyctl apps create quickdot-api
```
If you pick a different name, update `app =` in `backend/fly.toml` to match.

Set the two secrets Stage 6 hardening requires before the first real deploy
(both default to dev-only values locally — see `application.properties` —
and must not ship with those defaults in production):
```
! flyctl secrets set APP_ALLOWED_ORIGIN=https://quickdot.amartian.ca
! flyctl secrets set APP_IP_HASH_SALT=$(openssl rand -hex 32)
```
`APP_ALLOWED_ORIGIN` locks the WebSocket origin check and CORS (spec §6) to
the real frontend origin instead of the local Angular dev server.
`APP_IP_HASH_SALT` salts the hashed IPs in the metadata audit log
(`logs/audit.log`, 30-day retention) — generate it once and keep it stable
across deploys, since changing it breaks correlation of repeat offenders
across the retention window; don't reuse the dev default.

Deploy using the existing committed `fly.toml`:
```
! flyctl deploy --ha=false --remote-only
```
`--ha=false` stops the first deploy from creating a second standby machine
(Fly's default for apps with an HTTP service). `--remote-only` builds the
Docker image on Fly's remote builder, so you don't need Docker installed
locally.

Confirm the cost lock held:
```
! flyctl machines list      # expect exactly ONE machine, region yyz, shared-cpu-1x 512MB
! flyctl ips list           # expect v6 + "shared" v4 only — no dedicated v4 ($2/mo)
! flyctl volumes list       # expect none
```
If the first deploy prints `Failed to provision IP addresses` (seen
2026-09-28 — the machine starts but `*.fly.dev` is unreachable), allocate
the free addresses by hand — never plain `allocate-v4`, which is a paid
dedicated IPv4:
```
! flyctl ips allocate-v6 -a quickdot-api
! flyctl ips allocate-v4 --shared -a quickdot-api
```
If a second machine ever shows up, `flyctl scale count 1` removes it. If a
dedicated IPv4 was allocated, `flyctl ips release <address>`.

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
