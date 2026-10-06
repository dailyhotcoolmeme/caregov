import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { tap, input, switchAccount, pause, nodes, assertText, screenshot, adb } from './mobile-ui.mjs';

const checks = [];
const checkpoint = name => { checks.push(name); console.log(`PASS ${name}`); };
const manifest = JSON.parse(readFileSync('updates/bundle-android.json', 'utf8'));
const uiVersion = readFileSync('mobile/App.js', 'utf8').match(/UI_RELEASE = '([^']+)'/)[1];
const packageInfo = () => adb(['shell', 'dumpsys', 'package', 'com.ourmine.caregov.demo']);
const before = packageInfo().match(/lastUpdateTime=(.*)/)?.[1];
const openBooking = async status => { await tap(`김영희 OTA Draft Hospital ${status}`, { scroll: true }); };
const confirm = async () => { await tap('확인', { scroll: true }); await pause(700); };

// Continue the request held open across the real R2 publication/foreground check.
if (!process.argv.includes('--from-review')) await tap('다음', { scroll: true });
await tap('신청 정보 및 공유 범위에 동의', { scroll: true });
await tap('동행 신청', { scroll: true });
await pause(5000);
await tap('내 정보');
await tap('업데이트 확인', { scroll: true });
await pause(3000);
const updateRows = nodes();
assert.ok(updateRows.some(r => r.text === uiVersion));
assert.ok(updateRows.some(r => r['resource-id'] === `ota-update-${manifest.id}`));
assert.match(packageInfo(), /versionCode=10 /);
assert.equal(packageInfo().match(/lastUpdateTime=(.*)/)?.[1], before);
screenshot('verified-bundle-no-reinstall'); checkpoint('bundle applied after saved request, native version/install time unchanged');

await switchAccount('운영자 · 운영팀');
await tap('예약 관리'); await openBooking('접수 완료');
await tap('매니저 배정', { scroll: true }); await confirm();
checkpoint('operator assignment');

await switchAccount('매니저 · 박서연');
await tap('내 일정'); await openBooking('배정 대기');
await tap('배정 수락', { scroll: true }); await confirm();
for (const step of ['만남 완료', '병원 도착', '진료 완료', '귀가 완료']) {
  await tap(`${step} 기록`, { scroll: true }); await confirm();
}
checkpoint('manager acceptance and ordered visit progress');
await tap('결과 보고서 작성', { scroll: true });
await input('동행 내용', 'Visit completed safely');
await input('전달 사항', 'Hospital instructions handed over');
await input('약 수령', 'Medication received');
await input('다음 방문', 'Next week');
await confirm(); screenshot('report-complete'); checkpoint('report saved');

await switchAccount('보호자 · 이준호'); await tap('예약 내역'); await openBooking('동행 완료');
await tap('결과 공유 권한이 없습니다.', { scroll: true, allowText: true });
assert.ok(!nodes().some(r => String(r.text).includes('Visit completed safely')));
checkpoint('progress-only guardian cannot read report');

await switchAccount('환자 · 김영희'); await tap('예약 내역'); await openBooking('동행 완료');
await tap('공유 범위 변경', { scroll: true }); await tap('진행 상황과 결과'); await confirm();
await switchAccount('보호자 · 이준호'); await tap('예약 내역'); await openBooking('동행 완료');
await tap('Visit completed safely', { scroll: true, allowText: true });
await tap('이용 평가', { scroll: true }); await input('이용 후기', 'Thank you'); await confirm();
checkpoint('shared results and guardian review');

await switchAccount('운영자 · 운영팀'); await tap('정산 관리'); await openBooking('동행 완료');
await tap('정산 확정', { scroll: true }); await confirm();
await tap('정산 관리'); assertText('32,000원'); screenshot('settlement-confirmed'); checkpoint('operator settlement confirmation');

await switchAccount('환자 · 김영희'); await tap('예약 내역'); await openBooking('동행 완료');
await tap('공유 범위 변경', { scroll: true }); await tap('공유하지 않음'); await confirm();
await switchAccount('보호자 · 이준호'); await tap('예약 내역');
assert.ok(!nodes().some(r => String(r['content-desc']).includes('OTA Draft Hospital')));
checkpoint('sharing revocation removes guardian record');

adb(['shell', 'svc', 'wifi', 'disable']); adb(['shell', 'svc', 'data', 'disable']);
adb(['shell', 'am', 'force-stop', 'com.ourmine.caregov.demo']);
adb(['shell', 'am', 'start', '-n', 'com.ourmine.caregov.demo/.MainActivity']); await pause(4000);
await tap('예약 내역'); assertText('이전예약보존병원');
await tap('내 정보'); await tap('업데이트 확인', { scroll: true });
await pause(2000); assertText(uiVersion);
adb(['shell', 'svc', 'wifi', 'enable']); adb(['shell', 'svc', 'data', 'enable']);
checkpoint('offline cold start keeps cached bundle, account and migrated records');

writeFileSync('output/mobile-qa/results.json', JSON.stringify({ checks, updateId: manifest.id, nativeVersionCode: 10, nativeLastUpdateTime: before, phoneReceiptVerified: false }, null, 2));
console.log(`Passed ${checks.length} workflow checkpoints`);
