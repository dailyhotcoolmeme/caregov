import { execFileSync } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../mobile/package.json', import.meta.url));
const { XMLParser, XMLBuilder } = require('fast-xml-parser');
export const packageName = 'com.ourmine.caregov.demo';
const adbPath = `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`;
export const adb = (args, options = {}) => execFileSync(adbPath, ['-s', 'emulator-5580', ...args], { encoding: 'utf8', ...options });
export const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
export function nodes() {
  adb(['shell', 'uiautomator', 'dump', '/sdcard/caregov-ui.xml']);
  const parsed = new XMLParser({ ignoreAttributes: false, attributeNamePrefix: '' }).parse(adb(['shell', 'cat', '/sdcard/caregov-ui.xml']));
  const rows = [];
  const walk = value => { if (!value || typeof value !== 'object') return; if (value.bounds) rows.push(value); for (const child of Object.values(value)) if (Array.isArray(child)) child.forEach(walk); else if (child && typeof child === 'object') walk(child); };
  walk(parsed); return rows;
}
const matches = (row, label) => row['content-desc'] === label || row.text === label;
export async function tap(label, { scroll = false, occurrence = 0, allowText = false } = {}) {
  for (let attempt = 0; attempt < (scroll ? 14 : 3); attempt++) {
    const matching = nodes().filter(r => {
      if (!matches(r, label)) return false;
      const b = r.bounds.match(/\d+/g)?.map(Number);
      return b && b[2] > b[0] && b[3] - b[1] >= 50;
    });
    const actionable = matching.filter(r => r.clickable === 'true' || r.class === 'android.widget.EditText');
    const row = (allowText ? matching : actionable)[occurrence];
    if (row) {
      const coordinates = row.bounds.match(/\d+/g).map(Number);
      adb(['shell', 'input', 'tap', String((coordinates[0] + coordinates[2]) / 2), String((coordinates[1] + coordinates[3]) / 2)]);
      await pause(600); return;
    }
    if (scroll) adb(['shell', 'input', 'swipe', '350', '1000', '350', '400', '300']);
    await pause(500);
  }
  throw new Error(`Control not found: ${label}`);
}
export async function input(label, value, clear = 0) {
  if (!/^[\x20-\x7E]*$/.test(value)) throw new Error('ADB text input is ASCII-only');
  await tap(label, { scroll: true });
  if (clear) adb(['shell', 'input', 'keyevent', '123', ...Array(clear).fill('67')]);
  adb(['shell', 'input', 'text', value.replaceAll(' ', '%s')]);
  const ime = adb(['shell', 'dumpsys', 'input_method']);
  if (/mInputShown=true|mIsInputViewShown=true|isVisibleBound=true/.test(ime)) adb(['shell', 'input', 'keyevent', '4']);
  await pause(500);
}
export function assertText(text) {
  if (!nodes().some(r => String(r.text || r['content-desc'] || '').includes(text))) throw new Error(`Text missing: ${text}`);
}
export function screenshot(name) {
  mkdirSync('output/mobile-qa', { recursive: true });
  writeFileSync(`output/mobile-qa/${name}.png`, adb(['exec-out', 'screencap', '-p'], { encoding: 'buffer' }));
}
export async function switchAccount(label) {
  await tap('사용자 선택'); await tap('사용자 선택');
  await tap(label, { allowText: true }); await input('비밀번호', '260401'); await tap('사용자 변경', { scroll: true });
}
export function seedLegacy(records, account = 'manager2') {
  adb(['shell', 'am', 'force-stop', packageName]);
  const xml = new XMLBuilder({ ignoreAttributes: false, attributeNamePrefix: '@_', format: true }).build({ map: {
    string: [{ '@_name': 'bookings', '#text': JSON.stringify(records) }, { '@_name': 'account', '#text': account }],
    boolean: { '@_name': 'signed_out', '@_value': 'false' },
  } });
  adb(['shell', 'run-as', packageName, 'sh', '-c', "'mkdir -p shared_prefs && cat > shared_prefs/service_records.xml'"], { input: xml });
}
