export const ROOM_CODE_LENGTH = 6;

/**
 * Pulls a room code out of whatever was typed or pasted: a bare code, a code
 * with stray characters, or a full room link (https://quickdot.amartian.ca/r/X7K2P9).
 */
export function extractCode(raw: string): string {
  const fromLink = raw.match(/\/r\/([A-Za-z0-9]{6})/);
  if (fromLink) {
    return fromLink[1].toUpperCase();
  }
  const clean = raw.toUpperCase().replace(/[^A-Z0-9]/g, '');
  // Something link-shaped without /r/: the code is the tail.
  return clean.length > ROOM_CODE_LENGTH && /[/.:]/.test(raw)
    ? clean.slice(-ROOM_CODE_LENGTH)
    : clean.slice(0, ROOM_CODE_LENGTH);
}
