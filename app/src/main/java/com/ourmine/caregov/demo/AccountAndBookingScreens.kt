package com.ourmine.caregov.demo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LoginScreen(onLogin: (String, String) -> ServiceAccount?) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var phone by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("병원동행", style = MaterialTheme.typography.headlineSmall)
        Text("로그인", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(phone, { phone = it; error = false }, label = { Text("휴대폰 번호") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(pin, { pin = it; error = false }, label = { Text("비밀번호") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (error) Text("휴대폰 번호와 비밀번호를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { focus.clearFocus(); keyboard?.hide(); error = onLogin(phone, pin) == null }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = phone.isNotBlank() && pin.isNotBlank()) { Text("로그인") }
    }
}

@Composable
fun BookingForm(account: ServiceAccount, modifier: Modifier,
    onSubmit: (String, String, String, String, String, String, String, String) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var patient by rememberSaveable { mutableStateOf(if (account.role == DemoRole.PATIENT) account.name else "") }
    var hospital by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by rememberSaveable { mutableStateOf("10:00") }
    var meeting by rememberSaveable { mutableStateOf("") }
    var support by rememberSaveable { mutableStateOf("도보 이동") }
    var note by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionTitle(if (account.role == DemoRole.PATIENT) "본인 동행 신청" else "가족 동행 신청")
        OutlinedTextField(patient, { patient = it }, label = { Text("이용자 이름") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, readOnly = account.role == DemoRole.PATIENT)
        HorizontalDivider(); SectionTitle("병원 방문")
        OutlinedTextField(hospital, { hospital = it }, label = { Text("병원 이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(department, { department = it }, label = { Text("진료과") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = {
            val selected = LocalDate.parse(date)
            DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() },
                selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("방문 날짜  $date") }
        OutlinedButton(onClick = {
            val selected = LocalTime.parse(time)
            TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("HH:mm")) },
                selected.hour, selected.minute, true).show()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("방문 시간  $time") }
        HorizontalDivider(); SectionTitle("만날 장소와 이동 지원")
        OutlinedTextField(meeting, { meeting = it }, label = { Text("만날 장소") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Column {
            listOf("도보 이동", "보행 보조", "휠체어 이용").forEach { option ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = support == option, onClick = { support = option }); Text(option)
                }
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("요청사항 (선택)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text(if (account.role == DemoRole.PATIENT) "동행 신청을 위한 정보 제공에 동의합니다." else "이용자의 동의를 받고 동행을 신청합니다.",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            if (!submitted) {
                focus.clearFocus(); keyboard?.hide()
                submitted = true
                runCatching { onSubmit(patient, hospital, department, date, time, meeting, support, note) }
                    .onFailure { submitted = false; error = it.message ?: "신청을 저장하지 못했습니다. 다시 확인해 주세요." }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            enabled = !submitted && consent && patient.isNotBlank() && hospital.isNotBlank() && department.isNotBlank() && meeting.isNotBlank()) {
            Text("동행 신청하기")
        }
        Spacer(Modifier.height(16.dp))
    }
}
