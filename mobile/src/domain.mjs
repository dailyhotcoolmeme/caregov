export const accounts = [
  { id: 'patient', name: '김영희', phone: '01000000001', role: 'PATIENT' },
  { id: 'guardian', name: '이준호', phone: '01000000002', role: 'GUARDIAN' },
  { id: 'manager', name: '박서연', phone: '01000000003', role: 'MANAGER' },
  { id: 'operator', name: '운영팀', phone: '01000000004', role: 'OPERATOR' },
  { id: 'manager2', name: '최지은', phone: '01000000005', role: 'MANAGER' },
];
export const roles = { PATIENT: '환자', GUARDIAN: '보호자', MANAGER: '매니저', OPERATOR: '운영자' };
export const services = { OUTPATIENT: '외래 진료', EXAMINATION: '검사 동행', RETURN_HOME: '귀가 동행' };
export const scopes = { NONE: '공유하지 않음', PROGRESS: '진행 상황만', RESULTS: '진행 상황과 결과' };
export const beforeVisit = ['접수 완료', '배정 대기', '예약 확정'];
const requireValue = (condition, message = '입력 내용을 확인해 주세요.') => { if (!condition) throw new Error(message); };
export const localDate = (value = new Date()) => `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
export const visitTime = b => new Date(`${b.date}T${b.time}:00`).getTime();
export function normalizeBooking(b) {
  const options = b.options || {};
  return {
    ...b, manager: b.manager || '', patientId: b.patientId || '', revision: b.revision || 0,
    cancellationReason: b.cancellationReason || '',
    options: { patientPhone: '', guardianPhone: '', relationship: '본인', service: 'OUTPATIENT', hours: 2,
      consent: false, estimatedWon: null, ...options,
      sharing: Object.hasOwn(options, 'sharing') ? (scopes[options.sharing] ? options.sharing : 'NONE') : 'PROGRESS' },
    workflow: { events: [], report: null, settlementAt: '', rating: 0, review: '', ...b.workflow },
  };
}
export function initialRecords() {
  return [normalizeBooking({ id: 'CG-1001', requesterId: 'patient', patientId: 'patient', patient: '김영희',
    hospital: '서울의료원', department: '내과', date: localDate(), time: '10:00',
    meeting: '서울의료원 1층 로비', support: '도보 이동', note: '접수 창구까지 함께 이동해 주세요.',
    manager: '박서연', status: '예약 확정' })];
}
export function migrateLegacy(legacy) {
  // Missing data means first install. Invalid existing data is never replaced with fixtures.
  const rows = legacy?.bookings == null ? initialRecords() : JSON.parse(legacy.bookings);
  requireValue(Array.isArray(rows) && rows.every(b => ['id', 'requesterId', 'patient', 'hospital', 'department', 'date', 'time', 'meeting', 'support', 'note', 'status'].every(k => typeof b[k] === 'string')), '기존 예약 데이터를 읽지 못했습니다. 원본은 보존되어 있습니다.');
  const accountId = accounts.some(a => a.id === legacy?.account) ? legacy.account : 'patient';
  return { schema: 1, records: rows.map(normalizeBooking), accountId, signedOut: !!legacy?.signedOut };
}
export function authenticate(id, pin) {
  const account = accounts.find(a => a.id === id);
  requireValue(account && pin === '260401', '비밀번호를 확인해 주세요.');
  return account;
}
export function visible(account, records) {
  return records.filter(b => account.role === 'OPERATOR' ||
    (account.role === 'MANAGER' && b.manager === account.name) ||
    (account.role === 'PATIENT' && (b.requesterId === account.id || b.patientId === account.id)) ||
    (account.role === 'GUARDIAN' && (b.requesterId === account.id || (account.id === 'guardian' && b.patientId === 'patient' && b.options.sharing !== 'NONE'))));
}
export function canReadReport(account, b) {
  return account.role === 'OPERATOR' || (account.role === 'MANAGER' && b.manager === account.name) ||
    (account.role === 'PATIENT' && (b.patientId === account.id || b.requesterId === account.id)) ||
    (account.role === 'GUARDIAN' && visible(account, [b]).length > 0 && b.options.sharing === 'RESULTS');
}
export const canModify = (a, b, now = Date.now()) => ['PATIENT', 'GUARDIAN'].includes(a.role) && a.id === b.requesterId && b.status === '접수 완료' && visitTime(b) > now;
export const canShare = (a, b) => ['PATIENT', 'GUARDIAN'].includes(a.role) && b.status !== '신청 취소' && (b.requesterId === a.id || (a.role === 'PATIENT' && b.patientId === a.id));
export function nextStep(b) {
  const steps = b.options.service === 'RETURN_HOME' ? ['예약 확정', '만남 완료', '귀가 완료'] :
    ['예약 확정', '만남 완료', '병원 도착', b.options.service === 'EXAMINATION' ? '검사 완료' : '진료 완료', '귀가 완료'];
  const index = steps.indexOf(b.status);
  return index >= 0 ? steps[index + 1] : undefined;
}
const phone = value => String(value || '').replace(/\D/g, '');
const validPhone = value => /^01[016789]\d{7,8}$/.test(phone(value));
export function validateDraft(account, draft, now = Date.now()) {
  requireValue(['PATIENT', 'GUARDIAN'].includes(account.role), '신청 권한이 없습니다.');
  requireValue(['patient', 'hospital', 'department', 'meeting'].every(k => typeof draft[k] === 'string' && draft[k].trim() && draft[k].length <= 120), '이용자와 병원, 만날 장소를 확인해 주세요.');
  requireValue(/^\d{4}-\d{2}-\d{2}$/.test(draft.date) && /^\d{2}:\d{2}$/.test(draft.time) && Number.isFinite(visitTime(draft)) && visitTime(draft) > now, '방문 일시는 현재 이후로 선택해 주세요.');
  const actual = new Date(visitTime(draft));
  requireValue(localDate(actual) === draft.date && `${String(actual.getHours()).padStart(2, '0')}:${String(actual.getMinutes()).padStart(2, '0')}` === draft.time, '방문 일시를 확인해 주세요.');
  requireValue(['도보 이동', '보행 보조', '휠체어 이용'].includes(draft.support) && typeof draft.note === 'string' && draft.note.length <= 1000);
  const o = draft.options;
  requireValue(o?.consent, '신청 동의 여부를 확인해 주세요.');
  requireValue(validPhone(o.patientPhone), '이용자 연락처를 확인해 주세요.');
  requireValue(!o.guardianPhone || validPhone(o.guardianPhone), '보호자 연락처를 확인해 주세요.');
  requireValue(Number.isInteger(o.hours) && o.hours >= 1 && o.hours <= 8 && services[o.service] && scopes[o.sharing]);
  let patientId = account.id;
  if (account.role === 'GUARDIAN') {
    requireValue(['어머니', '아버지', '배우자', '자녀', '친척', '기타'].includes(o.relationship), '이용자와의 관계를 선택해 주세요.');
    requireValue(validPhone(o.guardianPhone), '보호자 연락처를 확인해 주세요.');
    patientId = o.patientAccountId || '';
    requireValue(!patientId || (account.id === 'guardian' && patientId === 'patient' && draft.patient === '김영희'), '연결된 이용자를 확인해 주세요.');
  } else requireValue(draft.patient === account.name, '본인 신청은 본인 이름으로 접수해야 합니다.');
  return { patient: draft.patient.trim(), hospital: draft.hospital.trim(), department: draft.department.trim(), date: draft.date, time: draft.time, support: draft.support, meeting: draft.meeting.trim(), note: draft.note.trim(), patientId,
    options: { ...o, patientPhone: phone(o.patientPhone), guardianPhone: phone(o.guardianPhone), patientAccountId: patientId, relationship: account.role === 'PATIENT' ? '본인' : o.relationship, estimatedWon: o.hours * 20000 } };
}
export function mutate(records, account, action, args = {}, clock = new Date()) {
  requireValue(accounts.some(a => a.id === account?.id && a.name === account.name && a.role === account.role && a.phone === account.phone), '계정 정보를 확인해 주세요.');
  const now = clock.getTime(), at = clock.toISOString();
  const event = (b, title) => ({ ...b.workflow, events: [...b.workflow.events, { title, at, actor: account.name }] });
  if (action === 'create') {
    const validated = validateDraft(account, args.draft, now);
    requireValue(typeof args.newId === 'string' && /^CG-[A-Z0-9-]+$/.test(args.newId) && !records.some(b => b.id === args.newId));
    const row = normalizeBooking({ ...validated, id: args.newId, requesterId: account.id, manager: '', status: '접수 완료' });
    row.workflow = event(row, '접수 완료');
    return [...records, row];
  }
  const original = records.find(b => b.id === args.id);
  requireValue(original, '예약을 찾을 수 없습니다.');
  requireValue(original.revision === args.revision, '예약이 변경되었습니다. 다시 확인해 주세요.');
  let b = { ...original, options: { ...original.options }, workflow: { ...original.workflow } };
  const assigned = () => requireValue(account.role === 'MANAGER' && b.manager === account.name, '담당 매니저만 처리할 수 있습니다.');
  const operator = () => requireValue(account.role === 'OPERATOR' && beforeVisit.includes(b.status), '처리할 수 없는 예약입니다.');
  const reason = () => { requireValue(typeof args.reason === 'string' && args.reason.trim() && args.reason.length <= 200, '사유를 입력해 주세요.'); return args.reason.trim(); };
  const available = (candidate, manager) => {
    const start = visitTime(candidate), end = start + candidate.options.hours * 3600000;
    requireValue(!records.some(other => other.id !== b.id && other.manager === manager && !['신청 취소', '동행 완료'].includes(other.status) && visitTime(other) < end && visitTime(other) + other.options.hours * 3600000 > start), '해당 시간에 매니저의 다른 일정이 있습니다.');
  };
  switch (action) {
    case 'update':
      requireValue(canModify(account, b, now), '변경할 수 없는 예약입니다.');
      b = { ...b, ...validateDraft(account, args.draft, now), workflow: event(b, '신청 내용 수정') }; break;
    case 'cancel':
      requireValue(canModify(account, b, now), '취소할 수 없는 예약입니다.');
      b = { ...b, status: '신청 취소', cancellationReason: reason(), workflow: event(b, '신청 취소') }; break;
    case 'assign': {
      operator(); requireValue(visitTime(b) > now, '방문 일시를 먼저 변경해 주세요.');
      const manager = accounts.find(a => a.id === args.managerId && a.role === 'MANAGER');
      requireValue(manager && manager.name !== b.manager, '매니저를 확인해 주세요.'); available(b, manager.name);
      b = { ...b, manager: manager.name, status: '배정 대기', workflow: event(b, `매니저 배정 · ${manager.name}`) }; break;
    }
    case 'reschedule': {
      operator(); const message = reason();
      const candidate = { ...b, date: args.date, time: args.time };
      requireValue(/^\d{4}-\d{2}-\d{2}$/.test(args.date) && /^([01]\d|2[0-3]):[0-5]\d$/.test(args.time) && visitTime(candidate) > now && localDate(new Date(visitTime(candidate))) === args.date, '방문 일시는 현재 이후로 선택해 주세요.');
      if (b.manager) available(candidate, b.manager);
      b = { ...candidate, status: b.manager ? '배정 대기' : '접수 완료', workflow: event(b, `일정 변경 · ${message}`) }; break;
    }
    case 'operatorCancel':
      operator(); b = { ...b, status: '신청 취소', cancellationReason: reason(), workflow: event(b, `운영팀 취소 · ${args.reason.trim()}`) }; break;
    case 'respond':
      assigned(); requireValue(b.status === '배정 대기', '응답할 수 없는 배정입니다.');
      if (args.accept) { available(b, account.name); b = { ...b, status: '예약 확정', workflow: event(b, '배정 수락') }; }
      else { const message = reason(); b = { ...b, manager: '', status: '접수 완료', workflow: event(b, `배정 거절 · ${message}`) }; } break;
    case 'advance': {
      assigned(); const next = nextStep(b);
      requireValue(b.status === args.expectedStatus && next, '진행 상태가 변경되었습니다. 다시 확인해 주세요.');
      b = { ...b, status: next, workflow: event(b, next) }; break;
    }
    case 'complete': {
      assigned(); requireValue(b.status === '귀가 완료' && !b.workflow.report, '귀가 완료 후 결과를 등록할 수 있습니다.');
      const r = args.report;
      requireValue(r && typeof r.summary === 'string' && r.summary.trim() && r.summary.length <= 2000 && ['instructions', 'medication', 'nextVisit'].every(k => typeof r[k] === 'string' && r[k].length <= 1000), '동행 내용을 확인해 주세요.');
      requireValue(Number.isInteger(r.minutes) && r.minutes >= 1 && r.minutes <= 480, '이용 시간은 1분부터 480분까지 입력해 주세요.');
      const feeWon = Math.max(2, Math.ceil(r.minutes / 30)) * 10000;
      const report = { ...r, summary: r.summary.trim(), instructions: r.instructions.trim(), medication: r.medication.trim(), nextVisit: r.nextVisit.trim(), submittedAt: at, feeWon, managerWon: feeWon * .8 };
      b = { ...b, status: '동행 완료', workflow: { ...event(b, '동행 완료 · 결과 등록'), report } }; break;
    }
    case 'confirmSettlement':
      requireValue(account.role === 'OPERATOR' && b.status === '동행 완료' && b.workflow.report && !b.workflow.settlementAt, '정산을 확정할 수 없는 예약입니다.');
      b.workflow = { ...event(b, '정산 확정'), settlementAt: at }; break;
    case 'review':
      requireValue(['PATIENT', 'GUARDIAN'].includes(account.role) && canReadReport(account, b) && b.status === '동행 완료' && !b.workflow.rating, '평가를 등록할 수 없는 예약입니다.');
      requireValue(Number.isInteger(args.rating) && args.rating >= 1 && args.rating <= 5 && typeof args.text === 'string' && args.text.length <= 1000);
      b.workflow = { ...event(b, '이용 평가 등록'), rating: args.rating, review: args.text.trim() }; break;
    case 'changeSharing':
      requireValue(canShare(account, b) && scopes[args.scope], '공유 범위를 변경할 수 없습니다.');
      b.options.sharing = args.scope; b.workflow = event(b, '공유 범위 변경'); break;
    default: throw new Error('지원하지 않는 변경입니다.');
  }
  b.revision = original.revision + 1;
  return records.map(row => row.id === b.id ? b : row);
}
