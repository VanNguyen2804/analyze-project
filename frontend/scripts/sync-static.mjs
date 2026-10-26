import { cpSync, existsSync, readdirSync, realpathSync, unlinkSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = join(frontend, 'dist', 'analyzeproject');
const target = resolve(frontend, '../backend/src/main/resources/static');

if (!existsSync(join(source, 'index.html'))) {
  throw new Error('Build Angular before synchronizing static assets.');
}
if (realpathSync(target) !== target) {
  throw new Error('Refusing to synchronize a redirected static directory.');
}

// Only remove obsolete generated bundles; preserve any other backend resources.
const generatedBundle = /^(?:main|polyfills|runtime|styles|chunk)[.-][\w-]+\.(?:js|css)(?:\.map)?$/;
const generatedMetadata = new Set(['prerendered-routes.json', '3rdpartylicenses.txt']);
for (const entry of readdirSync(target, { withFileTypes: true })) {
  const obsoleteMetadata =
    generatedMetadata.has(entry.name) && !existsSync(join(source, entry.name));
  if (entry.isFile() && (generatedBundle.test(entry.name) || obsoleteMetadata)) {
    unlinkSync(join(target, entry.name));
  }
}
cpSync(source, target, { recursive: true, force: true });
console.log('Synchronized Angular assets into backend static resources.');
