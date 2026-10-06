import { execFileSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cloudflareEnv, bucket, publicBase } from './cloudflare-env.mjs';
import { assertNativeRuntime } from './native-runtime.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const mobile = join(root, 'mobile');
assertNativeRuntime();
const config = JSON.parse(execFileSync('npx', ['expo', 'config', '--json'], { cwd: mobile, encoding: 'utf8' }));
if (config.android.package !== 'com.ourmine.caregov.demo' || config.updates.url !== `${publicBase}/manifest` || !/^[\w.-]+$/.test(config.runtimeVersion)) throw new Error('Unexpected bundle target');
const native = readFileSync(join(mobile, 'android/app/src/main/AndroidManifest.xml'), 'utf8');
const runtimeResource = readFileSync(join(mobile, 'android/app/src/main/res/values/strings.xml'), 'utf8');
if (!native.includes(`${publicBase}/manifest`) || !runtimeResource.includes(`>${config.runtimeVersion}</string>`)) throw new Error('Prebuild runtime does not match bundle runtime');
execFileSync('npx', ['expo', 'export', '--platform', 'android'], { cwd: mobile, stdio: 'inherit' });
const dist = join(mobile, 'dist');
const files = JSON.parse(readFileSync(join(dist, 'metadata.json'), 'utf8')).fileMetadata.android;
const hash = bytes => createHash('sha256').update(bytes).digest('base64url');
const env = cloudflareEnv();
const contentType = ext => ({ ttf: 'font/ttf', otf: 'font/otf', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', js: 'application/javascript', json: 'application/json', webp: 'image/webp' })[ext] || 'application/octet-stream';
async function uploadVerified(file, name, type) {
  const bytes = readFileSync(file), expected = hash(bytes), url = `${publicBase}/files/${name}`;
  const existing = await fetch(url, { signal: AbortSignal.timeout(30000) });
  if (existing.ok) {
    const received = Buffer.from(await existing.arrayBuffer());
    if (hash(received) !== expected || received.length !== bytes.length) throw new Error('Immutable object mismatch');
  } else {
    if (existing.status !== 404) throw new Error(`Public asset unavailable: ${existing.status}`);
    put(`ota/files/${name}`, file, type);
    const response = await fetch(url, { signal: AbortSignal.timeout(60000) });
    if (!response.ok) throw new Error('Public asset unavailable after upload');
    const received = Buffer.from(await response.arrayBuffer());
    if (hash(received) !== expected || received.length !== bytes.length) throw new Error('Public asset integrity mismatch');
  }
  return { hash: expected, url, contentType: type };
}
function put(key, file, type) {
  for (let attempt = 0; ; attempt++) {
    try { execFileSync('npx', ['--yes', 'wrangler', 'r2', 'object', 'put', `${bucket}/${key}`, `--file=${file}`, `--content-type=${type}`, '--remote', '--config', 'cloudflare/ota/wrangler.toml'], { cwd: root, env, stdio: 'pipe' }); return; }
    catch { if (attempt === 3) throw new Error(`R2 upload failed: ${key}`); Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000 * 2 ** attempt); }
  }
}
const bundle = join(dist, files.bundle), bundleHash = hash(readFileSync(bundle));
const launchAsset = { ...await uploadVerified(bundle, `bundle-${bundleHash}.js`, 'application/javascript'), key: bundleHash };
const assets = [];
for (const asset of files.assets) {
  const file = join(dist, asset.path), assetHash = hash(readFileSync(file));
  assets.push({ ...await uploadVerified(file, `asset-${assetHash}.${asset.ext}`, contentType(asset.ext)), key: basename(asset.path), fileExtension: `.${asset.ext}` });
}
const manifest = { id: randomUUID(), createdAt: new Date().toISOString(), runtimeVersion: config.runtimeVersion, launchAsset, assets,
  metadata: {}, extra: { expoClient: config } };
const directory = join(root, 'output/ota'); mkdirSync(directory, { recursive: true });
const path = join(directory, `${manifest.id}.json`); writeFileSync(path, JSON.stringify(manifest, null, 2) + '\n');
// Commit the pointer only after every public byte has passed integrity validation.
put(`ota/pointer/${config.runtimeVersion}/android.json`, path, 'application/json');
const live = await fetch(`${publicBase}/manifest`, { headers: { 'expo-platform': 'android', 'expo-runtime-version': config.runtimeVersion }, cache: 'no-store' });
if (!live.ok || !(await live.text()).includes(manifest.id)) throw new Error('Published manifest mismatch');
writeFileSync(join(root, 'updates/bundle-android.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(`Bundle uploaded and verified: ${manifest.id}`);
console.log(`Runtime: ${config.runtimeVersion}; assets: ${assets.length}`);
