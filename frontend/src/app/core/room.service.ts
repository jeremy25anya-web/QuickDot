import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { decodeChunkFrame, encodeChunkFrame } from './chunk-frame';
import { FeedItem } from './feed-item';
import { BLOCKED_EXTENSIONS, CHUNK_SIZE_BYTES, MAX_FILE_BYTES, fileExtension } from './limits';
import {
  ErrorMessage,
  FileEndMessage,
  FileStartMessage,
  IncomingTextMessage,
  JoinedMessage,
  LeftMessage,
  ParticipantView,
  RoomEndedMessage,
  ServerMessage,
  WelcomeMessage
} from './protocol';
import { ToastService } from './toast.service';

export type ConnectionState = 'connecting' | 'open' | 'reconnecting' | 'closed';

const RECONNECT_DELAYS_MS = [1000, 2000, 4000, 8000];
/** One colour per other person (styles.scss --p0..--p6); you stay neutral. */
export const PARTICIPANT_COLOURS = 7;

interface IncomingTransfer {
  from: ParticipantView;
  name: string;
  mime: string;
  parts: Uint8Array[];
}

function randomId(): string {
  return crypto.randomUUID();
}

/**
 * Owns the single active room's WebSocket connection and all room state.
 * One room is active at a time app-wide (matches the prototype and the
 * backend's one-connection-per-participant model) — connect() resets state.
 */
