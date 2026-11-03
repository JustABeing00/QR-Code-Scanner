package com.phonapps.qrscanner.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonapps.qrscanner.data.repo.ScanRepository
import com.phonapps.qrscanner.data.repo.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ResultViewModel(
    val scanId: Long,
    private val scans: ScanRepository,
    settings: SettingsRepository
) : ViewModel() {

    val scan = scans.observeById(scanId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoOpenUrls = settings.settings
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            com.phonapps.qrscanner.data.repo.AppSettings()
        )

    fun toggleFavorite(current: Boolean) {
        viewModelScope.launch { scans.setFavorite(scanId, !current) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            scans.delete(scanId)
            onDone()
        }
    }
}
