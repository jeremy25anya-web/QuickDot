import { FeedItem } from './feed-item';

// Published in legal/TERMS-OF-SERVICE.md §6 and the Privacy Policy.
export const ABUSE_EMAIL = 'abuse@amartian.ca';

/**
 * A mailto: link that drafts an abuse report in the reporter's own mail app.
 * It carries only what's needed to find the room in the server's metadata
 * audit log (room code, time, and for files the type and exact size, which
 * match the `file_relayed` entry). It never carries message text, file names
 * or file contents. The reporter describes what they saw in their own words.
 */
export function reportMailto(item: FeedItem, roomCode: string, now: Date = new Date()): string {
  const lines = [
    'QuickDot abuse report',
    '',
    `Room code: ${roomCode}`,
    `Reported at: ${now.toISOString()}`,
    `Item type: ${item.kind}`
  ];
  if (item.kind !== 'text') {
    if (item.mime) lines.push(`File type: ${item.mime}`);
    if (item.size != null) lines.push(`File size: ${item.size} bytes`);
  }
  lines.push(
    '',
    "QuickDot doesn't store anything that was shared, so please describe what you saw:",
    '',
    '',
    '',
    'If this involves child sexual abuse material, please also report it to Cybertip.ca.'
  );
  const subject = `QuickDot report: room ${roomCode}`;
  return `mailto:${ABUSE_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(lines.join('\r\n'))}`;
}
