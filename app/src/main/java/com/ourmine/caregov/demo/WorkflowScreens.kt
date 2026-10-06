package com.ourmine.caregov.demo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun eventTime(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M.d HH:mm"))
}.getOrDefault(value)

@Composable
internal fun WorkflowSection(store: ServiceStore, account: ServiceAccount, booking: Booking, onChanged: () -> Unit) {
    var error by remember(booking.id, booking.revision) { mutableStateOf<String?>(null) }
    fun perform(action: () -> Unit) {
        runCatching(action).onSuccess { onChanged() }.onFailure { error = it.message ?: "처리하지 못했습니다. 다시 확인해 주세요." }
    }
    val reportVisible = store.canReadReport(account, booking)
    HorizontalDivider(); SectionTitle("동행 진행")
    if (booking.workflow.events.isEmpty()) InfoLine("현재 상태", booking.status)
    booking.workflow.events.forEach { item ->
        // Progress-only recipients must not receive cancellation/rejection notes.
        val title = if (account.role == DemoRole.GUARDIAN && !reportVisible) item.title.substringBefore(" · ") else item.title
        InfoLine(eventTime(item.at), "$title · ${item.actor}")
    }
    if (account.role == DemoRole.OPERATOR && booking.status in VisitSteps.beforeVisit) {
        OperatorActions(store, account, booking, ::perform)
    }
    if (account.role == DemoRole.MANAGER && booking.manager == account.name) {
        ManagerActions(store, account, booking, ::perform)
    }
    HorizontalDivider(); SectionTitle("동행 결과")
    val report = booking.workflow.report
    when {
        booking.status == "신청 취소" -> Text("취소된 신청입니다.")
        !reportVisible -> Text("결과 정보가 공유되지 않았습니다.")
        report == null -> Text("아직 동행 결과가 등록되지 않았습니다.")
        else -> {
            InfoLine("동행 내용", report.summary)
            InfoLine("병원 안내 사항", report.instructions.ifBlank { "등록된 내용이 없습니다." })
            InfoLine("약 수령 및 안내", report.medication.ifBlank { "등록된 내용이 없습니다." })
            InfoLine("다음 방문", report.nextVisit.ifBlank { "등록된 일정이 없습니다." })
            InfoLine("이용 시간", "${report.minutes}분")
            InfoLine("서비스 요금", moneyLabel(report.feeWon))
            InfoLine("등록 일시", eventTime(report.submittedAt))
        }
    }
    if (report != null && account.role in setOf(DemoRole.MANAGER, DemoRole.OPERATOR)) {
        HorizontalDivider(); SectionTitle("정산 내역")
        InfoLine("매니저 정산액", moneyLabel(report.managerWon))
        InfoLine("운영 수수료", moneyLabel(report.feeWon - report.managerWon))
        InfoLine("정산 상태", if (booking.workflow.settlementAt.isBlank()) "확정 대기" else "정산 확정")
        if (booking.workflow.settlementAt.isNotBlank()) InfoLine("확정 일시", eventTime(booking.workflow.settlementAt))
        if (account.role == DemoRole.OPERATOR && booking.workflow.settlementAt.isBlank()) {
            var confirm by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("정산 확정") }
            if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("정산 내역을 확정할까요?") },
                text = { Text("${booking.manager} · ${moneyLabel(report.managerWon)}") },
                confirmButton = { TextButton(onClick = { confirm = false; perform { store.confirmSettlement(account, booking.id, booking.revision) } }) { Text("확정") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("돌아가기") } })
        }
    }
    if (booking.status == "동행 완료" && reportVisible) {
        HorizontalDivider(); SectionTitle("이용 평가")
        if (booking.workflow.rating > 0) {
            InfoLine("평점", "${booking.workflow.rating} / 5")
            if (booking.workflow.review.isNotBlank()) Text(booking.workflow.review)
        } else if (account.role in setOf(DemoRole.PATIENT, DemoRole.GUARDIAN)) {
            ReviewForm { rating, review -> perform { store.review(account, booking.id, booking.revision, rating, review) } }
        } else Text("아직 등록된 평가가 없습니다.")
    }
    if (account.role in setOf(DemoRole.PATIENT, DemoRole.GUARDIAN) && booking.status != "신청 취소" &&
        (booking.requesterId == account.id || account.role == DemoRole.PATIENT && booking.patientId == account.id)) {
        var sharingDialog by remember { mutableStateOf(false) }
        var scope by remember(booking.options.sharing) { mutableStateOf(booking.options.sharing) }
        OutlinedButton(onClick = { sharingDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("공유 범위 변경") }
        if (sharingDialog) AlertDialog(onDismissRequest = { sharingDialog = false }, title = { Text("보호자 정보 공유") },
            text = { Column { SharingScope.entries.forEach { option -> ChoiceRadio(option.label, scope == option) { scope = option } } } },
            confirmButton = { TextButton(onClick = { sharingDialog = false; perform { store.changeSharing(account, booking.id, booking.revision, scope) } }) { Text("공유 설정 저장") } },
            dismissButton = { TextButton(onClick = { sharingDialog = false }) { Text("돌아가기") } })
    }
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun OperatorActions(store: ServiceStore, account: ServiceAccount, booking: Booking, perform: (() -> Unit) -> Unit) {
    var dialog by rememberSaveable(booking.id) { mutableStateOf("") }
    OutlinedButton(onClick = { dialog = "assign" }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(if (booking.manager.isBlank()) "매니저 배정" else "매니저 변경")
    }
    OutlinedButton(onClick = { dialog = "schedule" }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Outlined.DateRange, null); Spacer(Modifier.width(8.dp)); Text("일정 변경")
    }
    TextButton(onClick = { dialog = "cancel" }, modifier = Modifier.fillMaxWidth()) { Text("예약 취소") }
    if (dialog.isNotBlank()) OperatorDialog(store, account, booking, dialog, onDismiss = { dialog = "" }, onSaved = { dialog = ""; perform {} })
}

