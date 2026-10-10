import { Injectable, signal } from '@angular/core';

export interface Toast {
  message: string;
  error: boolean;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly current = signal<Toast | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  show(message: string, error = false): void {
    this.current.set({ message, error });
    clearTimeout(this.timer);
    // Long enough to read: about 4 s for a short line, longer for a sentence.
    const ms = Math.min(8000, 2500 + message.length * 45);
    this.timer = setTimeout(() => this.current.set(null), ms);
  }
}
