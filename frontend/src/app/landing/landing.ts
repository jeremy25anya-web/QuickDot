import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { saveHostToken } from '../core/host-token';
import { ROOM_CODE_LENGTH, extractCode } from '../core/join-code';
import { RoomApiService } from '../core/room-api.service';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-landing',
  templateUrl: './landing.html',
  styleUrl: './landing.scss'
})
export class Landing implements OnDestroy {
  private readonly roomApi = inject(RoomApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly joinCode = signal('');
  readonly creating = signal(false);

  private pasteJoinTimer: ReturnType<typeof setTimeout> | undefined;

  onJoinInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const code = extractCode(input.value);
    input.value = code;
    this.joinCode.set(code);
    clearTimeout(this.pasteJoinTimer);
    // A pasted code or link joins on its own after a beat, so the person sees what landed.
    if ((event as InputEvent).inputType === 'insertFromPaste' && code.length === ROOM_CODE_LENGTH) {
      this.pasteJoinTimer = setTimeout(() => this.join(), 350);
    }
  }

  async createRoom(): Promise<void> {
    if (this.creating()) {
      return;
    }
    this.creating.set(true);
    try {
      const { code, hostToken } = await this.roomApi.createRoom();
      if (hostToken) {
        saveHostToken(code, hostToken);
      }
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
    clearTimeout(this.pasteJoinTimer);
    const code = this.joinCode();
    if (code.length !== ROOM_CODE_LENGTH) {
      this.toast.show(code ? 'Room codes are 6 characters.' : 'Enter the code you were given.', true);
      return;
    }
    this.router.navigate(['/r', code]);
  }

  ngOnDestroy(): void {
    clearTimeout(this.pasteJoinTimer);
  }
}
