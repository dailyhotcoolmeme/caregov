package com.ourmine.caregov.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ourmine.caregov.demo.updates.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregovApp(updater: UpdateViewModel) {
    val context = LocalContext.current
    val store = remember { ServiceStore(context) }
    var account by remember { mutableStateOf(store.account()) }
    var bookings by remember { mutableStateOf(store.bookings()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var applying by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var formStep by rememberSaveable { mutableStateOf(0) }
    var showUpdate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(updater.state.phase) {
        if (updater.state.phase == UpdatePhase.AVAILABLE) showUpdate = true
    }
    BackHandler(enabled = detail != null || applying || tab != 0) {
        if (applying && formStep > 0) formStep--
        else if (applying) { applying = false; editing = false }
        else if (detail != null) detail = null else tab = 0
    }
    val current = account
    if (current == null) {
        LoginScreen { phone, pin -> store.signIn(phone, pin)?.also { account = it; tab = 0; detail = null } }
    } else {
        val visible = store.visible(current, bookings).sortedWith(compareBy({ it.date }, { it.time }))
        val selected = visible.firstOrNull { it.id == detail }
        val labels = when (current.role) {
            DemoRole.MANAGER -> listOf("홈", "내 일정", "내 정보", "내 정산")
            DemoRole.OPERATOR -> listOf("홈", "예약 관리", "내 정보", "정산 관리")
            else -> listOf("홈", "예약 내역", "내 정보", "알림")
        }
        Scaffold(
            topBar = { TopAppBar(
                title = { Text(if (applying) { if (editing) "신청 수정" else "동행 신청" } else if (selected != null) "예약 상세" else "병원동행") },
                navigationIcon = {
                    if (applying || selected != null) IconButton(onClick = {
                        if (applying && formStep > 0) formStep--
                        else if (applying) { applying = false; editing = false }
                        else detail = null
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로")
                    }
                },
                actions = { if (!applying && selected == null) Text(current.role.label, Modifier.padding(end = 20.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            ) },
            bottomBar = { if (!applying && selected == null) NavigationBar(containerColor = Color.White) {
                val financial = current.role in setOf(DemoRole.MANAGER, DemoRole.OPERATOR)
                val compactLabels = listOf("홈", if (current.role == DemoRole.MANAGER) "일정" else "예약", "내정보", if (financial) "정산" else "알림")
                listOf(Icons.Outlined.Home, Icons.Outlined.DateRange, Icons.Outlined.Person,
                    if (financial) Icons.Outlined.CheckCircle else Icons.Outlined.Notifications).forEachIndexed { index, icon ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, modifier = Modifier.semantics { contentDescription = labels[index] },
                        icon = { Icon(icon, null) }, label = { Text(compactLabels[index], maxLines = 1, style = MaterialTheme.typography.labelSmall) })
                }
            } },
        ) { insets ->
            if (applying) BookingForm(current, if (editing) selected else null, formStep, { formStep = it }, Modifier.padding(insets)) { draft ->
                val added = if (editing) store.update(current, requireNotNull(selected).id, selected.revision, draft) else store.create(current, draft)
                bookings = store.bookings(); applying = false; editing = false; formStep = 0; detail = added.id
            } else Column(Modifier.fillMaxSize().padding(insets).verticalScroll(key(current.id, tab, detail) { rememberScrollState() })
                .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                when {
                    selected != null -> BookingDetails(store, current, selected, store.canModify(current, selected),
                        onChanged = { bookings = store.bookings() },
                        onEdit = { editing = true; formStep = 0; applying = true },
                        onCancel = { reason -> store.cancel(current, selected.id, selected.revision, reason); bookings = store.bookings() })
                    tab == 2 -> AccountScreen(current, onUpdate = {
                        showUpdate = true
                        if (updater.state.phase != UpdatePhase.READY && updater.state.phase != UpdatePhase.DOWNLOADING) updater.check()
                    }, onSignOut = { store.signOut(); account = null })
                    tab == 3 -> if (current.role in setOf(DemoRole.MANAGER, DemoRole.OPERATOR))
                        SettlementScreen(current, visible) { detail = it.id }
                    else NotificationScreen(current, visible) { detail = it.id }
                    tab == 1 -> {
                        var filter by rememberSaveable(current.id) { mutableStateOf("전체") }
                        SectionTitle(labels[1], "${visible.size}건")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("전체", "대기", "진행", "완료", "취소").forEach { label ->
                                FilterChip(filter == label, { filter = label }, label = { Text(label) })
                            }
                        }
                        val filtered = visible.filter { when (filter) {
                            "대기" -> it.status in setOf("접수 완료", "배정 대기", "예약 확정")
                            "진행" -> it.status !in VisitSteps.beforeVisit + setOf("동행 완료", "신청 취소")
                            "완료" -> it.status == "동행 완료"
                            "취소" -> it.status == "신청 취소"
                            else -> true
                        } }
                        if (filtered.isEmpty()) Text("해당 예약 내역이 없습니다.")
                        filtered.forEach { BookingCard(it) { detail = it.id } }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Text("${current.name}님,\n안녕하세요", style = MaterialTheme.typography.headlineSmall)
                            Text(when (current.role) {
                                DemoRole.PATIENT -> "병원 가는 날,\n함께하겠습니다."
                                DemoRole.GUARDIAN -> "가족의 병원 방문을 함께 챙깁니다."
                                DemoRole.MANAGER -> "오늘의 동행 일정을 확인하세요."
                                DemoRole.OPERATOR -> "예약과 배정 현황을 확인하세요."
                            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current.role == DemoRole.PATIENT || current.role == DemoRole.GUARDIAN) Button(
                            onClick = { editing = false; formStep = 0; applying = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        ) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(10.dp)); Text("동행 신청") }
                        if (current.role == DemoRole.OPERATOR) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Statistic("전체 예약", visible.size.toString(), Modifier.weight(1f))
                            Statistic("배정 대기", visible.count { it.manager.isEmpty() && it.status == "접수 완료" }.toString(), Modifier.weight(1f))
                        }
                        if (current.role == DemoRole.OPERATOR) TextButton(onClick = { tab = 1 }) { Text("접수·배정 내역 보기") }
                        if (current.role == DemoRole.MANAGER) InfoLine("수락 대기", "${visible.count { it.status == "배정 대기" }}건")
                        val next = visible.firstOrNull { it.date >= LocalDate.now().toString() && it.status !in listOf("동행 완료", "신청 취소") }
                        SectionTitle(if (current.role == DemoRole.MANAGER) "다가오는 일정" else "다가오는 동행")
                        if (next != null) BookingCard(next) { detail = next.id } else Text("예정된 동행이 없습니다.")
                        HorizontalDivider()
                        if (current.role == DemoRole.GUARDIAN) {
                            SectionTitle("함께 돌보는 가족"); InfoLine("어머니", "김영희")
                        } else if (current.role == DemoRole.PATIENT) {
                            SectionTitle("동행 서비스"); InfoLine("병원 방문", "접수부터 귀가까지 함께합니다.")
                        }
                    }
                }
            }
        }
    }
    if (showUpdate) UpdateDialog(updater, onDismiss = { showUpdate = false })
}

@Composable
private fun BookingCard(booking: Booking, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "예약 ${booking.id}" },
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(booking.date.drop(5).replace("-", ".") + " · " + booking.time,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusLabel(booking.status)
            }
            Text("${booking.hospital} · ${booking.department}", style = MaterialTheme.typography.titleLarge)
            Text("${booking.patient}님 동행", style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            InfoLine("만날 장소", booking.meeting)
            InfoLine("담당 매니저", booking.manager.ifBlank { "배정 대기" })
            Text("예약 상세 보기", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BookingDetails(store: ServiceStore, account: ServiceAccount, booking: Booking, editable: Boolean,
    onChanged: () -> Unit, onEdit: () -> Unit, onCancel: (String) -> Unit) {
    var confirmCancel by rememberSaveable(booking.id) { mutableStateOf(false) }
    var cancelReason by rememberSaveable(booking.id) { mutableStateOf("일정 변경") }
    var cancelError by remember { mutableStateOf<String?>(null) }
    StatusLabel(booking.status)
    Text("${booking.hospital}\n${booking.department}", style = MaterialTheme.typography.headlineSmall)
    Text("예약번호 ${booking.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(); SectionTitle("동행 정보")
    InfoLine("이용자", booking.patient)
    val privateDetails = account.role != DemoRole.GUARDIAN || account.id == booking.requesterId || store.canReadReport(account, booking)
    if (privateDetails && booking.options.patientPhone.isNotBlank()) InfoLine("이용자 연락처", booking.options.patientPhone)
    InfoLine("이용자와의 관계", booking.options.relationship)
    if (privateDetails && booking.options.guardianPhone.isNotBlank()) InfoLine("보호자 연락처", booking.options.guardianPhone)
    InfoLine("서비스", booking.options.service.label)
    InfoLine("방문 일시", "${booking.date} ${booking.time}")
    InfoLine("만날 장소", booking.meeting)
    InfoLine("이동 지원", booking.support)
    if (privateDetails) InfoLine("요청사항", booking.note.ifBlank { "등록된 요청사항이 없습니다." })
    InfoLine("정보 공유", booking.options.sharing.label)
    PriceSummary(booking.options.hours, booking.options.estimatedWon)
    HorizontalDivider(); SectionTitle("담당 매니저")
    InfoLine("매니저", booking.manager.ifBlank { "배정 대기" })
    WorkflowSection(store, account, booking, onChanged)
    if (privateDetails && booking.cancellationReason.isNotBlank()) InfoLine("취소 사유", booking.cancellationReason)
    if (editable) {
        HorizontalDivider()
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("신청 수정") }
        TextButton(onClick = { cancelError = null; confirmCancel = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("신청 취소") }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("동행 신청을 취소할까요?") },
        text = { Column { listOf("일정 변경", "이용 필요 없음", "기타").forEach { reason -> ChoiceRadio(reason, reason == cancelReason) { cancelReason = reason } }
            if (cancelError != null) Text(cancelError!!, color = MaterialTheme.colorScheme.error) } },
        confirmButton = { TextButton(onClick = {
            runCatching { onCancel(cancelReason) }.onSuccess { confirmCancel = false }.onFailure { cancelError = it.message ?: "취소하지 못했습니다." }
        }) { Text("취소 확정") } }, dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("돌아가기") } })
}

@Composable
private fun AccountScreen(account: ServiceAccount, onUpdate: () -> Unit, onSignOut: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    Text(account.name, style = MaterialTheme.typography.headlineSmall)
    Text(account.role.label, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
    InfoLine("휴대폰", account.phone.take(3) + "-" + account.phone.substring(3, 7) + "-" + account.phone.takeLast(4))
    HorizontalDivider()
    ListItem(headlineContent = { Text("앱 업데이트") }, supportingContent = { Text("현재 버전 ${BuildConfig.VERSION_NAME}") },
        leadingContent = { Icon(Icons.Outlined.Refresh, null) },
        modifier = Modifier.clickable(onClick = onUpdate).semantics { contentDescription = "앱 업데이트 확인" },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    TextButton(onClick = { confirmLogout = true }) { Text("로그아웃") }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("로그아웃할까요?") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onSignOut() }) { Text("로그아웃") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("취소") } })
}

@Composable
internal fun SectionTitle(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing.isNotBlank()) Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusLabel(status: String) {
    Surface(color = if (status == "신청 취소") Color(0xFFEDF0F1) else if (status == "접수 완료") Color(0xFFEAF0FC) else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small) {
        Text(status, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (status == "접수 완료") Color(0xFF365CAC) else MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Statistic(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}
