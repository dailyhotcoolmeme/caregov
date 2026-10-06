import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../cloudflare/ota/src/index.ts';

const keys = [];
const env = { BUCKET: { async get(key) {
  keys.push(key);
  return { size: 2, body: '{}', writeHttpMetadata(headers) { headers.set('Content-Type', 'text/plain'); } };
} } };
test('manifest is fetched directly from R2 without caching', async () => {
  const response = await worker.fetch(new Request('https://example.com/android.json'), env);
  assert.equal(keys.at(-1), 'ota/pointer/android.json');
  assert.equal(response.headers.get('Cache-Control'), 'no-store, max-age=0');
  assert.equal(response.headers.get('Content-Type'), 'application/json');
});
test('APK response is downloadable and immutable', async () => {
  const response = await worker.fetch(new Request('https://example.com/apk/caregov-0.3.1-abc.apk'), env);
  assert.equal(response.headers.get('Content-Type'), 'application/vnd.android.package-archive');
  assert.match(response.headers.get('Content-Disposition'), /attachment/);
  assert.match(response.headers.get('Cache-Control'), /immutable/);
});
test('HEAD has no body; unrelated paths and writes are denied', async () => {
  const head = await worker.fetch(new Request('https://example.com/android.json', { method: 'HEAD' }), env);
  assert.equal(await head.text(), '');
  assert.equal((await worker.fetch(new Request('https://example.com/private/key'), env)).status, 404);
  assert.equal((await worker.fetch(new Request('https://example.com/android.json', { method: 'POST' }), env)).status, 405);
});
