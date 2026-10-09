export interface FeedItem {
  id: string;
  kind: 'text' | 'image' | 'file';
  outgoing: boolean;
  /** Participant id of the sender; absent for your own items. */
  senderId?: string;
  senderName: string;
  /** When it was sent or arrived, in ms since the epoch (client clock). */
  at: number;
  text?: string;
  name?: string;
  mime?: string;
  size?: number;
  url?: string;
}
