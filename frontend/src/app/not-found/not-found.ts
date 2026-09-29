import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

// Catch-all route. With the Workers SPA fallback (ADR-0016) every unknown
// path serves index.html, so the 404 has to be an Angular route rather than
// a static 404.html.
@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <main class="nf">
      <div class="link" aria-hidden="true">
        <span class="node"></span><span class="wire"></span><span class="node gone"></span>
      </div>
      <h1>Nothing here.</h1>
      <p>This page doesn't exist. If someone sent you a room link, the room may have expired. Rooms last 30 minutes.</p>
      <a class="home" routerLink="/">Start a new room</a>
    </main>
  `,
  styles: `
    :host {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
    }

    .nf {
      max-width: 34ch;
      padding: 96px 22px 40px;
      text-align: center;
    }

    .link {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 22px;
    }

    .node {
      width: 14px;
      height: 14px;
      border-radius: 50%;
      background: var(--ink);

      &.gone {
        background: transparent;
        border: 2px dashed var(--line-strong);
      }
    }

    .wire {
      width: 64px;
      height: 2px;
      background: repeating-linear-gradient(90deg, var(--line-strong) 0 6px, transparent 6px 11px);
    }

    h1 {
      font-family: 'Space Grotesk';
      font-size: 24px;
      letter-spacing: -.02em;
      margin: 0 0 8px;
      color: var(--ink);
    }

    p {
      color: var(--ink-soft);
      font-size: 15px;
      line-height: 1.6;
      margin: 0 0 22px;
    }

    .home {
      display: inline-block;
      padding: 12px 20px;
      border-radius: var(--radius);
      background: var(--ink);
      color: var(--paper-raised);
      font-weight: 600;
      font-size: 14px;
      text-decoration: none;
    }
  `
})
export class NotFound {}
