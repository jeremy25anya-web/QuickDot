import * as Sentry from '@sentry/angular';
import type { Breadcrumb, ErrorEvent } from '@sentry/angular';

import { environment } from '../../environments/environment';

// Error monitoring (spec §3, §7): errors only, no PII. Mirrors the backend's
// monitoring.SentryScrubber — request/user data dropped, exception messages
// dropped (a browser's JSON.parse error quotes the text it failed on, which
// can be room content), and room codes redacted from URLs, since a code is a
// live credential for its room.

const ROOM_PATH = /(\/(?:ws|r|api\/rooms)\/)[A-Za-z0-9]+/g;
export const REDACTED_MESSAGE = '[redacted]';

export function redact(text: string): string;
export function redact(text: string | undefined): string | undefined;
export function redact(text: string | undefined): string | undefined {
  return text?.replace(ROOM_PATH, '$1:code');
}

export function scrubEvent(event: ErrorEvent): ErrorEvent {
  delete event.request;
  delete event.user;
  event.transaction = redact(event.transaction);
  if (event.message) event.message = redact(event.message);
  for (const ex of event.exception?.values ?? []) {
    ex.value = REDACTED_MESSAGE;
  }
  event.breadcrumbs = event.breadcrumbs?.map(scrubBreadcrumb);
  return event;
}

export function scrubBreadcrumb(crumb: Breadcrumb): Breadcrumb {
  crumb.message = redact(crumb.message);
  if (crumb.data) {
    for (const [key, value] of Object.entries(crumb.data)) {
      if (typeof value === 'string') crumb.data[key] = redact(value);
    }
  }
  return crumb;
}

/** No-op when no DSN is configured (local dev, tests). */
export function initSentry(): void {
  if (!environment.sentryDsn) return;
  Sentry.init({
    dsn: environment.sentryDsn,
    environment: environment.production ? 'production' : 'development',
    dataCollection: { userInfo: false, cookies: false, httpHeaders: false, httpBodies: [], urlQueryParams: false },
    // Errors only: no tracing, no replay, no session/release-health pings.
    // Console breadcrumbs are off (Console integration dropped) and so are
    // DOM-click breadcrumbs — clicks record element attributes like `title`,
    // which on the feed can be a file name.
    integrations: (defaults) => [
      ...defaults.filter((i) => !['Console', 'BrowserSession', 'Breadcrumbs'].includes(i.name)),
      Sentry.breadcrumbsIntegration({ dom: false })
    ],
    beforeSend: scrubEvent,
    beforeBreadcrumb: scrubBreadcrumb
  });
}
