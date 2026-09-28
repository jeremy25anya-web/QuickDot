import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { RoomApiService } from '../core/room-api.service';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-landing',
  templateUrl: './landing.html',
  styleUrl: './landing.scss'
})
export class Landing {
  private readonly roomApi = inject(RoomApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly joinCode = signal('');
  readonly creating = signal(false);

  onJoinInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const cleaned = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
    input.value = cleaned;
    this.joinCode.set(cleaned);
  }

  async createRoom(): Promise<void> {
    if (this.creating()) {
      return;
    }
    this.creating.set(true);
    try {
      const { code } = await this.roomApi.createRoom();
      this.router.navigate(['/r', code]);
    } catch (err) {
      // 429 = per-IP room limit (spec §6); the server's message says which one.
      const limitMessage = err instanceof HttpErrorResponse && err.status === 429 ? err.error?.message : null;
      this.toast.show(limitMessage ?? "Couldn't create a room. Try again.", true);
    } finally {
      this.creating.set(false);
    }
  }

  join(): void {
    const code = this.joinCode().trim();
    if (code.length < 4) {
      this.toast.show('Enter the code you were given.', true);
      return;
    }
    this.router.navigate(['/r', code]);
  }
}
