import type { ErrorEvent } from '@sentry/angular';

import { REDACTED_MESSAGE, redact, scrubBreadcrumb, scrubEvent } from './sentry';

describe('Sentry scrubbing', () => {
  it('drops request and user, and exception messages but not types', () => {
    const event: ErrorEvent = {
      type: undefined,
      request: { url: 'https://quickdot.amartian.ca/r/ABC234' },
      user: { ip_address: '203.0.113.5' },
      exception: { values: [{ type: 'SyntaxError', value: '"my secret" is not valid JSON' }] }
    };

    const out = scrubEvent(event);

    expect(out.request).toBeUndefined();
    expect(out.user).toBeUndefined();
    expect(out.exception?.values?.[0].type).toBe('SyntaxError');
    expect(out.exception?.values?.[0].value).toBe(REDACTED_MESSAGE);
  });

  it('redacts room codes in navigation and fetch breadcrumbs', () => {
    const nav = scrubBreadcrumb({ category: 'navigation', data: { from: '/', to: '/r/ABC234' } });
    const fetch = scrubBreadcrumb({ category: 'fetch', data: { url: 'https://api.quickdot.amartian.ca/api/rooms/XYZ789', status_code: 404 } });

    expect(nav.data?.['to']).toBe('/r/:code');
    expect(fetch.data?.['url']).toBe('https://api.quickdot.amartian.ca/api/rooms/:code');
    expect(fetch.data?.['status_code']).toBe(404);
  });

  it('leaves text without room paths alone', () => {
    expect(redact('/api/rooms')).toBe('/api/rooms');
    expect(redact(undefined)).toBeUndefined();
  });
});
