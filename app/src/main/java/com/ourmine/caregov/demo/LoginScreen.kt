package com.ourmine.caregov.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
        Button(onClick = { focus.clearFocus(); keyboard?.hide(); error = onLogin(phone, pin) == null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = phone.isNotBlank() && pin.isNotBlank()) { Text("로그인") }
    }
}
