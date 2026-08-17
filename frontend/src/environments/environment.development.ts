// Used for `ng serve` / development builds (see fileReplacements in
// angular.json). Points at the backend running locally.
//
// Testing from a second device on the same network? Backend isn't
// reachable at localhost from there — either run the backend's dev server
// through the Cloudflare Tunnel too and swap these for the tunnel hostname,
// or just build/serve with `--configuration=production` so the app uses
// environment.ts's api.quickdot.amartian.ca instead.
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  wsBaseUrl: 'ws://localhost:8080'
};
