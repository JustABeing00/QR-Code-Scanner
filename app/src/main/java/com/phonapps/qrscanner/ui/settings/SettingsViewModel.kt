package com.phonapps.qrscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonapps.qrscanner.data.repo.AppSettings
import com.phonapps.qrscanner.data.repo.ScanRepository
import com.phonapps.qrscanner.data.repo.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val scans: ScanRepository
) : ViewModel() {

    val state = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val totalCount = scans.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setVibrate(v: Boolean) = launch { settings.setVibrate(v) }
    fun setSound(v: Boolean) = launch { settings.setSound(v) }
    fun setAutoOpenUrls(v: Boolean) = launch { settings.setAutoOpenUrls(v) }
    fun setContinuousMode(v: Boolean) = launch { settings.setContinuousMode(v) }
    fun setDetectAllFormats(v: Boolean) = launch { settings.setDetectAllFormats(v) }
    fun setTorchDefault(v: Boolean) = launch { settings.setTorchOnByDefault(v) }

    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            scans.clearAll()
            onDone()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
