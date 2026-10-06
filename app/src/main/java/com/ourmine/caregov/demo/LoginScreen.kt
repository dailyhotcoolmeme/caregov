package com.ourmine.caregov.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(accounts: List<ServiceAccount>, onLogin: (String, String) -> ServiceAccount?) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var selectedId by rememberSaveable { mutableStateOf(accounts.first().id) }
    val selected = accounts.first { it.id == selectedId }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    fun submit() {
        if (pin.length != 6) return
        focus.clearFocus(); keyboard?.hide()
        error = onLogin(selected.phone, pin) == null
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("병원동행", style = MaterialTheme.typography.headlineSmall)
        Text("로그인", style = MaterialTheme.typography.titleLarge)
        AccountSelector(accounts, selectedId) { selectedId = it; error = false; pin = "" }
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6); error = false }, label = { Text("비밀번호") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        if (error) Text("비밀번호를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
        Button(onClick = ::submit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = pin.length == 6) { Text("로그인") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSelector(accounts: List<ServiceAccount>, selectedId: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.first { it.id == selectedId }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(value = "${selected.role.label} · ${selected.name}", onValueChange = {}, readOnly = true,
            label = { Text("사용자") }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.role.label} · ${account.name}") },
                onClick = { expanded = false; onSelected(account.id) }) }
        }
    }
}

@Composable
internal fun AccountSwitchDialog(accounts: List<ServiceAccount>, current: ServiceAccount, onDismiss: () -> Unit,
    onLogin: (String, String) -> ServiceAccount?) {
    var selectedId by rememberSaveable { mutableStateOf(current.id) }
    var pin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("사용자 변경") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AccountSelector(accounts, selectedId) { selectedId = it; pin = ""; error = false }
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6); error = false }, label = { Text("비밀번호") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            if (error) Text("비밀번호를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(onClick = {
            error = onLogin(accounts.first { it.id == selectedId }.phone, pin) == null
            if (!error) onDismiss()
        }, enabled = pin.length == 6) { Text("변경") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
