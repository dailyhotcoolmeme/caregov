import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../cloudflare/ota/src/index.ts';

const keys = [];
const env = { BUCKET: { async get(key) {
  keys.push(key);
  return { size: 2, body: '{}', async text() { return '{}'; }, writeHttpMetadata(headers) { headers.set('Content-Type', 'text/plain'); } };
} } };
test('manifest is fetched directly from R2 without caching', async () => {
  const response = await worker.fetch(new Request('https://example.com/android.json'), env);
  assert.equal(keys.at(-1), 'ota/pointer/android.json');
  assert.equal(response.headers.get('Cache-Control'), 'no-store, max-age=0');
  assert.equal(response.headers.get('Content-Type'), 'application/json');
});
test('Expo manifest is runtime-isolated multipart, not an APK response', async () => {
  const response = await worker.fetch(new Request('https://example.com/manifest', { headers: { 'expo-platform': 'android', 'expo-runtime-version': '1.0.0' } }), env);
  assert.equal(keys.at(-1), 'ota/pointer/1.0.0/android.json');
  assert.match(response.headers.get('Content-Type'), /multipart\/mixed/);
  assert.equal(response.headers.get('expo-protocol-version'), '1');
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.match(await response.text(), /name="manifest"/);
});
test('invalid runtime/platform and arbitrary bundle paths fail closed', async () => {
  for (const runtime of ['', '../android', 'a/b', 'a'.repeat(81)]) assert.equal((await worker.fetch(new Request('https://example.com/manifest', { headers: { 'expo-platform': 'android', 'expo-runtime-version': runtime } }), env)).status, 400);
  assert.equal((await worker.fetch(new Request('https://example.com/manifest', { headers: { 'expo-platform': 'ios', 'expo-runtime-version': '1.0.0' } }), env)).status, 400);
  assert.equal((await worker.fetch(new Request('https://example.com/files/secret'), env)).status, 404);
});
test('bundle assets are immutable and cannot reach another bucket prefix', async () => {
  const name = `bundle-${'a'.repeat(43)}.js`;
  const response = await worker.fetch(new Request(`https://example.com/files/${name}`), env);
  assert.equal(keys.at(-1), `ota/files/${name}`); assert.match(response.headers.get('Cache-Control'), /immutable/);
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
