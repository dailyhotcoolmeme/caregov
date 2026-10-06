package com.ourmine.caregov.demo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BookingForm(account: ServiceAccount, initial: Booking?, step: Int, onStep: (Int) -> Unit,
    modifier: Modifier, onSubmit: (BookingDraft) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val proxy = account.role == DemoRole.GUARDIAN
    var linked by rememberSaveable { mutableStateOf(proxy && (initial == null || initial.patientId == "patient")) }
    var patient by rememberSaveable { mutableStateOf(initial?.patient ?: if (proxy) "김영희" else account.name) }
    var patientPhone by rememberSaveable { mutableStateOf(initial?.options?.patientPhone?.ifBlank { null } ?: if (proxy) "01000000001" else account.phone) }
    var relationship by rememberSaveable { mutableStateOf(initial?.options?.relationship?.takeIf { it != "본인" } ?: "어머니") }
    var guardianPhone by rememberSaveable { mutableStateOf(initial?.options?.guardianPhone?.ifBlank { null } ?: if (proxy) account.phone else "") }
    var sharing by rememberSaveable { mutableStateOf(initial?.options?.sharing?.name ?: SharingScope.PROGRESS.name) }
    var service by rememberSaveable { mutableStateOf(initial?.options?.service?.name ?: VisitService.OUTPATIENT.name) }
    var hours by rememberSaveable { mutableStateOf(initial?.options?.hours ?: 2) }
    var hospital by rememberSaveable { mutableStateOf(initial?.hospital ?: "") }
    var department by rememberSaveable { mutableStateOf(initial?.department ?: "") }
    var date by rememberSaveable { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(1).toString()) }
    var time by rememberSaveable { mutableStateOf(initial?.time ?: "10:00") }
    var meeting by rememberSaveable { mutableStateOf(initial?.meeting ?: "") }
    var support by rememberSaveable { mutableStateOf(initial?.support ?: "도보 이동") }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var relationshipsOpen by remember { mutableStateOf(false) }
    val draft = BookingDraft(patient, hospital, department, date, time, meeting, support, note,
        RequestOptions(patientPhone, if (proxy) relationship else "본인", guardianPhone, SharingScope.valueOf(sharing),
            VisitService.valueOf(service), hours, if (proxy && linked) "patient" else if (!proxy) account.id else "", consent,
            ServicePricing.estimate(hours)))
    fun next() {
        focus.clearFocus(); keyboard?.hide()
        error = when {
            step == 0 && (patient.isBlank() || !validPhone(patientPhone) ||
                (proxy && !validPhone(guardianPhone)) || (guardianPhone.isNotBlank() && !validPhone(guardianPhone))) -> "이름과 연락처를 확인해 주세요."
            step == 1 && (hospital.isBlank() || department.isBlank() || meeting.isBlank()) -> "병원과 만날 장소를 입력해 주세요."
            step == 1 && !LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).isAfter(LocalDateTime.now()) -> "방문 일시는 현재 이후로 선택해 주세요."
            else -> null
        }
        if (error == null) onStep(step + 1)
    }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(key(step) { rememberScrollState() }).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("${step + 1} / 3", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        SectionTitle(if (step == 0) { if (proxy) "가족 동행 신청" else "본인 동행 신청" }
            else if (step == 1) "병원 방문" else "신청 내용 확인")
        when (step) {
            0 -> {
                if (proxy) {
                    ChoiceRadio("김영희 · 어머니", linked) { linked = true; patient = "김영희"; patientPhone = "01000000001"; relationship = "어머니" }
                    ChoiceRadio("다른 이용자", !linked) { linked = false; patient = ""; patientPhone = ""; relationship = "기타" }
                }
                OutlinedTextField(patient, { patient = it.take(120) }, label = { Text("이용자 이름") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = !proxy || linked)
                OutlinedTextField(patientPhone, { patientPhone = it }, label = { Text("이용자 연락처") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                if (proxy) Box {
                    OutlinedButton(onClick = { relationshipsOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("이용자와의 관계 · $relationship", Modifier.weight(1f)); Icon(Icons.Outlined.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = relationshipsOpen, onDismissRequest = { relationshipsOpen = false }) {
                        listOf("어머니", "아버지", "배우자", "자녀", "친척", "기타").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { relationship = option; relationshipsOpen = false })
                        }
                    }
                }
                OutlinedTextField(guardianPhone, { guardianPhone = it }, label = { Text(if (proxy) "보호자 연락처" else "보호자 연락처 (선택)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                HorizontalDivider(); SectionTitle("서비스")
                VisitService.entries.forEach { option -> ChoiceRadio(option.label, service == option.name) { service = option.name } }
                SectionTitle("예상 이용 시간")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { hours-- }, enabled = hours > ServicePricing.minimumHours) { Icon(Icons.Outlined.KeyboardArrowDown, "이용 시간 줄이기") }
                    Text("${hours}시간", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { hours++ }, enabled = hours < 8) { Icon(Icons.Outlined.KeyboardArrowUp, "이용 시간 늘리기") }
                }
                PriceSummary(hours)
                HorizontalDivider(); SectionTitle("보호자 정보 공유")
                SharingScope.entries.forEach { option -> ChoiceRadio(option.label, sharing == option.name) { sharing = option.name } }
            }
            1 -> {
                OutlinedTextField(hospital, { hospital = it.take(120) }, label = { Text("병원 이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(department, { department = it.take(120) }, label = { Text("진료과") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = {
                    val selected = LocalDate.parse(date)
                    DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() },
                        selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                        datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.DateRange, null); Spacer(Modifier.width(8.dp)); Text("방문 날짜  $date")
                }
                OutlinedButton(onClick = {
                    val selected = LocalTime.parse(time)
                    TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("HH:mm")) },
                        selected.hour, selected.minute, true).show()
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("방문 시간  $time") }
                HorizontalDivider(); SectionTitle("만날 장소와 이동 지원")
                OutlinedTextField(meeting, { meeting = it.take(120) }, label = { Text("만날 장소") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                listOf("도보 이동", "보행 보조", "휠체어 이용").forEach { option -> ChoiceRadio(option, support == option) { support = option } }
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("요청사항 (선택)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
            2 -> {
                RequestSummary(draft)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().toggleable(consent, role = Role.Checkbox, onValueChange = { consent = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = null)
                    Text(if (proxy) "이용자의 동의를 받고 동행을 신청합니다." else "동행 신청을 위한 정보 제공에 동의합니다.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (step < 2) Button(onClick = ::next, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("다음") }
        else Button(onClick = {
            if (!submitted) {
                focus.clearFocus(); keyboard?.hide(); submitted = true
                runCatching { onSubmit(draft) }.onFailure { submitted = false; error = it.message ?: "신청을 저장하지 못했습니다." }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), enabled = consent && !submitted) {
            Text(if (initial == null) "동행 신청하기" else "변경 내용 저장")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ChoiceRadio(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null); Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun moneyLabel(amount: Long?) = amount?.let { NumberFormat.getNumberInstance(Locale.KOREA).format(it) + "원" } ?: "견적 확인 후 확정"

@Composable
internal fun PriceSummary(hours: Int, amount: Long? = ServicePricing.estimate(hours)) {
    SectionTitle("예상 요금")
    Text(moneyLabel(amount), style = MaterialTheme.typography.titleLarge)
    Text("${hours}시간 · 교통비·진료비 별도", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun RequestSummary(draft: BookingDraft) {
    SummaryLine("이용자", "${draft.patient} · ${draft.options.relationship}")
    SummaryLine("이용자 연락처", draft.options.patientPhone)
    if (draft.options.guardianPhone.isNotBlank()) SummaryLine("보호자 연락처", draft.options.guardianPhone)
    SummaryLine("서비스", draft.options.service.label)
    SummaryLine("방문 일시", "${draft.date} ${draft.time}")
    SummaryLine("병원", "${draft.hospital} · ${draft.department}")
    SummaryLine("만날 장소", draft.meeting)
    SummaryLine("이동 지원", draft.support)
    SummaryLine("정보 공유", draft.options.sharing.label)
    if (draft.note.isNotBlank()) SummaryLine("요청사항", draft.note)
    PriceSummary(draft.options.hours, draft.options.estimatedWon)
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
