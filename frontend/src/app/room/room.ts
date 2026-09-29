import { Component, ElementRef, HostListener, OnDestroy, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
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
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly now = signal(Date.now());
  protected readonly menuFor = signal<string | null>(null);
  protected readonly draft = signal('');
  /** Text for the screen-reader-only live region (joins, leaves, time warning). */
  protected readonly announcement = signal('');

  /** The ⋯ button that opened the current menu, so focus can return to it. */
  private menuTrigger: HTMLElement | null = null;
  private knownParticipants = new Map<string, string>();
  private knownYouId: string | null = null;

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

  /** The code spelled out so screen readers read it character by character. */
  protected readonly codeLabel = computed(() => {
    const code = this.room.code();
    return code ? `Room code: ${code.split('').join(' ')}` : 'Room code loading';
  });

  constructor() {
    effect(() => {
      if (this.ttlRemainingMs() === 0) {
        this.leaveWith("Room expired. Everything's gone.");
      }
    });
    effect(() => {
      if (this.ttlWarn()) {
        untracked(() => this.announce('Two minutes left. The room and everything in it will be deleted when time runs out.'));
      }
    });
    effect(() => {
      const you = this.room.you();
      const list = this.room.participants();
      untracked(() => this.announcePresenceChanges(you?.id ?? null, list));
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
    if (this.menuFor() === item.id) {
      this.closeMenu(false);
      return;
    }
    this.menuTrigger = event.currentTarget as HTMLElement;
    this.menuFor.set(item.id);
    this.focusMenuItem(0);
  }

  openMenuFromKeyboard(item: FeedItem, event: Event): void {
    event.preventDefault();
    if (this.menuFor() !== item.id) {
      this.toggleMenu(item, event);
    } else {
      this.focusMenuItem(0);
    }
  }

  /** Arrow/Home/End move between items, Esc closes and returns focus, Tab closes. */
  onMenuKeydown(event: KeyboardEvent): void {
    const items = this.menuItems();
    const index = items.indexOf(document.activeElement as HTMLElement);
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.focusMenuItem((index + 1) % items.length);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.focusMenuItem((index - 1 + items.length) % items.length);
        break;
      case 'Home':
        event.preventDefault();
        this.focusMenuItem(0);
        break;
      case 'End':
        event.preventDefault();
        this.focusMenuItem(items.length - 1);
        break;
      case 'Escape':
        event.preventDefault();
        this.closeMenu(true);
        break;
      case 'Tab':
        this.closeMenu(false);
        break;
    }
  }

  private menuItems(): HTMLElement[] {
    return Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.menu [role="menuitem"]'));
  }

  /** Waits a tick so the menu has rendered before focusing into it. */
  private focusMenuItem(index: number): void {
    setTimeout(() => this.menuItems()[index]?.focus());
  }

  private closeMenu(returnFocus: boolean): void {
    this.menuFor.set(null);
    if (returnFocus) {
      this.menuTrigger?.focus();
    }
    this.menuTrigger = null;
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
    this.closeMenu(true);
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
    if (this.menuFor() !== null) {
      this.closeMenu(false);
    }
  }

  /**
   * Announces who joined or left. A change of our own id means a fresh
   * welcome (first connect or a reconnect), which resets the baseline
   * silently rather than announcing everyone already in the room.
   */
  private announcePresenceChanges(youId: string | null, list: { id: string; name: string }[]): void {
    const current = new Map(list.map((p) => [p.id, p.name]));
    if (youId !== this.knownYouId) {
      this.knownYouId = youId;
      this.knownParticipants = current;
      return;
    }
    const joined = list.filter((p) => !this.knownParticipants.has(p.id)).map((p) => `${p.name} joined`);
    const left = [...this.knownParticipants].filter(([id]) => !current.has(id)).map(([, name]) => `${name} left`);
    this.knownParticipants = current;
    const message = [...joined, ...left].join('. ');
    if (message) {
      this.announce(message);
    }
  }

  /** Clearing first makes screen readers re-read a repeated message. */
  private announce(message: string): void {
    this.announcement.set('');
    setTimeout(() => this.announcement.set(message), 50);
  }
}
