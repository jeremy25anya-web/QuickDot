# QuickDot Accessibility Checklist

**Target:** WCAG 2.0 Level AA (the standard AODA points to in Ontario).
Meeting WCAG 2.1 AA where easy is good practice and a genuine portfolio signal.

The reference prototype already does a lot right: keyboard operable, visible
focus, `aria-live` on the feed, labeled icon buttons, reduced-motion support,
`lang="en"`, alt text on shared images. The items below are what to **verify or
fix** for the production build.

## Must-fix (found in a real contrast audit of the current palette)

- [ ] **Faint gray text fails AA.** `--ink-faint #9AA19C` on the paper
      background is only **2.3:1** (AA needs 4.5:1 for normal text). Wherever
      faint gray carries *information* — the countdown, hints, the "you're
      {name}" line, file sub-labels, empty-state text — use `--ink-soft
      #5B635F` (**5.39:1**, passes) or darker. Reserve `#9AA19C` for purely
      decorative use only.
- [ ] **Rust accent only passes for large text.** `--signal #C6462F` on paper
      is **4.24:1** — fine for large/bold display (the countdown warning, the
      dot) but **failing for normal-size text and links**. For small rust text
      or links, introduce a darker token, e.g. `--signal-text #A83420`
      (**5.76:1**), and keep `#C6462F` for the dot and large elements.
- [ ] **Don't rely on color alone.** The error toast is distinguished mainly by
      turning rust. Add a text or icon cue (e.g. a leading "⚠") so the error is
      perceivable without color (WCAG 1.4.1).

## Touch targets & pointer
- [ ] **44×44 CSS px minimum** for interactive controls. The per-message ⋯
      button is currently ~22px — enlarge its hit area (padding or an invisible
      expanded target) to ≥44px. Check the copy-code/copy-link buttons and the
      composer icon buttons too.
- [ ] Ensure adequate spacing so adjacent targets aren't easily mis-tapped.

## Keyboard
- [ ] Every action reachable and operable by keyboard: create, join, send,
      attach, copy, download, report, close.
- [ ] Logical focus order; no keyboard traps.
- [ ] The ⋯ menu opens with keyboard, is arrow-key navigable, closes on `Esc`,
      and returns focus to the ⋯ button.
- [ ] Visible focus indicator on **all** interactive elements, including custom
      buttons and menu items (don't remove outlines without a replacement).

## Screen reader / semantics
- [ ] Landmarks: `<header>`, `<main>`, `<footer>` present and correct; the room
      view exposed as a region.
- [ ] Heading structure is sane (a single `<h1>`, meaningful order).
- [ ] **Announce dynamic changes**: new messages, "connected / someone joined /
      someone left", and toasts must reach screen readers. The feed uses
      `aria-live="polite"`; give status/toasts `role="status"` (or `role="alert"`
      for errors) so they're announced, not just shown.
- [ ] The ⋯ menu uses proper roles (`role="menu"` / `role="menuitem"`).
- [ ] All icon-only buttons keep descriptive `aria-label`s (attach, send, ⋯,
      copy, close).
- [ ] Shared images keep meaningful `alt`; decorative graphics (the live-link
      dots/wire) are hidden from assistive tech (`aria-hidden`).
- [ ] The room code is announced clearly (consider an `aria-label` that reads it
      out character by character, since the display is spaced monospace).

## Forms & input
- [ ] The join-code input has a programmatic label (not just a placeholder).
- [ ] Errors (bad code, file too large, blocked type) are announced and describe
      how to fix, in the interface's voice.

## Motion & preferences
- [ ] `prefers-reduced-motion` respected across all animation (already in
      prototype — keep it as new motion is added, e.g. the reconnect state).

## Testing checklist before launch
- [ ] Keyboard-only walkthrough of every flow.
- [ ] Screen-reader pass (VoiceOver or NVDA) of create → share → exchange →
      close.
- [ ] Automated scan (axe DevTools or Lighthouse) with zero critical issues.
- [ ] Contrast re-checked after the palette fixes above.
- [ ] Zoom to 200% without loss of content or function.
