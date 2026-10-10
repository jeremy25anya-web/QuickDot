import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FeedItem } from '../core/feed-item';
import { clearHostToken, hostTokenFor } from '../core/host-token';
import { fileExtension, formatSize } from '../core/limits';
import { NoticeService } from '../core/notice.service';
import { Layout, PreferencesService, ThemePref } from '../core/preferences.service';
import { ParticipantView } from '../core/protocol';
import { ABUSE_EMAIL, reportMailto } from '../core/report';
import { RoomService } from '../core/room.service';
import { ToastService } from '../core/toast.service';

type MenuAction = { label: string; run: () => void };
type Flashable = 'code' | 'chip' | 'link';

const LEAVE_ARM_MS = 3000;
const FLASH_MS = 1400;
/** Space a ⋯ menu needs below its trigger: three actions plus padding. */
const MENU_ROOM = 140;

@Component({
  selector: 'app-room',
  imports: [NgTemplateOutlet],
  templateUrl: './room.html',
  styleUrl: './room.scss'
})
export class Room implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly notice = inject(NoticeService);
  protected readonly prefs = inject(PreferencesService);
  protected readonly room = inject(RoomService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  private readonly feedEl = viewChild<ElementRef<HTMLElement>>('feed');
  private readonly composerEl = viewChild<ElementRef<HTMLTextAreaElement>>('composer');

  protected readonly now = signal(Date.now());
  /** Id of the item whose ⋯ menu is open. */
  protected readonly menuFor = signal<string | null>(null);
  /** Open the ⋯ menu upwards when there isn't room for it below the trigger. */
  protected readonly menuUp = signal(false);
  protected readonly roomMenuOpen = signal(false);
  /** The first tap on the X arms it; a second tap within 3 s leaves. */
  protected readonly leaveArmed = signal(false);
  /** "End room for everyone" also takes a second, deliberate tap. */
  protected readonly endArmed = signal(false);
  protected readonly flashed = signal<Flashable | null>(null);
  protected readonly dragging = signal(false);
  protected readonly draft = signal('');
  /** Text for the screen-reader-only live region (joins, leaves, time warning, copies). */
  protected readonly announcement = signal('');

  protected readonly isTouch = window.matchMedia?.('(hover: none)').matches ?? false;
  /** Phones get the system share sheet; everything else copies. */
  protected readonly canShare = this.isTouch && typeof navigator.share === 'function';

  protected readonly layoutOptions: [Layout, string][] = [['bubbles', 'Bubbles'], ['ledger', 'Ledger']];
  protected readonly themeOptions: [ThemePref, string][] = [['auto', 'Auto'], ['light', 'Light'], ['dark', 'Dark']];

  /** The ⋯ button that opened the current menu, so focus can return to it. */
  private menuTrigger: HTMLElement | null = null;
  private knownParticipants = new Map<string, string>();
  private knownYouId: string | null = null;
  private dragDepth = 0;

  private tickHandle: ReturnType<typeof setInterval> | undefined;
  private leaveTimer: ReturnType<typeof setTimeout> | undefined;
  private flashTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly ttlRemainingMs = computed(() => {
    const end = this.room.expiresAt();
    return end == null ? null : Math.max(0, end - this.now());
  });

  protected readonly ttlClock = computed(() => {
    const left = this.ttlRemainingMs();
    if (left == null) return '';
    const m = Math.floor(left / 60000);
    const s = Math.floor((left % 60000) / 1000);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  });

  protected readonly ttlText = computed(() => (this.ttlClock() ? `${this.ttlClock()} left` : ''));

  protected readonly ttlWarn = computed(() => (this.ttlRemainingMs() ?? Infinity) < 120000);

  /** The code spelled out so screen readers read it character by character. */
  protected readonly codeLabel = computed(() => {
    const code = this.room.code();
    return code ? `Room code: ${code.split('').join(' ')}. Copy` : 'Room code loading';
  });

  /** Everyone else, in colour-slot order. */
  protected readonly others = computed(() => {
    const slots = this.room.slots();
    return [...this.room.participants()].sort((a, b) => (slots.get(a.id) ?? 0) - (slots.get(b.id) ?? 0));
  });

  /** Once someone joins, the invite collapses into one quiet header row. */
  protected readonly compact = computed(() => this.others().length > 0);

  /** The far node and "Connected" take the first other person's colour. */
  protected readonly headAccent = computed(() => {
    const first = this.others()[0];
    return first ? this.slotClass(first.id) : '';
  });

  protected readonly isHost = computed(() => hostTokenFor(this.room.code()) !== null);

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
      if (!err) return;
      if (untracked(() => this.room.fatalCode()) === 'ROOM_FULL') {
        this.router.navigate(['/']);
        this.notice.show('This room is full', 'QuickDot rooms hold up to 8 people. Ask someone to leave, or start a new room.');
      } else {
        this.leaveWith(err, true);
      }
    });
    effect(() => {
      const by = this.room.endedBy();
      if (by) {
        this.leaveWith(`${by} ended the room. Everything's gone.`);
      }
    });
    // Keep the newest item in view.
    effect(() => {
      this.room.items().length;
      const feed = untracked(() => this.feedEl())?.nativeElement;
      if (feed) {
        setTimeout(() => (feed.scrollTop = feed.scrollHeight));
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
    clearTimeout(this.leaveTimer);
    clearTimeout(this.flashTimer);
    this.room.leave();
  }

  private leaveWith(message: string, error = false): void {
    this.toast.show(message, error);
    this.router.navigate(['/']);
  }

  /** You leave; the room carries on for everyone else until it expires. */
  leaveRoom(): void {
    const others = this.room.participants().length;
    clearHostToken();
    this.leaveWith(others ? 'You left. The room stays open for the others until it expires.' : "You left the room. Everything's gone.");
  }

  endRoomForEveryone(): void {
    const token = hostTokenFor(this.room.code());
    if (!token) return;
    this.room.endRoom(token);
    clearHostToken();
    this.leaveWith("Room ended. Everything's gone.");
  }

  // ---------------------------------------------------------------- composer

  sendDraft(): void {
    this.room.sendText(this.draft());
    this.draft.set('');
    const ta = this.composerEl()?.nativeElement;
    if (ta) {
      ta.value = '';
      this.autosize(ta);
    }
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
    this.autosize(el);
  }

  private autosize(el: HTMLTextAreaElement): void {
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.sendFiles(input.files);
    input.value = '';
  }

  private sendFiles(files: FileList | File[] | null | undefined): void {
    for (const file of Array.from(files ?? [])) {
      this.room.sendFile(file);
    }
  }

  /** Paste anywhere in the room: files send straight away, text lands in the composer. */
  @HostListener('document:paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    const data = event.clipboardData;
    if (!data) return;
    const files = Array.from(data.items)
      .filter((item) => item.kind === 'file')
      .map((item) => item.getAsFile())
      .filter((file): file is File => file !== null);
    if (files.length) {
      event.preventDefault();
      this.sendFiles(files);
      return;
    }
    const target = event.target as HTMLElement | null;
    if (target && /^(INPUT|TEXTAREA)$/.test(target.tagName)) return;
    const text = data.getData('text');
    const ta = this.composerEl()?.nativeElement;
    if (text && ta) {
      event.preventDefault();
      ta.focus();
      ta.setRangeText(text, ta.selectionStart, ta.selectionEnd, 'end');
      this.draft.set(ta.value);
      this.autosize(ta);
    }
  }

  // ----------------------------------------------------------- drag and drop

  private hasFiles(event: DragEvent): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files');
  }

  @HostListener('window:dragenter', ['$event'])
  onDragEnter(event: DragEvent): void {
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    this.dragDepth++;
    this.dragging.set(true);
  }

  @HostListener('window:dragover', ['$event'])
  onDragOver(event: DragEvent): void {
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
  }

  @HostListener('window:dragleave', ['$event'])
  onDragLeave(event: DragEvent): void {
    if (this.hasFiles(event) && --this.dragDepth <= 0) {
      this.dragDepth = 0;
      this.dragging.set(false);
    }
  }

  @HostListener('window:drop', ['$event'])
  onDrop(event: DragEvent): void {
    this.dragDepth = 0;
    this.dragging.set(false);
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    this.sendFiles(event.dataTransfer?.files);
  }

  // ----------------------------------------------------------- code and link

  roomLink(): string {
    return `${location.origin}/r/${this.room.code()}`;
  }

  copyCode(from: Flashable): void {
    this.copy(this.room.code() ?? '');
    this.flash(from);
    this.announce('Code copied.');
  }

  /** The share sheet on phones; elsewhere copy, with the button flipping to "Copied". */
  shareLink(): void {
    if (this.canShare) {
      navigator.share({ title: 'QuickDot room', text: `Join my QuickDot room: ${this.room.code()}`, url: this.roomLink() }).catch(() => {});
      return;
    }
    this.copy(this.roomLink());
    this.flash('link');
    this.announce('Link copied.');
  }

  private copy(text: string, toastMessage?: string): void {
    const done = () => {
      if (toastMessage) this.toast.show(toastMessage);
    };
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(done, done);
    } else {
      done();
    }
  }

  private flash(what: Flashable): void {
    this.flashed.set(what);
    clearTimeout(this.flashTimer);
    this.flashTimer = setTimeout(() => this.flashed.set(null), FLASH_MS);
  }

  // ---------------------------------------------------------- leave (the X)

  onLeaveClick(event: Event): void {
    event.stopPropagation();
    if (this.leaveArmed()) {
      this.leaveRoom();
      return;
    }
    this.closeMenu(false);
    this.leaveArmed.set(true);
    clearTimeout(this.leaveTimer);
    this.leaveTimer = setTimeout(() => this.leaveArmed.set(false), LEAVE_ARM_MS);
  }

  private disarmLeave(): void {
    clearTimeout(this.leaveTimer);
    this.leaveArmed.set(false);
  }

  // ------------------------------------------------------------ presentation

  slotClass(participantId: string | undefined): string {
    const slot = participantId ? this.room.slots().get(participantId) : undefined;
    return slot === undefined ? '' : `p${slot}`;
  }

  itemClass(item: FeedItem): string {
    return item.outgoing ? 'out' : `in ${this.slotClass(item.senderId)}`;
  }

  senderLabel(item: FeedItem): string {
    return item.outgoing ? 'you' : item.senderName;
  }

  /** "just now", "5m", "1h", refreshed by the 1 s clock tick. */
  ago(at: number): string {
    const seconds = (this.now() - at) / 1000;
    if (seconds < 45) return 'just now';
    const minutes = Math.round(seconds / 60);
    return minutes < 60 ? `${minutes}m` : `${Math.floor(minutes / 60)}h`;
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

  // ------------------------------------------------------------------- menus

  toggleMenu(item: FeedItem, event: Event): void {
    event.stopPropagation();
    if (this.menuFor() === item.id) {
      this.closeMenu(false);
      return;
    }
    this.closeMenu(false);
    this.disarmLeave();
    this.menuTrigger = event.currentTarget as HTMLElement;
    // The feed clips its overflow, so a menu on one of the last items would
    // hang below the composer and hide Report. Flip it when it won't fit.
    const feed = this.feedEl()?.nativeElement.getBoundingClientRect();
    const trigger = this.menuTrigger.getBoundingClientRect();
    if (feed) {
      const below = feed.bottom - trigger.bottom;
      const above = trigger.top - feed.top;
      this.menuUp.set(below < MENU_ROOM && above > below);
    }
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

  toggleRoomMenu(event: Event): void {
    event.stopPropagation();
    if (this.roomMenuOpen()) {
      this.closeMenu(false);
      return;
    }
    this.closeMenu(false);
    this.disarmLeave();
    this.menuTrigger = event.currentTarget as HTMLElement;
    this.roomMenuOpen.set(true);
    this.focusMenuItem(0);
  }

  openRoomMenuFromKeyboard(event: Event): void {
    event.preventDefault();
    if (!this.roomMenuOpen()) {
      this.toggleRoomMenu(event);
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
        event.stopPropagation();
        this.closeMenu(true);
        break;
      case 'Tab':
        this.closeMenu(false);
        break;
    }
  }

  private menuItems(): HTMLElement[] {
    return Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.menu [role^="menuitem"]'));
  }

  /** Waits a tick so the menu has rendered before focusing into it. */
  private focusMenuItem(index: number): void {
    setTimeout(() => this.menuItems()[index]?.focus());
  }

  private closeMenu(returnFocus: boolean): void {
    this.menuFor.set(null);
    this.roomMenuOpen.set(false);
    this.endArmed.set(false);
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
    actions.push({ label: 'Report', run: () => this.reportItem(item) });
    return actions;
  }

  runMenuAction(action: MenuAction): void {
    action.run();
    this.closeMenu(true);
  }

  roomMenuShare(): void {
    if (this.canShare) {
      this.shareLink();
    } else {
      this.copy(this.roomLink(), 'Link copied. Anyone with it joins this room.');
    }
    this.closeMenu(true);
  }

  roomMenuCopyCode(): void {
    this.copy(this.room.code() ?? '', 'Code copied.');
    this.closeMenu(true);
  }

  roomMenuLeave(): void {
    this.closeMenu(false);
    this.leaveRoom();
  }

  /** Ending wipes the room for everyone, so it takes a second, deliberate tap. */
  roomMenuEnd(event: Event): void {
    event.stopPropagation();
    if (this.endArmed()) {
      this.closeMenu(false);
      this.endRoomForEveryone();
      return;
    }
    this.endArmed.set(true);
  }

  /** Drafts an abuse report in the reporter's mail app (see core/report.ts). */
  private reportItem(item: FeedItem): void {
    const code = this.room.code();
    if (!code) return;
    const a = document.createElement('a');
    a.href = reportMailto(item, code);
    document.body.appendChild(a);
    a.click();
    a.remove();
    // Name the address in case no mail app is set up to catch the link.
    this.toast.show(`Opening an email to ${ABUSE_EMAIL}.`);
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
    if (this.menuFor() !== null || this.roomMenuOpen()) {
      this.closeMenu(false);
    }
    this.disarmLeave();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.menuFor() !== null || this.roomMenuOpen()) {
      this.closeMenu(true);
    }
    this.disarmLeave();
  }

  // ----------------------------------------------------------- announcements

  /**
   * Announces who joined or left. A change of our own id means a fresh
   * welcome (first connect or a reconnect), which resets the baseline
   * silently rather than announcing everyone already in the room.
   */
  private announcePresenceChanges(youId: string | null, list: ParticipantView[]): void {
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
