package com.ourmine.caregov.demo.updates

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ourmine.caregov.demo.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val update: AppUpdate? = null,
    val progress: Float = 0f,
    val file: File? = null,
    val error: String? = null,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UpdateRepository(application)
    var state by mutableStateOf(UpdateState())
        private set

    init {
        check()
    }

    fun check() {
        if (state.phase == UpdatePhase.CHECKING || state.phase == UpdatePhase.DOWNLOADING) return
        viewModelScope.launch {
            state = UpdateState(phase = UpdatePhase.CHECKING)
            try {
                val update = repository.fetch()
                state = UpdateState(
                    phase = if (update.versionCode > BuildConfig.VERSION_CODE) UpdatePhase.AVAILABLE else UpdatePhase.CURRENT,
                    update = update,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state = UpdateState(phase = UpdatePhase.ERROR, error = error.message ?: "업데이트를 확인할 수 없습니다.")
            }
        }
    }

    fun download() {
        val update = state.update ?: return
        if (state.phase != UpdatePhase.AVAILABLE) return
        viewModelScope.launch {
            state = state.copy(phase = UpdatePhase.DOWNLOADING, error = null)
            try {
                val file = repository.download(update) { progress ->
                    withContext(Dispatchers.Main) { state = state.copy(progress = progress) }
                }
                state = state.copy(phase = UpdatePhase.READY, file = file, progress = 1f)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                state = state.copy(phase = UpdatePhase.ERROR, error = error.message ?: "업데이트를 다운로드할 수 없습니다.")
            }
        }
    }

    fun reportInstallError() {
        state = state.copy(phase = UpdatePhase.ERROR, error = "설치 화면을 열 수 없습니다. 다시 시도해 주세요.")
    }
}
