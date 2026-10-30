# QuickDot frontend

Angular 22 app (standalone components, signals, zoneless) for
[QuickDot](../README.md). It's served as static assets from Cloudflare and
talks to the Spring Boot backend over a WebSocket.

```bash
npm ci
npm start        # dev server on http://localhost:4200
npm test         # Vitest unit tests
npm run build    # production build into dist/frontend/browser
```

The backend URL comes from `src/environments/`. For the full architecture,
limits and deploy steps, see the [root README](../README.md),
[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) and
[`docs/DEPLOY.md`](../docs/DEPLOY.md).
