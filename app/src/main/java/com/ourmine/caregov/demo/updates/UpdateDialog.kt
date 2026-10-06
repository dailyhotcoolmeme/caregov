package com.ourmine.caregov.demo.updates

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ourmine.caregov.demo.BuildConfig

@Composable
fun UpdateDialog(updater: UpdateViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = updater.state
    val launchInstaller = {
        runCatching {
            val file = requireNotNull(updater.state.file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure { updater.reportInstallError() }
        Unit
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) launchInstaller()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱 업데이트") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("현재 버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                when (state.phase) {
                    UpdatePhase.IDLE, UpdatePhase.CHECKING -> {
                        Text("새 버전을 확인하고 있습니다.")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    UpdatePhase.CURRENT -> Text("최신 버전을 사용하고 있습니다.")
                    UpdatePhase.AVAILABLE -> {
                        Text("${state.update?.versionName} 버전이 준비되었습니다.")
                        if (!state.update?.releaseNotes.isNullOrBlank()) Text(state.update!!.releaseNotes)
                    }
                    UpdatePhase.DOWNLOADING -> {
                        Text("업데이트 다운로드 ${(state.progress * 100).toInt()}%")
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    UpdatePhase.READY -> Text("다운로드가 완료되었습니다. 새 버전을 설치해 주세요.")
                    UpdatePhase.ERROR -> Text(state.error ?: "업데이트를 확인할 수 없습니다.")
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                UpdatePhase.AVAILABLE -> TextButton(onClick = updater::download) { Text("업데이트 다운로드") }
                UpdatePhase.READY -> TextButton(onClick = {
                    if (context.packageManager.canRequestPackageInstalls()) {
                        launchInstaller()
                    } else {
                        runCatching {
                            installPermission.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                            )
                        }.onFailure { updater.reportInstallError() }
                    }
                }) { Text("새 버전 설치") }
                UpdatePhase.ERROR -> TextButton(onClick = updater::check) { Text("다시 확인") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
