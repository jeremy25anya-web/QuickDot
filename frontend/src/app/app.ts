import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { NoticeService } from './core/notice.service';
import { PreferencesService } from './core/preferences.service';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  host: {
    '[class.in-room]': 'inRoom()',
    '(document:keydown.escape)': 'notice.dismiss()'
  }
})
export class App {
  protected readonly toast = inject(ToastService);
  protected readonly notice = inject(NoticeService);
  private readonly router = inject(Router);
  // Created here so the saved theme is applied and follows the system from the first page.
  private readonly prefs = inject(PreferencesService);

  /** The room is full-height and app-like: no footer, and no brandmark on narrow screens. */
  protected readonly inRoom = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => (e as NavigationEnd).urlAfterRedirects.startsWith('/r/'))
    ),
    { initialValue: false }
  );

  private readonly noticeOk = viewChild<ElementRef<HTMLButtonElement>>('noticeOk');
  private focusBeforeNotice: HTMLElement | null = null;

  constructor() {
    // Move focus into the notice when it opens and give it back when it closes.
    effect(() => {
      const ok = this.noticeOk();
      if (ok) {
        this.focusBeforeNotice = document.activeElement as HTMLElement | null;
        ok.nativeElement.focus();
      } else if (this.focusBeforeNotice) {
        this.focusBeforeNotice.focus();
        this.focusBeforeNotice = null;
      }
    });
  }

  onNoticeBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.notice.dismiss();
    }
  }
}
