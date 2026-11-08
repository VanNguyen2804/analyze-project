import { test } from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { renderConfig } from './render-config.mjs';

test('generated public config executes and normalizes a trailing slash', () => {
  const context = { window: {} };
  vm.runInNewContext(renderConfig('https://analyze-project.onrender.com/'), context);
  assert.equal(context.window.analyzeConfig.apiUrl, 'https://analyze-project.onrender.com');
});

test('rejects missing origins, insecure URLs and URLs containing credentials or paths', () => {
  for (const value of [
    undefined,
    '',
    'http://example.com',
    'https://user:secret@example.com',
    'https://example.com/api',
    'https://example.com?token=x',
    'https://example.com/#fragment',
  ]) {
    assert.throws(() => renderConfig(value));
  }
});
