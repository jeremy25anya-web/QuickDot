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
  ServerMessage,
  WelcomeMessage
} from './protocol';
import { ToastService } from './toast.service';

export type ConnectionState = 'connecting' | 'open' | 'closed';

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
  readonly uploading = signal(false);

  readonly connected = computed(() => this.participants().length > 0);

  private socket: WebSocket | null = null;
  private readonly incomingTransfers = new Map<string, IncomingTransfer>();

  connect(code: string): void {
    this.reset();
    this.code.set(code);
    this.connectionState.set('connecting');

    const socket = new WebSocket(`${environment.wsBaseUrl}/ws/${code}`);
    socket.binaryType = 'arraybuffer';
    this.socket = socket;

    socket.onmessage = (event) => this.handleMessage(event);
    socket.onclose = () => {
      if (this.socket === socket) {
        this.connectionState.set('closed');
      }
    };
    socket.onerror = () => {
      if (this.socket === socket) {
        this.connectionState.set('closed');
      }
    };
  }

  leave(): void {
    this.reset();
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
      text: trimmed
    });
    this.send({ type: 'text', text: trimmed });
  }

  async sendFile(file: File): Promise<void> {
    const me = this.you();
    if (!me || !this.isOpen()) {
      return;
    }
    if (this.uploading()) {
      this.toast.show('Finish your current upload first.', true);
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.toast.show(`That's ${Math.ceil(file.size / (1024 * 1024))} MB. The limit is 25 MB.`, true);
      return;
    }
    const ext = fileExtension(file.name);
    if (BLOCKED_EXTENSIONS.includes(ext)) {
      this.toast.show(`Can't send .${ext} files — executables are blocked.`, true);
      return;
    }

    const transferId = randomId();
    const isImage = file.type.startsWith('image/');
    this.appendItem({
      id: transferId,
      kind: isImage ? 'image' : 'file',
      outgoing: true,
      senderName: me.name,
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
      case 'error':
        this.handleError(message);
        break;
    }
  }

  private handleWelcome(message: WelcomeMessage): void {
    this.you.set(message.you);
    this.participants.set(message.participants);
    this.expiresAt.set(Date.parse(message.expiresAt));
    this.connectionState.set('open');
  }

  private handleJoined(message: JoinedMessage): void {
    this.participants.update((current) => [...current, { id: message.id, name: message.name }]);
  }

  private handleLeft(message: LeftMessage): void {
    this.participants.update((current) => current.filter((p) => p.id !== message.id));
  }

  private handleIncomingText(message: IncomingTextMessage): void {
    this.appendItem({
      id: message.id,
      kind: 'text',
      outgoing: false,
      senderName: message.from.name,
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
      senderName: transfer.from.name,
      name: transfer.name,
      mime: transfer.mime,
      size: blob.size,
      url: URL.createObjectURL(blob)
    });
  }

  private handleError(message: ErrorMessage): void {
    if (message.code === 'ROOM_NOT_FOUND' || message.code === 'ROOM_FULL') {
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
    this.uploading.set(false);
    this.incomingTransfers.clear();
  }
}
