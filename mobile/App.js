import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, BackHandler, Keyboard, KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import DateTimePicker from '@react-native-community/datetimepicker';
import * as Crypto from 'expo-crypto';
import Ionicons from '@expo/vector-icons/Ionicons';
import { accounts, roles, services, scopes, beforeVisit, localDate, visible, canReadReport, canModify, canShare, nextStep, authenticate, mutate } from './src/domain.mjs';
import { loadState, saveState } from './src/storage';
import { checkUpdate, setUpdateBlocked, subscribeUpdates, releaseInfo } from './src/ota';
import { Button, Choices, Field, Heading, Label, Row, Section, Select, Status, colors, s } from './src/components';

export const UI_RELEASE = '0.6.3';
const entries = object => Object.entries(object).map(([value, label]) => ({ value, label }));
const money = number => `${Number(number || 0).toLocaleString('ko-KR')}원`;
const accountOptions = accounts.map(a => ({ value: a.id, label: `${roles[a.role]} · ${a.name}` }));
const supports = ['도보 이동', '보행 보조', '휠체어 이용'].map(value => ({ value, label: value }));
const titleForTab = (tab, a) => ({ home: '홈', bookings: a.role === 'MANAGER' ? '내 일정' : a.role === 'OPERATOR' ? '예약 관리' : '예약 내역', account: '내 정보', activity: ['MANAGER', 'OPERATOR'].includes(a.role) ? '정산 관리' : '알림' })[tab];

