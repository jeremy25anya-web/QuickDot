import { Injectable, signal } from '@angular/core';

export interface Notice {
  title: string;
  body: string;
}

/** A message that waits to be dismissed, for things a toast is too quick for (e.g. a full room). */
@Injectable({ providedIn: 'root' })
export class NoticeService {
  readonly current = signal<Notice | null>(null);

  show(title: string, body: string): void {
    this.current.set({ title, body });
  }

  dismiss(): void {
    this.current.set(null);
  }
}