@Composable
private fun OperatorDialog(store: ServiceStore, account: ServiceAccount, booking: Booking, kind: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val managers = store.accounts.filter { it.role == DemoRole.MANAGER && it.name != booking.manager }
    var managerId by rememberSaveable { mutableStateOf(managers.firstOrNull()?.id ?: "") }
    var date by rememberSaveable { mutableStateOf(booking.date) }
    var time by rememberSaveable { mutableStateOf(booking.time) }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(when (kind) { "assign" -> "매니저 선택"; "schedule" -> "방문 일정 변경"; else -> "예약 취소" }) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kind == "assign") managers.forEach { manager -> ChoiceRadio(manager.name, manager.id == managerId) { managerId = manager.id } }
            else {
                if (kind == "schedule") VisitDateControls(date, time, { date = it }, { time = it })
                OutlinedTextField(reason, { reason = it.take(200) }, label = { Text(if (kind == "schedule") "변경 사유" else "취소 사유") }, modifier = Modifier.fillMaxWidth())
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching {
                when (kind) {
                    "assign" -> store.assign(account, booking.id, booking.revision, managerId)
                    "schedule" -> store.reschedule(account, booking.id, booking.revision, date, time, reason)
                    else -> store.operatorCancel(account, booking.id, booking.revision, reason)
                }
            }.onSuccess { onSaved() }.onFailure { error = it.message ?: "처리하지 못했습니다." }
        }, enabled = if (kind == "assign") managerId.isNotBlank() else reason.isNotBlank()) {
            Text(when (kind) { "assign" -> "배정"; "schedule" -> "변경 저장"; else -> "취소 확정" })
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("돌아가기") } })
}

@Composable
private fun VisitDateControls(date: String, time: String, onDate: (String) -> Unit, onTime: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val selected = LocalDate.parse(date)
        DatePickerDialog(context, { _, y, m, d -> onDate(LocalDate.of(y, m + 1, d).toString()) }, selected.year, selected.monthValue - 1, selected.dayOfMonth)
            .apply { datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()
    }, modifier = Modifier.fillMaxWidth()) { Text("방문 날짜 $date") }
    OutlinedButton(onClick = {
        val selected = LocalTime.parse(time)
        TimePickerDialog(context, { _, h, m -> onTime(LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("HH:mm"))) }, selected.hour, selected.minute, true).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("방문 시간 $time") }
}

@Composable
private fun ManagerActions(store: ServiceStore, account: ServiceAccount, booking: Booking, perform: (() -> Unit) -> Unit) {
    var confirmation by rememberSaveable(booking.id) { mutableStateOf("") }
    var reason by rememberSaveable(booking.id) { mutableStateOf("") }
    if (booking.status == "배정 대기") {
        Button(onClick = { confirmation = "accept" }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("배정 수락") }
        TextButton(onClick = { confirmation = "decline" }, modifier = Modifier.fillMaxWidth()) { Text("배정 거절") }
    }
    VisitSteps.next(booking)?.let { next ->
        Button(onClick = { confirmation = "progress" }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(if (booking.status == "예약 확정") "동행 시작" else "$next 확인")
        }
    }
    if (confirmation.isNotBlank()) {
        val next = VisitSteps.next(booking)
        AlertDialog(onDismissRequest = { confirmation = "" }, title = { Text(when (confirmation) {
            "accept" -> "배정을 수락할까요?"; "decline" -> "배정을 거절할까요?"; else -> "$next 상태로 변경할까요?"
        }) }, text = {
            if (confirmation == "decline") OutlinedTextField(reason, { reason = it.take(200) }, label = { Text("거절 사유") })
            else Text("${booking.patient} · ${booking.hospital}")
        }, confirmButton = { TextButton(onClick = {
            val action = confirmation; confirmation = ""
            perform {
                if (action == "progress") store.advance(account, booking.id, booking.revision, booking.status)
                else store.respond(account, booking.id, booking.revision, action == "accept", reason)
            }
        }, enabled = confirmation != "decline" || reason.isNotBlank()) { Text("확인") } },
            dismissButton = { TextButton(onClick = { confirmation = "" }) { Text("돌아가기") } })
    }
    if (booking.status == "귀가 완료") {
        HorizontalDivider(); SectionTitle("결과 등록")
        ReportForm(booking) { report -> perform { store.complete(account, booking.id, booking.revision, report) } }
    }
}

