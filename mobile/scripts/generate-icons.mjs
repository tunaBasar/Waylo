// Generates Waylo app icons from an inline SVG (no logo file exists in the repo; the web uses the
// text "Waylo" with the primary color #004ac6). Uses sharp from frontend/node_modules.
// Run: node mobile/scripts/generate-icons.mjs
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(path.join(here, '../../frontend/package.json'));
const sharp = require('sharp');
const out = (f) => path.join(here, '../assets/images', f);
const PRIMARY = '#004ac6';

const glyph = (color, size = 1024, scale = 1) =>
  `<text x="50%" y="50%" dy="0.35em" text-anchor="middle" font-family="DejaVu Sans, Arial, sans-serif" font-weight="700" font-size="${560 * scale * size / 1024}" fill="${color}">W</text>`;
const svg = (body, size = 1024, bg) =>
  Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">${bg ? `<rect width="100%" height="100%" fill="${bg}"/>` : ''}${body}</svg>`);

const jobs = [
  ['waylo-icon.png', svg(glyph('#fff'), 1024, PRIMARY)],
  ['waylo-adaptive-foreground.png', svg(glyph('#fff', 1024, 0.7))],
  ['waylo-adaptive-monochrome.png', svg(glyph('#000', 1024, 0.7))],
  ['waylo-splash.png', svg(glyph(PRIMARY))],
  ['waylo-favicon.png', svg(glyph('#fff', 48), 48, PRIMARY)],
];
for (const [name, buf] of jobs) {
  await sharp(buf).png().toFile(out(name));
  console.log('wrote', name);
}
