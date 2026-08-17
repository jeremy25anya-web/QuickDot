// Production build config (default). Swapped for environment.development.ts
// under the `development` configuration via angular.json fileReplacements.
//
// This same hostname is used whether it's currently backed by the
// Cloudflare Tunnel (temporary, see docs/DEPLOY.md) or by Fly.io
// (production target, once deployed) — no config change needed to move
// between them.
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.quickdot.amartian.ca',
  wsBaseUrl: 'wss://api.quickdot.amartian.ca'
};
