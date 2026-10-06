import test from 'node:test';
import assert from 'node:assert/strict';
import { accounts, authenticate, migrateLegacy, mutate, normalizeBooking, canReadReport, visible, nextStep } from '../src/domain.mjs';
const [patient, guardian, manager, operator, manager2] = accounts;
const clock = new Date('2026-10-06T00:00:00Z');
const draft = () => ({ patient: '김영희', hospital: '서울의료원', department: '내과', date: '2026-10-10', time: '10:00', meeting: '1층 로비', support: '도보 이동', note: '',
  options: { patientPhone: patient.phone, guardianPhone: '', hours: 2, consent: true, service: 'OUTPATIENT', sharing: 'PROGRESS', relationship: '본인' } });
const create = (a = patient, d = draft(), id = 'CG-TEST') => mutate([], a, 'create', { draft: d, newId: id }, clock);
const change = (rows, actor, action, args = {}) => mutate(rows, actor, action, { id: rows[0].id, revision: rows[0].revision, ...args }, clock);
test('all selected identities use the specified password', () => {
  for (const a of accounts) assert.equal(authenticate(a.id, '260401'), a);
  assert.throws(() => authenticate('patient', '000000'));
  assert.throws(() => authenticate('unknown', '260401'));
});
test('legacy migration keeps record IDs, account, signed-out flag, reports and revisions', () => {
  const row = normalizeBooking({ ...draft(), id: 'CG-OLD', requesterId: 'patient', patientId: 'patient', status: '동행 완료', revision: 9, workflow: { report: { summary: '기존 기록', feeWon: 40000 }, rating: 5 } });
  const migrated = migrateLegacy({ bookings: JSON.stringify([row]), account: 'manager2', signedOut: true });
  assert.equal(migrated.records[0].workflow.report.summary, '기존 기록');
  assert.equal(migrated.records[0].revision, 9); assert.equal(migrated.accountId, 'manager2'); assert.equal(migrated.signedOut, true);
  assert.deepEqual(migrateLegacy({ bookings: '[]' }).records, []);
  assert.throws(() => migrateLegacy({ bookings: 'corrupted' }));
  assert.throws(() => migrateLegacy({ bookings: '[{}]' }));
});
test('self/proxy booking links explicitly, recalculates price and validates consent/contact/time', () => {
  assert.equal(create()[0].patientId, patient.id);
  assert.equal(create()[0].options.estimatedWon, 40000);
  const proxy = draft(); proxy.options.guardianPhone = guardian.phone; proxy.options.relationship = '어머니';
  assert.equal(create(guardian, proxy)[0].patientId, '');
  proxy.options.patientAccountId = 'patient'; assert.equal(create(guardian, proxy)[0].patientId, 'patient');
  assert.throws(() => create(operator));
  for (const invalid of [ { ...draft(), patient: '다른 이름' }, { ...draft(), time: '35:00' }, { ...draft(), date: '2026-02-30' }, { ...draft(), options: { ...draft().options, consent: false } }, { ...draft(), options: { ...draft().options, patientPhone: '123' } } ]) assert.throws(() => create(patient, invalid));
});
test('permissions distinguish shared progress and shared result, requester cannot be spoofed', () => {
  const rows = create(); assert.equal(visible(guardian, rows).length, 1); assert.equal(canReadReport(guardian, rows[0]), false);
  assert.throws(() => change(rows, guardian, 'changeSharing', { scope: 'RESULTS' }));
  const shared = change(rows, patient, 'changeSharing', { scope: 'RESULTS' }); assert.equal(canReadReport(guardian, shared[0]), true);
  const revoked = change(shared, patient, 'changeSharing', { scope: 'NONE' }); assert.equal(visible(guardian, revoked).length, 0);
  assert.throws(() => change(rows, { ...patient, phone: '999' }, 'cancel', { reason: '취소' }));
});
test('revision conflict and unauthorized edits fail without changing original records', () => {
  const rows = create(); assert.throws(() => mutate(rows, patient, 'cancel', { id: rows[0].id, revision: 99, reason: '변경' }, clock));
  assert.throws(() => change(rows, guardian, 'cancel', { reason: '변경' }));
  const changed = change(rows, patient, 'update', { draft: { ...draft(), hospital: '다른 병원' } });
  assert.equal(changed[0].revision, 1); assert.equal(rows[0].hospital, '서울의료원');
  assert.equal(change(rows, patient, 'cancel', { reason: '일정 변경' })[0].status, '신청 취소');
  const injected = change(rows, patient, 'update', { draft: { ...draft(), id: 'CG-FAKE', requesterId: 'guardian', manager: '최지은', status: '동행 완료' } });
  assert.equal(injected[0].id, rows[0].id); assert.equal(injected[0].requesterId, 'patient'); assert.equal(injected[0].status, '접수 완료'); assert.equal(injected[0].manager, '');
});
test('assignment prevents overlapping manager schedules and permits another manager', () => {
  let rows = create(); rows = change(rows, operator, 'assign', { managerId: 'manager' });
  rows = mutate(rows, patient, 'create', { draft: { ...draft(), time: '11:00' }, newId: 'CG-SECOND' }, clock);
  assert.throws(() => mutate(rows, operator, 'assign', { id: 'CG-SECOND', revision: 0, managerId: 'manager' }, clock));
  assert.equal(mutate(rows, operator, 'assign', { id: 'CG-SECOND', revision: 0, managerId: 'manager2' }, clock)[1].manager, manager2.name);
});
test('reschedule requires manager re-acceptance; decline clears assignment', () => {
  let rows = change(create(), operator, 'assign', { managerId: 'manager' });
  rows = change(rows, manager, 'respond', { accept: true });
  rows = change(rows, operator, 'reschedule', { date: '2026-10-11', time: '10:00', reason: '병원 일정 변경' });
  assert.equal(rows[0].status, '배정 대기');
  rows = change(rows, manager, 'respond', { accept: false, reason: '다른 일정' }); assert.equal(rows[0].manager, ''); assert.equal(rows[0].status, '접수 완료');
});
test('full visit/report/settlement/review workflow is ordered and prices are immutable snapshots', () => {
  let rows = change(create(), operator, 'assign', { managerId: 'manager' });
  assert.throws(() => change(rows, manager2, 'respond', { accept: true }));
  rows = change(rows, manager, 'respond', { accept: true });
  assert.throws(() => change(rows, manager, 'advance', { expectedStatus: '귀가 완료' }));
  while (nextStep(rows[0])) rows = change(rows, manager, 'advance', { expectedStatus: rows[0].status });
  assert.equal(rows[0].status, '귀가 완료');
  rows = change(rows, manager, 'complete', { report: { summary: '동행 완료', instructions: '', medication: '', nextVisit: '', minutes: 91, feeWon: 1 } });
  assert.equal(rows[0].workflow.report.feeWon, 40000); assert.equal(rows[0].workflow.report.managerWon, 32000);
  assert.throws(() => change(rows, manager, 'confirmSettlement'));
  rows = change(rows, operator, 'confirmSettlement'); assert.ok(rows[0].workflow.settlementAt);
  assert.throws(() => change(rows, operator, 'confirmSettlement'));
  assert.throws(() => change(rows, guardian, 'review', { rating: 5, text: '' }));
  rows = change(rows, patient, 'review', { rating: 5, text: '감사합니다' }); assert.equal(rows[0].workflow.rating, 5);
  assert.throws(() => change(rows, patient, 'review', { rating: 5, text: '' }));
});
test('exam and return-home services use their own steps', () => {
  const b = normalizeBooking({ ...draft(), status: '병원 도착', options: { ...draft().options, service: 'EXAMINATION' } }); assert.equal(nextStep(b), '검사 완료');
  b.options.service = 'RETURN_HOME'; b.status = '만남 완료'; assert.equal(nextStep(b), '귀가 완료');
});
