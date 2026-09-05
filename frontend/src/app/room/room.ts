import { Component, HostListener, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { RoomService } from '../core/room.service';
import { ToastService } from '../core/toast.service';
import { FeedItem } from '../core/feed-item';
import { fileExtension, formatSize } from '../core/limits';

type MenuAction = { label: string; run: () => void };

@Component({
  selector: 'app-room',
  templateUrl: './room.html',
  styleUrl: './room.scss'
})
export class Room implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly room = inject(RoomService);

  protected readonly now = signal(Date.now());
  protected readonly menuFor = signal<string | null>(null);
  protected readonly draft = signal('');

  private tickHandle: ReturnType<typeof setInterval> | undefined;

  protected readonly ttlRemainingMs = computed(() => {
    const end = this.room.expiresAt();
    return end == null ? null : Math.max(0, end - this.now());
  });

  protected readonly ttlText = computed(() => {
    const left = this.ttlRemainingMs();
    if (left == null) return '';
    const m = Math.floor(left / 60000);
    const s = Math.floor((left % 60000) / 1000);
    return `${m}:${s < 10 ? '0' : ''}${s} left`;
  });

  protected readonly ttlWarn = computed(() => (this.ttlRemainingMs() ?? Infinity) < 120000);

  constructor() {
    effect(() => {
      if (this.ttlRemainingMs() === 0) {
        this.leaveWith("Room expired. Everything's gone.");
      }
    });
    effect(() => {
      const err = this.room.fatalError();
      if (err) {
        this.leaveWith(err, true);
      }
    });
  }

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code');
    if (!code) {
      this.router.navigate(['/']);
      return;
    }
    this.room.connect(code.toUpperCase());
    this.tickHandle = setInterval(() => this.now.set(Date.now()), 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.tickHandle);
    this.room.leave();
  }

  private leaveWith(message: string, error = false): void {
    this.toast.show(message, error);
    this.router.navigate(['/']);
  }

  closeRoom(): void {
    this.leaveWith("Room closed. Everything's gone.");
  }

  sendDraft(): void {
    this.room.sendText(this.draft());
    this.draft.set('');
  }

  onComposerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendDraft();
    }
  }

  onComposerInput(event: Event): void {
    const el = event.target as HTMLTextAreaElement;
    this.draft.set(el.value);
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
  }

  onComposerPaste(event: ClipboardEvent): void {
    const items = event.clipboardData?.items;
    if (!items) return;
    for (const item of items) {
      if (item.kind === 'file') {
        const file = item.getAsFile();
        if (file) {
          event.preventDefault();
          this.room.sendFile(file);
        }
      }
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.room.sendFile(file);
    }
    input.value = '';
  }

  copyCode(): void {
    this.copy(this.room.code() ?? '', 'Code copied — send it over.');
  }

  copyLink(): void {
    const code = this.room.code();
    this.copy(`${location.origin}/r/${code}`, 'Link copied — anyone with it joins this room.');
  }

  private copy(text: string, label: string): void {
    navigator.clipboard?.writeText(text).then(
      () => this.toast.show(label),
      () => this.toast.show(label)
    );
  }

  senderLabel(item: FeedItem): string {
    return item.outgoing ? 'you' : item.senderName;
  }

  linkify(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML.replace(
      /(https?:\/\/[^\s<]+)/g,
      (url) => `<a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a>`
    );
  }

  fileIconLabel(name: string): string {
    return (fileExtension(name) || 'FILE').slice(0, 4).toUpperCase();
  }

  formatSize(bytes: number): string {
    return formatSize(bytes);
  }

  toggleMenu(item: FeedItem, event: Event): void {
    event.stopPropagation();
    this.menuFor.set(this.menuFor() === item.id ? null : item.id);
  }

  menuActions(item: FeedItem): MenuAction[] {
    const actions: MenuAction[] = [];
    if (item.kind === 'text') {
      actions.push({ label: 'Copy', run: () => this.copy(item.text ?? '', 'Copied.') });
    } else {
      actions.push({ label: 'Download', run: () => this.downloadItem(item) });
    }
    actions.push({
      label: 'Report',
      run: () => this.toast.show('Reported. In the live app this flags the room for review.')
    });
    return actions;
  }

  runMenuAction(action: MenuAction): void {
    action.run();
    this.menuFor.set(null);
  }

  private downloadItem(item: FeedItem): void {
    if (!item.url) return;
    const a = document.createElement('a');
    a.href = item.url;
    a.download = item.name ?? (item.kind === 'image' ? 'quickdot-image.png' : 'quickdot-file');
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.menuFor.set(null);
  }
}
