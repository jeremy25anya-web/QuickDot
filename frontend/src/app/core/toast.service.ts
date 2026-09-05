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
    this.timer = setTimeout(() => this.current.set(null), 2600);
  }
}
