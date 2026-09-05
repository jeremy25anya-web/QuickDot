export interface FeedItem {
  id: string;
  kind: 'text' | 'image' | 'file';
  outgoing: boolean;
  senderName: string;
  text?: string;
  name?: string;
  mime?: string;
  size?: number;
  url?: string;
}
