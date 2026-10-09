// The host token proves the right to end a room for everyone (the server
// checks it, ADR-0022). Kept in sessionStorage so it survives a refresh of
// the creating tab but not a new tab or a closed browser.

const KEY = 'qd-host';

interface Stored {
  code: string;
  token: string;
}

export function saveHostToken(code: string, token: string): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ code, token } satisfies Stored));
  } catch {
    // Without storage this tab just can't end the room for everyone.
  }
}

export function hostTokenFor(code: string | null): string | null {
  if (!code) return null;
  try {
    const stored = JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as Stored | null;
    return stored?.code === code && stored.token ? stored.token : null;
  } catch {
    return null;
  }
}

export function clearHostToken(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // Nothing to clear.
  }
}
