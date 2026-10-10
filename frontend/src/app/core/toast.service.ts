import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  error: boolean;
}

/** Older toasts give way once this many are on screen. */
const MAX_TOASTS = 3;

/**
 * Short, self-dismissing messages, stacked in the bottom-left corner like a
 * chat log: newest at the bottom, each on its own timer.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private nextId = 0;

  show(message: string, error = false): void {
    // The same message again (a double tap, a repeated rejection) just restarts.
    const others = this.toasts().filter((t) => t.message !== message);
    const toast = { id: this.nextId++, message, error };
    this.toasts.set([...others, toast].slice(-MAX_TOASTS));
    // Long enough to read: about 4 s for a short line, longer for a sentence.
    const ms = Math.min(8000, 2500 + message.length * 45);
    setTimeout(() => this.dismiss(toast.id), ms);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
