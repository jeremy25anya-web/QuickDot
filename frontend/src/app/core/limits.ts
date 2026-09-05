// Client-side mirror of spec §6 limits (backend/src/main/resources/application.properties).
// These are UX pre-checks only — the server re-checks everything (the client is never trusted).

export const MAX_FILE_BYTES = 25 * 1024 * 1024;
export const CHUNK_SIZE_BYTES = 256 * 1024;

export const BLOCKED_EXTENSIONS = [
  'exe', 'bat', 'cmd', 'com', 'msi', 'scr', 'ps1', 'sh', 'bash', 'vbs', 'vbe',
  'jar', 'apk', 'deb', 'dmg', 'pkg', 'app', 'gadget', 'cpl', 'msc', 'wsf', 'hta', 'reg'
];

export function fileExtension(name: string): string {
  const parts = name.split('.');
  return parts.length > 1 ? (parts.pop() ?? '').toLowerCase() : '';
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
