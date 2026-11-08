import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

export function renderConfig(apiUrl) {
  if (!apiUrl) throw new Error('Set API_BASE_URL to the public HTTPS backend origin.');
  const url = new URL(apiUrl);
  if (
    url.protocol !== 'https:' ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== '/'
  ) {
    throw new Error(
      'API_BASE_URL must be an HTTPS origin without credentials, path, query or fragment.',
    );
  }
  return `window.analyzeConfig = ${JSON.stringify({ apiUrl: url.origin })};\n`;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  writeFileSync(
    new URL('../dist/analyzeproject/assets/app-config.js', import.meta.url),
    renderConfig(process.env.API_BASE_URL),
  );
  console.log('Generated public frontend API configuration.');
}