@Composable
private fun ReportForm(booking: Booking, onSubmit: (VisitReport) -> Unit) {
    var summary by rememberSaveable(booking.id) { mutableStateOf("") }
    var instructions by rememberSaveable(booking.id) { mutableStateOf("") }
    var medication by rememberSaveable(booking.id) { mutableStateOf("") }
    var next by rememberSaveable(booking.id) { mutableStateOf("") }
    var minutes by rememberSaveable(booking.id) { mutableStateOf((booking.options.hours * 60).toString()) }
    var confirm by remember { mutableStateOf(false) }
    OutlinedTextField(summary, { summary = it.take(2000) }, label = { Text("동행 내용") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    OutlinedTextField(instructions, { instructions = it.take(1000) }, label = { Text("병원 안내 사항 (선택)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    OutlinedTextField(medication, { medication = it.take(1000) }, label = { Text("약 수령 및 안내 (선택)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(next, { next = it.take(1000) }, label = { Text("다음 방문 (선택)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("실제 이용 시간 (분)") }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    val duration = minutes.toIntOrNull()
    val fee = duration?.takeIf { it in 1..480 }?.let { SettlementPricing.fee(it) }
    InfoLine("서비스 요금", fee?.let { moneyLabel(it) } ?: "이용 시간을 확인해 주세요.")
    Text("최소 1시간 · 이후 30분 단위", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { confirm = true }, enabled = summary.isNotBlank() && fee != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("동행 완료 및 결과 등록") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("동행 결과를 등록할까요?") },
        text = { Text("${booking.patient} · ${minutes}분 · ${moneyLabel(fee)}") },
        confirmButton = { TextButton(onClick = { confirm = false; onSubmit(VisitReport(summary, instructions, medication, next, requireNotNull(duration))) }) { Text("결과 등록") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("돌아가기") } })
}

@Composable
private fun ReviewForm(onSubmit: (Int, String) -> Unit) {
    var rating by rememberSaveable { mutableStateOf(5) }
    var review by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { score -> FilterChip(selected = rating == score, onClick = { rating = score }, label = { Text("${score}점") }) }
    }
    OutlinedTextField(review, { review = it.take(1000) }, label = { Text("이용 후기 (선택)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    OutlinedButton(onClick = { onSubmit(rating, review) }, modifier = Modifier.fillMaxWidth()) { Text("평가 등록") }
}

@Composable
internal fun SettlementScreen(account: ServiceAccount, records: List<Booking>, onOpen: (Booking) -> Unit) {
    var confirmed by rememberSaveable(account.id) { mutableStateOf(false) }
    val completed = records.filter { it.status == "동행 완료" && it.workflow.report != null }
    SectionTitle(if (account.role == DemoRole.OPERATOR) "정산 관리" else "내 정산")
    InfoLine("확정 대기", moneyLabel(completed.filter { it.workflow.settlementAt.isBlank() }.sumOf { it.workflow.report!!.managerWon }))
    InfoLine("정산 확정", moneyLabel(completed.filter { it.workflow.settlementAt.isNotBlank() }.sumOf { it.workflow.report!!.managerWon }))
    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!confirmed, { confirmed = false }, label = { Text("확정 대기") })
        FilterChip(confirmed, { confirmed = true }, label = { Text("정산 확정") })
    }
    val filtered = completed.filter { it.workflow.settlementAt.isNotBlank() == confirmed }
    if (filtered.isEmpty()) Text("해당 정산 내역이 없습니다.")
    filtered.forEach { booking ->
        OutlinedCard(onClick = { onOpen(booking) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${booking.date} · ${booking.patient}", style = MaterialTheme.typography.titleMedium)
                Text("${booking.manager} · ${booking.hospital}")
                Text(moneyLabel(booking.workflow.report!!.managerWon), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun NotificationScreen(account: ServiceAccount, records: List<Booking>, onOpen: (Booking) -> Unit) {
    SectionTitle("알림")
    val items = records.flatMap { booking -> booking.workflow.events.map { booking to it } }.sortedByDescending { it.second.at }
    if (items.isEmpty()) Text("새로운 알림이 없습니다.")
    items.forEach { (booking, event) ->
        val title = if (account.role == DemoRole.GUARDIAN && booking.options.sharing != SharingScope.RESULTS) event.title.substringBefore(" · ") else event.title
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text("${booking.patient} · ${booking.hospital}\n${eventTime(event.at)}") },
            modifier = Modifier.fillMaxWidth(), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            trailingContent = { IconButton(onClick = { onOpen(booking) }) { Icon(Icons.Outlined.DateRange, "예약 상세 보기") } })
        HorizontalDivider()
    }
}