function AccountForm({ current, onSubmit, onCancel, switching }) {
  const [id, setId] = useState(current || 'patient'), [pin, setPin] = useState('');
  return <><Heading>{switching ? '사용자 변경' : '로그인'}</Heading><Select label="사용자 선택" value={id} options={accountOptions} onChange={setId} />
    <Field label="비밀번호" value={pin} onChangeText={setPin} secure numeric maxLength={6} />
    <Button title={switching ? '사용자 변경' : '로그인'} icon="log-in-outline" onPress={() => onSubmit(id, pin)} />
    {onCancel && <Button title="취소" secondary onPress={onCancel} />}</>;
}
function DateFields({ date, time, onDate, onTime }) {
  const [picker, setPicker] = useState(null);
  const value = new Date(`${date}T${time}:00`);
  return <><Label style={s.fieldLabel}>방문 일시</Label><View style={styles.horizontal}>
    <View style={{ flex: 1 }}><Button title={date} secondary icon="calendar-outline" onPress={() => setPicker('date')} /></View>
    <View style={{ flex: 1 }}><Button title={time} secondary icon="time-outline" onPress={() => setPicker('time')} /></View>
  </View>{picker && <DateTimePicker value={Number.isFinite(value.getTime()) ? value : new Date()} mode={picker} is24Hour onChange={(event, selected) => {
    const mode = picker; setPicker(null); if (!selected || event.type === 'dismissed') return;
    if (mode === 'date') onDate(localDate(selected)); else onTime(`${String(selected.getHours()).padStart(2, '0')}:${String(selected.getMinutes()).padStart(2, '0')}`);
  }} />}</>;
}
function BookingForm({ account, original, onSave, onCancel }) {
  const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1);
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState(original ? { ...original, options: { ...original.options, patientAccountId: original.patientId, consent: false } } : {
    patient: account.role === 'PATIENT' ? account.name : '김영희', hospital: '', department: '', date: localDate(tomorrow), time: '10:00',
    meeting: '', support: '도보 이동', note: '', options: { patientPhone: account.role === 'PATIENT' ? account.phone : accounts[0].phone, guardianPhone: account.role === 'GUARDIAN' ? account.phone : '',
      patientAccountId: account.role === 'GUARDIAN' ? 'patient' : account.id, relationship: account.role === 'PATIENT' ? '본인' : '어머니', sharing: 'PROGRESS', service: 'OUTPATIENT', hours: 2, consent: false },
  });
  const field = (key, value) => setDraft(d => ({ ...d, [key]: value, options: { ...d.options, consent: false } }));
  const option = (key, value) => setDraft(d => ({ ...d, options: { ...d.options, consent: false, [key]: value } }));
  const scroll = useRef(null);
  const next = n => { setStep(n); scroll.current?.scrollTo({ y: 0, animated: false }); };
  useEffect(() => { const listener = BackHandler.addEventListener('hardwareBackPress', () => { if (step > 0) next(step - 1); else onCancel(); return true; }); return () => listener.remove(); }, [step, onCancel]);
  return <ScrollView ref={scroll} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
    <Label muted>{step + 1} / 3</Label><Heading>{['이용자 정보', '방문 정보', '신청 확인'][step]}</Heading>
    {step === 0 && <>
      {account.role === 'GUARDIAN' && <Select label="이용자 선택" value={draft.options.patientAccountId || ''} options={[{ value: 'patient', label: '김영희' }, { value: '', label: '다른 이용자' }]} onChange={value => setDraft(d => ({ ...d, patient: value ? '김영희' : '', options: { ...d.options, patientAccountId: value, consent: false } }))} />}
      <Field label="이용자 이름" value={draft.patient} editable={account.role !== 'PATIENT' && !draft.options.patientAccountId} onChangeText={v => field('patient', v)} />
      <Field label="이용자 연락처" value={draft.options.patientPhone} numeric onChangeText={v => option('patientPhone', v)} />
      {account.role === 'GUARDIAN' && <Select label="이용자와의 관계" value={draft.options.relationship} options={['어머니', '아버지', '배우자', '자녀', '친척', '기타'].map(value => ({ value, label: value }))} onChange={v => option('relationship', v)} />}
      <Field label="보호자 연락처" value={draft.options.guardianPhone} numeric onChangeText={v => option('guardianPhone', v)} />
      <Select label="동행 서비스" value={draft.options.service} options={entries(services)} onChange={v => option('service', v)} />
      <Label style={s.fieldLabel}>예상 이용 시간</Label><View style={styles.stepper}>
        <Pressable accessibilityRole="button" accessibilityLabel="이용 시간 줄이기" disabled={draft.options.hours <= 1} style={styles.iconButton} onPress={() => option('hours', draft.options.hours - 1)}><Ionicons name="remove" size={24} color={colors.green} /></Pressable>
        <Label style={{ width: 100, textAlign: 'center' }}>{draft.options.hours}시간</Label>
        <Pressable accessibilityRole="button" accessibilityLabel="이용 시간 늘리기" disabled={draft.options.hours >= 8} style={styles.iconButton} onPress={() => option('hours', draft.options.hours + 1)}><Ionicons name="add" size={24} color={colors.green} /></Pressable>
      </View><Row label="예상 이용료" value={money(draft.options.hours * 20000)} />
      <Choices label="보호자 공유 범위" value={draft.options.sharing} options={entries(scopes)} onChange={v => option('sharing', v)} />
    </>}
    {step === 1 && <>
      <Field label="병원" value={draft.hospital} onChangeText={v => field('hospital', v)} /><Field label="진료과" value={draft.department} onChangeText={v => field('department', v)} />
      <DateFields date={draft.date} time={draft.time} onDate={v => field('date', v)} onTime={v => field('time', v)} />
      <Field label="만날 장소" value={draft.meeting} onChangeText={v => field('meeting', v)} />
      <Choices label="이동 지원" value={draft.support} options={supports} onChange={v => field('support', v)} />
      <Field label="요청 사항" value={draft.note} multiline maxLength={1000} onChangeText={v => field('note', v)} />
    </>}
    {step === 2 && <><Row label="이용자" value={draft.patient} /><Row label="연락처" value={draft.options.patientPhone} /><Row label="보호자" value={draft.options.guardianPhone} />
      <Row label="병원 · 진료과" value={`${draft.hospital} · ${draft.department}`} /><Row label="방문 일시" value={`${draft.date} ${draft.time}`} /><Row label="만날 장소" value={draft.meeting} />
      <Row label="서비스" value={services[draft.options.service]} /><Row label="이동 지원" value={draft.support} /><Row label="요청 사항" value={draft.note} />
      <Row label="공유 범위" value={scopes[draft.options.sharing]} /><Row label="예상 시간" value={`${draft.options.hours}시간`} /><Row label="예상 이용료" value={money(draft.options.hours * 20000)} />
      <Pressable accessibilityRole="checkbox" accessibilityState={{ checked: draft.options.consent }} accessibilityLabel="신청 정보 및 공유 범위에 동의" style={s.choice} onPress={() => option('consent', !draft.options.consent)}>
        <Ionicons name={draft.options.consent ? 'checkbox' : 'square-outline'} size={25} color={colors.green} /><Label style={{ flex: 1 }}>신청 정보 및 공유 범위에 동의합니다.</Label>
      </Pressable></>}
    <Button title={step === 2 ? (original ? '변경 내용 저장' : '동행 신청') : '다음'} icon={step === 2 ? 'checkmark-outline' : 'arrow-forward-outline'} onPress={() => step === 2 ? onSave(draft) : next(step + 1)} />
    <Button title={step ? '이전' : '신청 취소'} secondary onPress={() => step ? next(step - 1) : onCancel()} />
  </ScrollView>;
}

