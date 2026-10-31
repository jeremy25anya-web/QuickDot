# Security policy

QuickDot relays content between devices and stores none of it, so the most
valuable reports are ones that show it leaking, persisting or bypassing a
limit.

## Reporting a vulnerability

Email **hello@amartian.ca** with "QuickDot security" in the subject, or use
GitHub's private vulnerability reporting on this repo. Please don't open a
public issue for something exploitable.

Include what you found, how to reproduce it, and the impact you expect. I'll
acknowledge it within a few days and tell you when it's fixed.

## In scope

- The live app at `quickdot.amartian.ca` and its API at
  `api.quickdot.amartian.ca`
- Anything in this repository

Examples worth reporting: content surviving after a room ends, bypassing the
server-side limits (file size, blocked types, rate limits, room caps),
cross-room access, and WebSocket origin or header issues.

## Out of scope

- Denial-of-service or load testing against production (the harness in
  `tools/loadtest` is for the project's own checks)
- Social engineering, physical attacks, or third-party services (Fly.io,
  Cloudflare, Sentry)
- Missing best-practice headers with no demonstrated impact

Please don't access other people's rooms or content while testing.