@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly toast = inject(ToastService);

  readonly code = signal<string | null>(null);
  readonly you = signal<ParticipantView | null>(null);
  readonly participants = signal<ParticipantView[]>([]);
  readonly items = signal<FeedItem[]>([]);
  readonly expiresAt = signal<number | null>(null);
  readonly connectionState = signal<ConnectionState>('connecting');
  /** Set when the server rejects the connection outright (room gone/full). */
  readonly fatalError = signal<string | null>(null);
  /** The server's error code behind fatalError, e.g. ROOM_FULL. */
  readonly fatalCode = signal<string | null>(null);
  /** Name of whoever ended the room for everyone, once they have. */
  readonly endedBy = signal<string | null>(null);
  readonly uploading = signal(false);
  /**
   * Colour slot per other participant, for this viewer only: the first
   * person you meet gets 0, the next 1, and so on. A slot is kept for the
   * life of the room, so someone's old messages keep their colour after
   * they leave.
   */
  readonly slots = signal<ReadonlyMap<string, number>>(new Map());

  readonly connected = computed(() => this.participants().length > 0);

  private socket: WebSocket | null = null;
  private readonly incomingTransfers = new Map<string, IncomingTransfer>();
  private intentionalClose = false;
  private reconnectAttempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  /** Uploads run one at a time (spec §6: 1 concurrent upload per connection). */
  private uploadQueue: Promise<void> = Promise.resolve();
  /** Bumped on every reset, so queued uploads from a previous room are dropped. */
  private generation = 0;

  connect(code: string): void {
    this.reset();
    this.intentionalClose = false;
    this.reconnectAttempt = 0;
    this.code.set(code);
    this.openSocket(code);
  }

  leave(): void {
    this.intentionalClose = true;
    clearTimeout(this.reconnectTimer);
    this.reset();
  }

  private openSocket(code: string): void {
    this.connectionState.set(this.reconnectAttempt > 0 ? 'reconnecting' : 'connecting');

    const socket = new WebSocket(`${environment.wsBaseUrl}/ws/${code}`);
    socket.binaryType = 'arraybuffer';
    this.socket = socket;

    socket.onmessage = (event) => this.handleMessage(event);
    socket.onclose = () => this.handleSocketDown(socket, code);
    socket.onerror = () => this.handleSocketDown(socket, code);
  }

  private handleSocketDown(socket: WebSocket, code: string): void {
    if (this.socket !== socket) {
      return; // a newer socket already superseded this one
    }
    if (this.intentionalClose || this.fatalError() || this.endedBy()) {
      return; // spec Stage 6 doesn't reconnect after an intentional leave or a room-gone/full rejection
    }
    this.connectionState.set('reconnecting');
    const delay = RECONNECT_DELAYS_MS[Math.min(this.reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];
    this.reconnectAttempt++;
    this.reconnectTimer = setTimeout(() => {
      if (!this.intentionalClose) {
        this.openSocket(code);
      }
    }, delay);
  }

  sendText(text: string): void {
    const trimmed = text.trim();
    const me = this.you();
    if (!trimmed || !me || !this.isOpen()) {
      return;
    }
    this.appendItem({
      id: randomId(),
      kind: 'text',
      outgoing: true,
      senderName: me.name,
      at: Date.now(),
      text: trimmed
    });
    this.send({ type: 'text', text: trimmed });
  }

  /**
   * Checks the file against the limits straight away, then queues it.
   * Several files (a multi-select, a drop, a paste) go one after another.
   */
  sendFile(file: File): Promise<void> {
    if (!this.you() || !this.isOpen()) {
      return this.uploadQueue;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.toast.show(`${file.name} is ${Math.ceil(file.size / (1024 * 1024))} MB. The limit is 25 MB.`, true);
      return this.uploadQueue;
    }
    const ext = fileExtension(file.name);
    if (BLOCKED_EXTENSIONS.includes(ext)) {
      this.toast.show(`Can't send .${ext} files. Executables are blocked.`, true);
      return this.uploadQueue;
    }
    const generation = this.generation;
    this.uploadQueue = this.uploadQueue.then(() => (generation === this.generation ? this.upload(file) : undefined));
    return this.uploadQueue;
  }

  /** Ends the room for everyone. The server checks the host token and closes every connection. */
  endRoom(hostToken: string): void {
    this.send({ type: 'end-room', hostToken });
  }

  private async upload(file: File): Promise<void> {
    const me = this.you();
    if (!me || !this.isOpen()) {
      return;
    }
    const transferId = randomId();
    const isImage = file.type.startsWith('image/');
    this.appendItem({
      id: transferId,
      kind: isImage ? 'image' : 'file',
      outgoing: true,
      senderName: me.name,
      at: Date.now(),
      name: file.name,
      mime: file.type,
      size: file.size,
      url: URL.createObjectURL(file)
    });

    this.uploading.set(true);
    try {
      this.send({ type: 'file-start', transferId, name: file.name, mime: file.type, size: file.size });
      let sequence = 0;
      for (let offset = 0; offset < file.size; offset += CHUNK_SIZE_BYTES) {
        const slice = file.slice(offset, offset + CHUNK_SIZE_BYTES);
        const buffer = new Uint8Array(await slice.arrayBuffer());
        this.socket?.send(encodeChunkFrame(transferId, sequence, buffer));
        sequence++;
      }
      this.send({ type: 'file-end', transferId });
    } finally {
      this.uploading.set(false);
    }
  }

  private isOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  private send(message: unknown): void {
    if (this.isOpen()) {
      this.socket!.send(JSON.stringify(message));
    }
  }

  private handleMessage(event: MessageEvent<string | ArrayBuffer>): void {
    if (typeof event.data === 'string') {
      this.handleControlMessage(JSON.parse(event.data) as ServerMessage);
    } else {
      this.handleChunk(event.data);
    }
  }

  private handleControlMessage(message: ServerMessage): void {
    switch (message.type) {
      case 'welcome':
        this.handleWelcome(message);
        break;
      case 'joined':
        this.handleJoined(message);
        break;
      case 'left':
        this.handleLeft(message);
        break;
      case 'text':
        this.handleIncomingText(message);
        break;
      case 'file-start':
        this.handleFileStart(message);
        break;
      case 'file-end':
        this.handleFileEnd(message);
        break;
      case 'ping':
        this.send({ type: 'pong' });
        break;
      case 'room-ended':
        this.handleRoomEnded(message);
        break;
      case 'error':
        this.handleError(message);
        break;
    }
  }

  private handleWelcome(message: WelcomeMessage): void {
    this.reconnectAttempt = 0;
    this.you.set(message.you);
    this.participants.set(message.participants);
    message.participants.forEach((p) => this.assignSlot(p.id));
    this.expiresAt.set(Date.parse(message.expiresAt));
    this.connectionState.set('open');
  }

  private handleJoined(message: JoinedMessage): void {
    this.participants.update((current) => [...current, { id: message.id, name: message.name }]);
    this.assignSlot(message.id);
  }

  private handleRoomEnded(message: RoomEndedMessage): void {
    this.intentionalClose = true; // the server closes us next; don't reconnect
    this.endedBy.set(message.by);
    this.connectionState.set('closed');
  }

  /** Gives a participant the lowest free colour slot, wrapping once all are taken. */
  private assignSlot(id: string): void {
    const slots = this.slots();
    if (slots.has(id)) return;
    const taken = new Set(slots.values());
    let slot = 0;
    while (slot < PARTICIPANT_COLOURS && taken.has(slot)) slot++;
    if (slot === PARTICIPANT_COLOURS) slot = slots.size % PARTICIPANT_COLOURS;
    this.slots.set(new Map(slots).set(id, slot));
  }

  private handleLeft(message: LeftMessage): void {
    this.participants.update((current) => current.filter((p) => p.id !== message.id));
  }

  private handleIncomingText(message: IncomingTextMessage): void {
    this.appendItem({
      id: message.id,
      kind: 'text',
      outgoing: false,
      senderId: message.from.id,
      senderName: message.from.name,
      at: Date.now(),
      text: message.text
    });
  }

  private handleFileStart(message: FileStartMessage): void {
    this.incomingTransfers.set(message.transferId, { from: message.from, name: message.name, mime: message.mime, parts: [] });
  }

  private handleChunk(buffer: ArrayBuffer): void {
    const frame = decodeChunkFrame(buffer);
    this.incomingTransfers.get(frame.transferId)?.parts.push(frame.payload);
  }

  private handleFileEnd(message: FileEndMessage): void {
    const transfer = this.incomingTransfers.get(message.transferId);
    this.incomingTransfers.delete(message.transferId);
    if (!transfer) {
      return;
    }
    const blob = new Blob(transfer.parts as BlobPart[], { type: transfer.mime });
    this.appendItem({
      id: message.transferId,
      kind: transfer.mime.startsWith('image/') ? 'image' : 'file',
      outgoing: false,
      senderId: transfer.from.id,
      senderName: transfer.from.name,
      at: Date.now(),
      name: transfer.name,
      mime: transfer.mime,
      size: blob.size,
      url: URL.createObjectURL(blob)
    });
  }

  private handleError(message: ErrorMessage): void {
    if (message.code === 'ROOM_NOT_FOUND' || message.code === 'ROOM_FULL') {
      this.fatalCode.set(message.code);
      this.fatalError.set(message.message);
      this.connectionState.set('closed');
      return;
    }
    this.toast.show(message.message, true);
  }

  private appendItem(item: FeedItem): void {
    this.items.update((current) => [...current, item]);
  }

  private reset(): void {
    this.generation++;
    clearTimeout(this.reconnectTimer);
    this.socket?.close();
    this.socket = null;
    this.code.set(null);
    this.you.set(null);
    this.participants.set([]);
    for (const item of this.items()) {
      if (item.url) {
        URL.revokeObjectURL(item.url);
      }
    }
    this.items.set([]);
    this.expiresAt.set(null);
    this.fatalError.set(null);
    this.fatalCode.set(null);
    this.endedBy.set(null);
    this.slots.set(new Map());
    this.uploading.set(false);
    this.incomingTransfers.clear();
  }
}