function BookingCard({ booking, onPress }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={`${booking.patient} ${booking.hospital} ${booking.status}`} onPress={onPress} style={s.card}>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8 }}><Label style={{ fontWeight: '700' }}>{booking.hospital}</Label><Status value={booking.status} /></View>
    <Label>{booking.patient} · {booking.department}</Label><Label muted>{booking.date} {booking.time}</Label>
    <Label muted>{booking.manager ? `담당 ${booking.manager}` : '매니저 배정 전'}</Label>
  </Pressable>;
}

function Detail({ b, account, onAction, onEdit, onDialog }) {
  const reportAllowed = canReadReport(account, b), privateAllowed = account.id === b.requesterId || reportAllowed;
  const manager = account.role === 'MANAGER', operator = account.role === 'OPERATOR';
  return <><Section title={b.hospital}><Status value={b.status} /><Row label="이용자" value={b.patient} /><Row label="진료과" value={b.department} /><Row label="방문 일시" value={`${b.date} ${b.time}`} /><Row label="만날 장소" value={b.meeting} />
    <Row label="서비스" value={services[b.options.service]} /><Row label="이동 지원" value={b.support} /><Row label="담당 매니저" value={b.manager || '배정 전'} />
    {privateAllowed && <><Row label="이용자 연락처" value={b.options.patientPhone} /><Row label="보호자 연락처" value={b.options.guardianPhone} /><Row label="요청 사항" value={b.note} /><Row label="예상 이용료" value={b.options.estimatedWon == null ? '미정' : money(b.options.estimatedWon)} /></>}
    {b.status === '신청 취소' && privateAllowed && <Row label="취소 사유" value={b.cancellationReason} />}
  </Section>
    {canModify(account, b) && <Section><Button title="신청 내용 수정" icon="create-outline" secondary onPress={onEdit} /><Button title="신청 취소" secondary onPress={() => onDialog('cancel')} /></Section>}
    {operator && beforeVisit.includes(b.status) && <Section title="예약 처리"><Button title="매니저 배정" icon="person-add-outline" onPress={() => onDialog('assign')} /><Button title="일정 변경" secondary icon="calendar-outline" onPress={() => onDialog('reschedule')} /><Button title="예약 취소" secondary onPress={() => onDialog('operatorCancel')} /></Section>}
    {manager && b.status === '배정 대기' && <Section title="배정 응답"><Button title="배정 수락" icon="checkmark-outline" onPress={() => onDialog('accept')} /><Button title="배정 거절" secondary onPress={() => onDialog('respond')} /></Section>}
    {manager && nextStep(b) && <Section title="동행 진행"><Button title={`${nextStep(b)} 기록`} icon="checkmark-circle-outline" onPress={() => onDialog('advance')} /></Section>}
    {manager && b.status === '귀가 완료' && !b.workflow.report && <Section><Button title="결과 보고서 작성" icon="document-text-outline" onPress={() => onDialog('complete')} /></Section>}
    <Section title="진행 기록">{b.workflow.events.length ? b.workflow.events.map((event, index) => <View key={`${event.at}-${index}`} style={styles.timeline}>
      <View style={styles.timelineDot} /><View style={{ flex: 1 }}><Label style={{ fontWeight: '600' }}>{privateAllowed ? event.title : event.title.split(' · ')[0]}</Label><Label muted style={{ fontSize: 14 }}>{new Date(event.at).toLocaleString('ko-KR')} · {event.actor}</Label></View>
    </View>) : <Label muted>{b.status}</Label>}</Section>
    {b.workflow.report && (reportAllowed ? <Section title="결과 보고서"><Row label="동행 내용" value={b.workflow.report.summary} /><Row label="전달 사항" value={b.workflow.report.instructions} /><Row label="약 수령" value={b.workflow.report.medication} /><Row label="다음 방문" value={b.workflow.report.nextVisit} /><Row label="이용 시간" value={`${b.workflow.report.minutes}분`} /><Row label="이용료" value={money(b.workflow.report.feeWon)} />
      {(manager || operator) && <><Row label="매니저 정산액" value={money(b.workflow.report.managerWon)} /><Row label="정산 상태" value={b.workflow.settlementAt ? '확정' : '확정 대기'} /></>}
      {operator && !b.workflow.settlementAt && <Button title="정산 확정" icon="checkmark-done-outline" onPress={() => onDialog('confirmSettlement')} />}
      {['PATIENT', 'GUARDIAN'].includes(account.role) && !b.workflow.rating && <Button title="이용 평가" icon="star-outline" secondary onPress={() => onDialog('review')} />}
    </Section> : <Section title="결과 보고서"><Label muted>결과 공유 권한이 없습니다.</Label></Section>)}
    {b.workflow.rating > 0 && reportAllowed && <Section title="이용 평가"><Row label="평점" value={`${b.workflow.rating} / 5`} /><Label>{b.workflow.review}</Label></Section>}
    {canShare(account, b) && <Section title="보호자 공유"><Label>{scopes[b.options.sharing]}</Label><Button title="공유 범위 변경" icon="people-outline" secondary onPress={() => onDialog('changeSharing')} /></Section>}
    <Row label="예약 번호" value={b.id} />
  </>;
}

