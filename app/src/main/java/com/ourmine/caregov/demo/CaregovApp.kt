package com.ourmine.caregov.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ourmine.caregov.demo.updates.UpdateDialog
import com.ourmine.caregov.demo.updates.UpdatePhase
import com.ourmine.caregov.demo.updates.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregovApp(session: DemoSession, updater: UpdateViewModel) {
    val role = remember { session.loadRole() }
    var showUpdate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(updater.state.phase) {
        if (updater.state.phase == UpdatePhase.AVAILABLE) showUpdate = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("병원동행", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = {
                        showUpdate = true
                        if (updater.state.phase != UpdatePhase.READY && updater.state.phase != UpdatePhase.DOWNLOADING) updater.check()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "앱 업데이트 확인")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${role.label} · ${role.accountName}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (role == DemoRole.OPERATOR) "오늘의 운영 현황" else "${role.accountName}님, 안녕하세요",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "10월 6일 화요일",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (role == DemoRole.OPERATOR) {
                OperationsOverview()
            } else {
                AppointmentOverview(role)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("연락 정보", style = MaterialTheme.typography.titleMedium)
                ContactRow(
                    "이용자",
                    "김영희",
                )
                HorizontalDivider()
                ContactRow(if (role == DemoRole.MANAGER) "공유받는 보호자" else "담당 매니저",
                    if (role == DemoRole.MANAGER) "이준호 · 아들" else "박서연",
                )
            }
        }
    }

    if (showUpdate) UpdateDialog(updater = updater, onDismiss = { showUpdate = false })

}

@Composable
private fun AppointmentOverview(role: DemoRole) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (role == DemoRole.MANAGER) "오늘의 일정" else "오늘의 동행",
            style = MaterialTheme.typography.titleMedium,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("오전 10:00", style = MaterialTheme.typography.titleMedium)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "예약 확정",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Text("서울의료원 · 내과", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (role) {
                        DemoRole.PATIENT -> "김영희님 본인 동행"
                        DemoRole.GUARDIAN -> "어머니 김영희님 동행"
                        else -> "김영희님 · 도보 이동 가능"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                HorizontalDivider()
                ContactRow("만날 장소", "서울의료원 1층 로비")
                ContactRow("담당 매니저", "박서연")
            }
        }
    }
}

@Composable
private fun OperationsOverview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("오늘 예약", "1", Modifier.weight(1f))
            Metric("미배정", "0", Modifier.weight(1f))
        }
        HorizontalDivider()
        Text("오늘 예약", style = MaterialTheme.typography.titleMedium)
        Text("10:00 · 김영희", style = MaterialTheme.typography.titleMedium)
        Text("서울의료원 · 내과", style = MaterialTheme.typography.bodyLarge)
        ContactRow("배정 상태", "박서연 매니저 배정 완료")
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun ContactRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
