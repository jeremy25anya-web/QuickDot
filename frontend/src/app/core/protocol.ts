// Wire types mirroring backend/src/main/java/ca/amartian/quickdot/ws/protocol/*
// (spec §5). Keep field names identical to the Jackson-serialized JSON.

export interface ParticipantView {
  id: string;
  name: string;
}

export interface WelcomeMessage {
  type: 'welcome';
  you: ParticipantView;
  participants: ParticipantView[];
  expiresAt: string; // ISO instant
}

export interface JoinedMessage {
  type: 'joined';
  id: string;
  name: string;
}

export interface LeftMessage {
  type: 'left';
  id: string;
}

export interface IncomingTextMessage {
  type: 'text';
  from: ParticipantView;
  id: string;
  text: string;
}

export interface FileStartMessage {
  type: 'file-start';
  from: ParticipantView;
  transferId: string;
  name: string;
  mime: string;
  size: number;
}

export interface FileEndMessage {
  type: 'file-end';
  from: ParticipantView;
  transferId: string;
}

export interface PingMessage {
  type: 'ping';
}

export interface RoomEndedMessage {
  type: 'room-ended';
  by: string;
}

export interface ErrorMessage {
  type: 'error';
  code: string;
  message: string;
}

export type ServerMessage =
  | WelcomeMessage
  | JoinedMessage
  | LeftMessage
  | IncomingTextMessage
  | FileStartMessage
  | FileEndMessage
  | PingMessage
  | RoomEndedMessage
  | ErrorMessage;