function ActionForm({ type, b, onSave, onCancel }) {
  const [reason, setReason] = useState(''), [managerId, setManagerId] = useState('manager');
  const [date, setDate] = useState(b.date), [time, setTime] = useState(b.time), [scope, setScope] = useState(b.options.sharing);
  const [rating, setRating] = useState(5), [text, setText] = useState('');
  const [report, setReport] = useState({ summary: '', instructions: '', medication: '', nextVisit: '', minutes: String(b.options.hours * 60) });
  const titles = { assign: '매니저 배정', reschedule: '일정 변경', cancel: '신청 취소', operatorCancel: '예약 취소', respond: '배정 거절', complete: '결과 보고서', confirmSettlement: '정산 확정', review: '이용 평가', changeSharing: '공유 범위 변경', accept: '배정 수락', advance: '동행 진행' };
  return <><Heading>{titles[type]}</Heading>
    {type === 'assign' && <Select label="담당 매니저" value={managerId} options={accounts.filter(a => a.role === 'MANAGER').map(a => ({ value: a.id, label: a.name }))} onChange={setManagerId} />}
    {type === 'reschedule' && <DateFields date={date} time={time} onDate={setDate} onTime={setTime} />}
    {['reschedule', 'cancel', 'operatorCancel', 'respond'].includes(type) && <Field label="사유" value={reason} onChangeText={setReason} multiline maxLength={200} />}
    {type === 'complete' && <>{[['summary', '동행 내용'], ['instructions', '전달 사항'], ['medication', '약 수령'], ['nextVisit', '다음 방문']].map(([key, label]) => <Field key={key} label={label} value={report[key]} multiline maxLength={key === 'summary' ? 2000 : 1000} onChangeText={value => setReport(r => ({ ...r, [key]: value }))} />)}
      <Field label="이용 시간 (분)" value={report.minutes} numeric onChangeText={value => setReport(r => ({ ...r, minutes: value }))} /></>}
    {type === 'changeSharing' && <Choices label="공유 범위" value={scope} options={entries(scopes)} onChange={setScope} />}
    {type === 'review' && <><Select label="평점" value={rating} options={[5, 4, 3, 2, 1].map(value => ({ value, label: `${value}점` }))} onChange={setRating} /><Field label="이용 후기" multiline maxLength={1000} value={text} onChangeText={setText} /></>}
    {type === 'advance' && <Label>{nextStep(b)}로 기록하시겠습니까?</Label>}
    {type === 'accept' && <Label>{b.date} {b.time} · {b.patient}님의 동행을 수락하시겠습니까?</Label>}
    {type === 'confirmSettlement' && <Row label="매니저 정산액" value={money(b.workflow.report?.managerWon)} />}
    <Button title="확인" icon="checkmark-outline" onPress={() => onSave(type === 'accept' ? 'respond' : type, { reason, managerId, date, time, scope, rating, text, report: { ...report, minutes: Number(report.minutes) }, accept: type === 'accept', expectedStatus: b.status })} />
    <Button title="취소" secondary onPress={onCancel} />
  </>;
}

