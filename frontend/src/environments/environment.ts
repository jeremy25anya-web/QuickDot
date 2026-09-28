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
  wsBaseUrl: 'wss://api.quickdot.amartian.ca',
  // Public by design (it ships in the bundle). Empty = Sentry disabled.
  sentryDsn: 'https://3ba3a21669a199c9c9a2001a85f75598@o4512166040502272.ingest.us.sentry.io/4512166061932544'
};
