// Copies the repo's legal/*.md into public/legal/ so the /privacy and /terms
// pages serve the same Markdown that lives in version control (the Angular
// builder can't copy assets from outside the frontend/ workspace).
// Runs automatically before `npm run build` and `npm start`.
import { copyFileSync, mkdirSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, '..', 'legal');
const dest = join(root, 'public', 'legal');

mkdirSync(dest, { recursive: true });
for (const file of readdirSync(src).filter((f) => f.endsWith('.md'))) {
  copyFileSync(join(src, file), join(dest, file));
  console.log(`legal: ${file} -> public/legal/`);
}