function Content({ state, account, tab, onOpen, onBook, onSwitch, onSignOut, onCheck, updateMessage }) {
  const [filter, setFilter] = useState('전체');
  const rows = visible(account, state.records), manager = account.role === 'MANAGER', operator = account.role === 'OPERATOR';
  const upcoming = rows.filter(b => !['동행 완료', '신청 취소'].includes(b.status)).sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));
  if (tab === 'home') return <>
    <Section><Label muted>{roles[account.role]}</Label><Heading>{account.name}님, 안녕하세요</Heading>
      {manager || operator ? <><Row label={manager ? '응답 대기' : '접수 대기'} value={`${rows.filter(b => b.status === (manager ? '배정 대기' : '접수 완료')).length}건`} /><Row label="진행 중" value={`${rows.filter(b => !beforeVisit.includes(b.status) && !['동행 완료', '신청 취소'].includes(b.status)).length}건`} /></> : <Button title={account.role === 'PATIENT' ? '병원 동행 신청' : '보호자 동행 신청'} icon="add-circle-outline" onPress={onBook} />}
    </Section><Section title={operator ? '처리할 예약' : manager ? '동행 일정' : '다가오는 동행'}>{upcoming.length ? upcoming.slice(0, 4).map(b => <BookingCard key={b.id} booking={b} onPress={() => onOpen(b.id)} />) : <Label muted>예정된 동행이 없습니다.</Label>}</Section>
  </>;
  if (tab === 'bookings') {
    const filtered = rows.filter(b => filter === '전체' || (filter === '대기' && ['접수 완료', '배정 대기'].includes(b.status)) || (filter === '진행' && !['접수 완료', '배정 대기', '동행 완료', '신청 취소'].includes(b.status)) || (filter === '완료' && b.status === '동행 완료') || (filter === '취소' && b.status === '신청 취소'));
    return <><Section><Heading>{titleForTab(tab, account)}</Heading>{!manager && !operator && <Button title="동행 신청" icon="add-outline" onPress={onBook} />}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 22, paddingVertical: 12 }}>{['전체', '대기', '진행', '완료', '취소'].map(value => <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: filter === value }} onPress={() => setFilter(value)} style={{ paddingVertical: 9, borderBottomWidth: 2, borderColor: filter === value ? colors.green : 'transparent' }}><Label style={{ color: filter === value ? colors.green : colors.muted, fontWeight: '600' }}>{value}</Label></Pressable>)}</ScrollView>
    </Section><Section>{filtered.length ? filtered.slice().reverse().map(b => <BookingCard key={b.id} booking={b} onPress={() => onOpen(b.id)} />) : <Label muted>예약 내역이 없습니다.</Label>}</Section></>;
  }
  if (tab === 'account') {
    const release = releaseInfo();
    return <><Section title="내 정보"><Row label="사용자" value={`${roles[account.role]} · ${account.name}`} /><Row label="연락처" value={account.phone} /><Button title="사용자 변경" secondary icon="people-outline" onPress={onSwitch} /><Button title="로그아웃" secondary icon="log-out-outline" onPress={onSignOut} /></Section>
      <Section title="앱 정보"><Row label="화면 버전" value={UI_RELEASE} /><Row label="앱 버전" value="0.6.0" /><Row label="업데이트" value={release.embedded ? '기본 버전' : '최신 화면 적용'} testID={`ota-update-${release.id}`} />
        <Button title="업데이트 확인" secondary icon="refresh-outline" onPress={onCheck} />{updateMessage ? <Label muted>{updateMessage}</Label> : null}</Section></>;
  }
  if (manager || operator) {
    const completed = rows.filter(b => b.status === '동행 완료' && b.workflow.report);
    const sum = confirmed => completed.filter(b => !!b.workflow.settlementAt === confirmed).reduce((total, b) => total + b.workflow.report.managerWon, 0);
    return <><Section title="정산 관리"><Row label="확정 대기" value={money(sum(false))} /><Row label="확정 완료" value={money(sum(true))} /></Section><Section>{completed.length ? completed.map(b => <BookingCard key={b.id} booking={b} onPress={() => onOpen(b.id)} />) : <Label muted>정산 내역이 없습니다.</Label>}</Section></>;
  }
  const notices = rows.flatMap(b => b.workflow.events.map((event, index) => ({ ...event, index, b }))).sort((a, b) => b.at.localeCompare(a.at));
  return <Section title="알림">{notices.length ? notices.map((event, index) => <Pressable key={`${event.b.id}-${event.index}`} onPress={() => onOpen(event.b.id)} style={s.card} accessibilityRole="button">
    <Label style={{ fontWeight: '600' }}>{account.id === event.b.requesterId || canReadReport(account, event.b) ? event.title : event.title.split(' · ')[0]}</Label><Label>{event.b.patient} · {event.b.hospital}</Label><Label muted style={{ fontSize: 14 }}>{new Date(event.at).toLocaleString('ko-KR')}</Label>
  </Pressable>) : <Label muted>새 알림이 없습니다.</Label>}</Section>;
}

