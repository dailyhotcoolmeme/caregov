import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';
import { bucket, cloudflareEnv, publicBase } from './cloudflare-env.mjs';

const apk = 'output/apk/caregov.apk';
const tools = join(process.env.ANDROID_HOME || join(homedir(), 'Library/Android/sdk'), 'build-tools/36.0.0');
execFileSync(join(tools, 'apksigner'), ['verify', apk]);
const badging = execFileSync(join(tools, 'aapt'), ['dump', 'badging', apk], { encoding: 'utf8' });
const match = badging.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
if (!match || match[1] !== 'com.ourmine.caregov.demo') throw new Error('Unexpected APK identity');
const bytes = readFileSync(apk);
const sha256 = createHash('sha256').update(bytes).digest('hex');
const versionCode = Number(match[2]);
const versionName = match[3];
if (!/^[0-9A-Za-z._-]+$/.test(versionName)) throw new Error('Invalid version');
const key = `apk/caregov-${versionName}-${sha256.slice(0, 12)}.apk`;
const apkUrl = `${publicBase}/${key}`;
const env = cloudflareEnv();
function put(key, file, type) {
  for (let attempt = 0; ; attempt++) {
    try {
      execFileSync('npx', ['--yes', 'wrangler', 'r2', 'object', 'put', `${bucket}/${key}`, `--file=${file}`,
        `--content-type=${type}`, '--remote', '--config', 'cloudflare/ota/wrangler.toml'], { env, stdio: 'pipe' });
      return;
    } catch {
      if (attempt === 3) throw new Error(`R2 upload failed: ${key}`);
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000 * (2 ** attempt));
    }
  }
}
put(key, apk, 'application/vnd.android.package-archive');
const response = await fetch(apkUrl);
if (!response.ok || response.headers.get('content-type') !== 'application/vnd.android.package-archive') throw new Error('Public APK unavailable');
const received = Buffer.from(await response.arrayBuffer());
if (received.length !== bytes.length || createHash('sha256').update(received).digest('hex') !== sha256) throw new Error('Public APK integrity mismatch');
const manifest = { schemaVersion: 1, applicationId: match[1], versionCode, versionName, apkUrl,
  sha256, sizeBytes: bytes.length, releaseNotes: process.env.RELEASE_NOTES || '앱을 업데이트했습니다.' };
const directory = mkdtempSync(join(tmpdir(), 'caregov-update-'));
try {
  const file = join(directory, 'android.json');
  writeFileSync(file, JSON.stringify(manifest, null, 2) + '\n');
  put('ota/pointer/android.json', file, 'application/json');
  const live = await fetch(`${publicBase}/android.json`, { cache: 'no-store' });
  if (!live.ok) throw new Error('Manifest unavailable');
  const value = await live.json();
  if (value.sha256 !== sha256 || value.versionCode !== versionCode || value.apkUrl !== apkUrl) throw new Error('Manifest mismatch');
  writeFileSync('updates/r2-android.json', JSON.stringify(manifest, null, 2) + '\n');
} finally { rmSync(directory, { recursive: true }); }
console.log(`Uploaded and verified: ${apkUrl}`);
console.log(`Manifest: ${publicBase}/android.json`);
