import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
const root = fileURLToPath(new URL('..', import.meta.url));
const require = createRequire(join(root, 'mobile/package.json'));
const config = require(join(root, 'mobile/app.config.js'));
const recordPath = join(root, 'updates/native-runtime.json');
export function nativeSignature() {
  const hash = createHash('sha256');
  const lock = JSON.parse(readFileSync(join(root, 'mobile/package-lock.json'), 'utf8'));
  // Conservatively pin installed runtime dependencies, not application JS or QA tools.
  const packages = Object.entries(lock.packages).filter(([path, value]) => path && !value.dev).map(([path, value]) => [path, value.version]);
  hash.update(JSON.stringify({ packages, android: config.android, plugins: config.plugins, updates: config.updates, runtime: config.runtimeVersion }));
  const include = path => hash.update(readFileSync(path));
  include(join(root, 'mobile/plugins/withCaregov.js'));
  include(join(root, 'app/src/main/res/drawable/ic_launcher.xml'));
  const walk = dir => { for (const entry of readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    if (['build', '.gradle', '.cxx'].includes(entry.name)) continue;
    const path = join(dir, entry.name); if (entry.isDirectory()) walk(path); else { hash.update(entry.name); include(path); }
  } };
  walk(join(root, 'mobile/modules'));
  return hash.digest('hex');
}
export function assertNativeRuntime() {
  const record = JSON.parse(readFileSync(recordPath, 'utf8'));
  if (record.runtimeVersion !== config.runtimeVersion || record.nativeSignature !== nativeSignature()) throw new Error('Native runtime changed. Build/verify a new APK and bump runtime before publishing bundles.');
}
if (process.argv.includes('--record')) {
  if (existsSync(recordPath)) {
    const previous = JSON.parse(readFileSync(recordPath, 'utf8'));
    if (previous.runtimeVersion === config.runtimeVersion && previous.nativeSignature !== nativeSignature()) throw new Error('Changed native runtime must receive a new runtimeVersion');
  }
  const apk = join(root, 'output/apk/caregov.apk');
  const tools = `${process.env.HOME}/Library/Android/sdk/build-tools/36.0.0`;
  const certificate = execFileSync(join(tools, 'apksigner'), ['verify', '--print-certs', apk], { encoding: 'utf8' }).match(/certificate SHA-256 digest: (\w+)/)?.[1];
  if (certificate !== '3ab06264882900cba1d7823d568a89f0579d01ced567614bd91bb36aff1ec701') throw new Error('Signing continuity failed');
  const identity = execFileSync(join(tools, 'aapt'), ['dump', 'badging', apk], { encoding: 'utf8' });
  if (!identity.includes(`name='${config.android.package}' versionCode='${config.android.versionCode}' versionName='${config.version}'`)) throw new Error('APK identity mismatch');
  writeFileSync(recordPath, JSON.stringify({ runtimeVersion: config.runtimeVersion, nativeSignature: nativeSignature(), apkSha256: createHash('sha256').update(readFileSync(apk)).digest('hex'), versionCode: config.android.versionCode, versionName: config.version, certificate }, null, 2) + '\n');
  console.log('Native runtime and signing record verified');
}
