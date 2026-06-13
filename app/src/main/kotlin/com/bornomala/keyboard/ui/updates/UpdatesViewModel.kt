package com.bornomala.keyboard.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bornomala.keyboard.data.update.UpdateConfig
import com.bornomala.keyboard.data.update.UpdateManifest
import com.bornomala.keyboard.data.update.UpdateService
import com.bornomala.keyboard.data.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdatesUiState(
    val versionName: String = "",
    val versionCode: Long = 0,
    val buildType: String = "release",
    val updatesConfigured: Boolean = true,
    val status: UpdateStatus = UpdateStatus.Idle,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val readyFile: File? = null,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val updateService: UpdateService,
) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdatesUiState(
            versionName = updateService.currentVersionName(),
            versionCode = updateService.currentVersionCode(),
            buildType = if (updateService.isDebugBuild()) "debug" else "release",
            updatesConfigured = UpdateConfig.isConfigured,
        ),
    )
    val state: StateFlow<UpdatesUiState> = _state.asStateFlow()

    init {
        // Auto-check on open so the user lands on a result, not a button.
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(status = UpdateStatus.Checking, readyFile = null) }
            val status = updateService.check()
            _state.update { it.copy(status = status) }
        }
    }

    fun download(manifest: UpdateManifest) {
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, readyFile = null) }
            val result = runCatching {
                updateService.download(manifest.apkUrl) { p ->
                    _state.update { it.copy(progress = p.coerceAtLeast(0f)) }
                }
            }
            result.fold(
                onSuccess = { file -> _state.update { it.copy(downloading = false, readyFile = file) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(downloading = false, status = UpdateStatus.Error(e.message ?: "Download failed"))
                    }
                },
            )
        }
    }
}