function Shell() {
  const [state, setState] = useState(null), [loadError, setLoadError] = useState(''), [busy, setBusy] = useState(false);
  const [tab, setTab] = useState('home'), [detailId, setDetailId] = useState(null), [editor, setEditor] = useState(null), [dialog, setDialog] = useState(null);
  const [message, setMessage] = useState(''), [updateMessage, setUpdateMessage] = useState('');
  const saving = useRef(false), stateRef = useRef(null), scroll = useRef(null);
  const blocked = !state || state.signedOut || busy || !!editor || !!dialog || !!message;
  // Update the guard during render so a foreground callback cannot race an opened editor.
  setUpdateBlocked(blocked);
  const reload = async () => { setLoadError(''); try { const value = await loadState(); stateRef.current = value; setState(value); } catch (error) { setLoadError(error.message); } };
  useEffect(() => { reload(); return () => setUpdateBlocked(true); }, []);
  useEffect(() => subscribeUpdates(setUpdateMessage), []);
  useEffect(() => {
    const handle = AppState.addEventListener('change', value => { if (value === 'active') checkUpdate(); });
    const timer = setInterval(() => { if (AppState.currentState === 'active') checkUpdate(); }, 60000);
    return () => { handle.remove(); clearInterval(timer); };
  }, []);
  useEffect(() => { if (!blocked) checkUpdate(); }, [blocked]);
  const account = state && !state.signedOut ? accounts.find(a => a.id === state.accountId) : null;
  const detail = account && visible(account, state.records).find(b => b.id === detailId);
  const back = () => { if (dialog) Keyboard.dismiss(); else if (editor) setDialog({ type: 'discard' }); else if (detailId) setDetailId(null); else if (tab !== 'home') setTab('home'); else return false; return true; };
  useEffect(() => { if (editor) return; const listener = BackHandler.addEventListener('hardwareBackPress', back); return () => listener.remove(); }, [dialog, editor, detailId, tab]);
  const persist = async transform => {
    if (saving.current) return false;
    saving.current = true; setUpdateBlocked(true); setBusy(true);
    try { const next = transform(stateRef.current); await saveState(next); stateRef.current = next; setState(next); return true; }
    catch (error) { setMessage(error.message || '저장하지 못했습니다. 다시 시도해 주세요.'); return false; }
    finally { saving.current = false; setBusy(false); }
  };
  const signIn = async (id, pin) => { try { authenticate(id, pin); } catch (error) { setMessage(error.message); return; }
    if (await persist(current => ({ ...current, accountId: id, signedOut: false }))) { setDialog(null); setDetailId(null); setTab('home'); }
  };
  const act = async (action, args) => {
    const id = detail.id, revision = detail.revision;
    if (await persist(current => ({ ...current, records: mutate(current.records, account, action, { ...args, id, revision }) }))) setDialog(null);
  };
  const saveBooking = async draft => {
    const newId = `CG-${Crypto.randomUUID().slice(0, 8).toUpperCase()}`;
    const action = editor.original ? 'update' : 'create';
    const args = editor.original ? { id: editor.original.id, revision: editor.original.revision, draft } : { newId, draft };
    if (await persist(current => ({ ...current, records: mutate(current.records, account, action, args) }))) { setEditor(null); setDetailId(args.id || newId); setTab('bookings'); }
  };
  const title = editor ? (editor.original ? '신청 내용 수정' : '동행 신청') : detail ? '예약 상세' : account ? titleForTab(tab, account) : '케어동행';
  return <SafeAreaView style={styles.root} edges={['top', 'bottom']}><StatusBar style="dark" /><KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
    <View style={styles.header}>
      {(editor || detail) && <Pressable accessibilityRole="button" accessibilityLabel="뒤로" style={styles.iconButton} onPress={back}><Ionicons name="arrow-back" size={25} color={colors.ink} /></Pressable>}
      <Label style={{ fontSize: 20, fontWeight: '700', flex: 1 }}>{title === '홈' ? '케어동행' : title}</Label>
      {account && !editor && <Pressable accessibilityRole="button" accessibilityLabel="사용자 선택" style={styles.iconButton} onPress={() => setDialog({ type: 'account' })}><Ionicons name="person-circle-outline" size={28} color={colors.green} /></Pressable>}
    </View>
    {!state ? <View style={styles.center}>{loadError ? <><Label>{loadError}</Label><Button title="다시 시도" onPress={reload} /></> : <><ActivityIndicator size="large" color={colors.green} /><Label>정보를 불러오고 있습니다.</Label></>}</View> : !account ? <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}><Section title="케어동행"><AccountForm current={state.accountId} onSubmit={signIn} /></Section></ScrollView> : editor ?
      <BookingForm account={account} original={editor.original} onSave={saveBooking} onCancel={() => setDialog({ type: 'discard' })} /> :
      <ScrollView ref={scroll} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content} key={`${tab}-${detailId || ''}-${account.id}`}>
        {detail ? <Detail b={detail} account={account} onAction={act} onEdit={() => setEditor({ original: detail })} onDialog={type => setDialog({ type })} /> : <Content state={state} account={account} tab={tab} onOpen={setDetailId} onBook={() => setEditor({})} onSwitch={() => setDialog({ type: 'account' })} onSignOut={() => setDialog({ type: 'signOut' })} onCheck={() => checkUpdate(true)} updateMessage={updateMessage} />}
      </ScrollView>}
    {account && !editor && <View style={styles.tabs}>{[['home', 'home-outline'], ['bookings', 'calendar-outline'], ['activity', ['MANAGER', 'OPERATOR'].includes(account.role) ? 'wallet-outline' : 'notifications-outline'], ['account', 'person-outline']].map(([key, icon]) => <Pressable key={key} accessibilityRole="tab" accessibilityLabel={titleForTab(key, account)} accessibilityState={{ selected: tab === key && !detail }} style={styles.tab} onPress={() => { setTab(key); setDetailId(null); }}>
      <Ionicons name={icon} size={22} color={tab === key ? colors.green : colors.muted} /><Label style={{ fontSize: 13, lineHeight: 20, textAlign: 'center', color: tab === key ? colors.green : colors.muted }}>{key === 'activity' ? (['MANAGER', 'OPERATOR'].includes(account.role) ? '정산' : '알림') : key === 'bookings' ? (account.role === 'MANAGER' ? '일정' : '예약') : key === 'account' ? '내 정보' : '홈'}</Label>
    </Pressable>)}</View>}
  </KeyboardAvoidingView>
    <Modal visible={!!dialog} animationType="fade" transparent onRequestClose={() => Keyboard.dismiss()}><KeyboardAvoidingView style={styles.scrim} behavior="padding"><View style={styles.modal}><ScrollView key={dialog ? `${dialog.type}-${detailId || ''}` : 'closed'} keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 22 }}>
      {dialog?.type === 'account' ? <AccountForm current={state?.accountId} switching onSubmit={signIn} onCancel={() => setDialog(null)} /> : dialog?.type === 'discard' ? <><Heading>작성을 취소하시겠습니까?</Heading><Button title="작성 계속" onPress={() => setDialog(null)} /><Button title="작성 취소" secondary onPress={() => { setEditor(null); setDialog(null); }} /></> : dialog?.type === 'signOut' ? <><Heading>로그아웃하시겠습니까?</Heading><Button title="로그아웃" onPress={async () => { if (await persist(current => ({ ...current, signedOut: true }))) { setDialog(null); setDetailId(null); setTab('home'); } }} /><Button title="취소" secondary onPress={() => setDialog(null)} /></> : detail && dialog ? <ActionForm key={`${detail.id}-${dialog.type}`} type={dialog.type} b={detail} onSave={act} onCancel={() => setDialog(null)} /> : null}
    </ScrollView></View></KeyboardAvoidingView></Modal>
    <Modal visible={!!message} transparent onRequestClose={() => setMessage('')}><View style={styles.scrim}><View style={[styles.modal, { padding: 22 }]}><Heading>확인해 주세요</Heading><Label>{message}</Label><Button title="확인" onPress={() => setMessage('')} /></View></View></Modal>
    {busy && <View style={styles.busy} accessibilityViewIsModal><ActivityIndicator size="large" color={colors.green} /><Label>저장하고 있습니다.</Label></View>}
  </SafeAreaView>;
}
export default function App() { return <SafeAreaProvider><Shell /></SafeAreaProvider>; }
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background }, header: { flexDirection: 'row', alignItems: 'center', minHeight: 58, paddingHorizontal: 16, borderBottomWidth: 1, borderColor: colors.line, gap: 8 },
  content: { paddingHorizontal: 20, paddingBottom: 28, flexGrow: 1 }, horizontal: { flexDirection: 'row', gap: 10, marginBottom: 16 },
  iconButton: { width: 46, height: 46, alignItems: 'center', justifyContent: 'center' }, stepper: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-start' },
  tabs: { flexDirection: 'row', borderTopWidth: 1, borderColor: colors.line, backgroundColor: 'white', minHeight: 66, alignItems: 'stretch' }, tab: { flex: 1, paddingVertical: 10, alignItems: 'center', justifyContent: 'center', gap: 3 },
  center: { flex: 1, padding: 24, gap: 18, justifyContent: 'center', alignItems: 'center' },
  timeline: { flexDirection: 'row', gap: 12, paddingBottom: 20 }, timelineDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.blue, marginTop: 9 },
  scrim: { flex: 1, backgroundColor: '#00000066', paddingHorizontal: 20, paddingVertical: 50, justifyContent: 'center' }, modal: { backgroundColor: colors.background, maxHeight: '100%', borderRadius: 8, width: '100%', maxWidth: 560, alignSelf: 'center' },
  busy: { ...StyleSheet.absoluteFillObject, zIndex: 20, backgroundColor: '#F8FBFAEE', justifyContent: 'center', alignItems: 'center', gap: 16 },
});
