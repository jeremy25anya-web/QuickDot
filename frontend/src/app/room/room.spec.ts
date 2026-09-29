import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { FeedItem } from '../core/feed-item';
import { ParticipantView } from '../core/protocol';
import { RoomService } from '../core/room.service';
import { Room } from './room';

class FakeRoomService {
  readonly code = signal<string | null>('AB2CD3');
  readonly you = signal<ParticipantView | null>({ id: 'me', name: 'Brave Otter' });
  readonly participants = signal<ParticipantView[]>([]);
  readonly items = signal<FeedItem[]>([]);
  readonly expiresAt = signal<number | null>(Date.now() + 30 * 60_000);
  readonly connectionState = signal<'connecting' | 'open' | 'reconnecting' | 'closed'>('open');
  readonly fatalError = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly connected = computed(() => this.participants().length > 0);
  connect(): void {}
  leave(): void {}
  sendText(): void {}
  sendFile(): void {}
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe('Room accessibility', () => {
  let fixture: ComponentFixture<Room>;
  let room: FakeRoomService;
  let el: HTMLElement;

  beforeEach(async () => {
    room = new FakeRoomService();
    await TestBed.configureTestingModule({
      imports: [Room],
      providers: [
        provideRouter([]),
        { provide: RoomService, useValue: room },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ code: 'AB2CD3' }) } } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(Room);
    el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el); // focus() only works on attached elements
    await fixture.whenStable();
  });

  afterEach(() => el.remove());

  const liveRegion = () => el.querySelector('[aria-live="polite"].sr-only')?.textContent?.trim();

  it('spells out the room code for screen readers', () => {
    expect(el.querySelector('.codeval .sr-only')?.textContent).toContain('Room code: A B 2 C D 3');
  });

  it('announces joins and leaves, but not the people already there on (re)connect', async () => {
    room.participants.set([{ id: 'p1', name: 'Quiet Heron' }]);
    await fixture.whenStable();
    await wait(80);
    expect(liveRegion()).toBe('Quiet Heron joined');

    room.participants.set([]);
    await fixture.whenStable();
    await wait(80);
    expect(liveRegion()).toBe('Quiet Heron left');

    // Reconnect: new id for us, existing participant arrives with the welcome.
    room.you.set({ id: 'me-2', name: 'Brave Otter' });
    room.participants.set([{ id: 'p1', name: 'Quiet Heron' }]);
    await fixture.whenStable();
    await wait(80);
    expect(liveRegion()).toBe('Quiet Heron left');
  });

  it('⋯ menu: opens into the first item, arrows move, Escape returns focus', async () => {
    room.items.set([{ id: 'm1', kind: 'text', outgoing: false, senderName: 'Quiet Heron', text: 'hi' }]);
    await fixture.whenStable();
    const dots = el.querySelector<HTMLButtonElement>('.dots')!;

    dots.click();
    await fixture.whenStable();
    await wait(10);
    const items = Array.from(el.querySelectorAll<HTMLElement>('[role="menuitem"]'));
    expect(dots.getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(items[0]);

    const menu = el.querySelector<HTMLElement>('[role="menu"]')!;
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    await wait(10);
    expect(document.activeElement).toBe(items[1]);

    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(el.querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(dots);
  });
});
