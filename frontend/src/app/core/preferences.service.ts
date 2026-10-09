import { Injectable, signal } from '@angular/core';

export type ThemePref = 'auto' | 'light' | 'dark';
export type Layout = 'bubbles' | 'ledger';

const THEME_KEY = 'qd-theme';
const LAYOUT_KEY = 'qd-layout';

/** Storage can throw (private windows, blocked site data); preferences are only conveniences. */
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Not saved; the choice still applies for this visit.
  }
}

/**
 * Per-device display preferences: theme and feed layout. Nothing here
 * leaves the browser. The inline script in index.html applies the saved
 * theme before first paint; this service keeps it in sync afterwards.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  // matchMedia is missing in some test DOMs and very old browsers: treat as light.
  private readonly systemDark: Pick<MediaQueryList, 'matches' | 'addEventListener'> =
    window.matchMedia?.('(prefers-color-scheme: dark)') ?? { matches: false, addEventListener: () => {} };

  readonly theme = signal<ThemePref>(this.parseTheme(read(THEME_KEY)));
  readonly layout = signal<Layout>(read(LAYOUT_KEY) === 'ledger' ? 'ledger' : 'bubbles');

  constructor() {
    this.applyTheme();
    this.systemDark.addEventListener('change', () => this.applyTheme());
    // A change made in another tab follows here too.
    window.addEventListener('storage', (event) => {
      if (event.key === THEME_KEY) {
        this.theme.set(this.parseTheme(event.newValue));
        this.applyTheme();
      } else if (event.key === LAYOUT_KEY) {
        this.layout.set(event.newValue === 'ledger' ? 'ledger' : 'bubbles');
      }
    });
  }

  setTheme(pref: ThemePref): void {
    this.theme.set(pref);
    write(THEME_KEY, pref);
    this.applyTheme();
  }

  setLayout(layout: Layout): void {
    this.layout.set(layout);
    write(LAYOUT_KEY, layout);
  }

  private applyTheme(): void {
    const pref = this.theme();
    const dark = pref === 'dark' || (pref === 'auto' && this.systemDark.matches);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  }

  private parseTheme(value: string | null): ThemePref {
    return value === 'light' || value === 'dark' ? value : 'auto';
  }
}
