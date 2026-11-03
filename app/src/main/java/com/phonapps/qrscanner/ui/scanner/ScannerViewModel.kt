package com.phonapps.qrscanner.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonapps.qrscanner.data.db.ScanEntity
import com.phonapps.qrscanner.data.db.ScanSource
import com.phonapps.qrscanner.data.repo.AppSettings
import com.phonapps.qrscanner.data.repo.ScanRepository
import com.phonapps.qrscanner.data.repo.SettingsRepository
import com.phonapps.qrscanner.scanner.ImageDecodeResult
import com.phonapps.qrscanner.scanner.ImageQrDecoder
import com.phonapps.qrscanner.util.Feedback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot UI events emitted after a successful decode. */
sealed interface ScanEvent {
    /** Single-scan mode: open the full result screen immediately. */
    data class OpenResult(val scanId: Long) : ScanEvent

    /** Continuous mode: surface the hit inline without leaving the camera. */
    data class SessionHit(val scan: ScanEntity) : ScanEvent
}

data class ImageImportState(
    val decoding: Boolean = false,
    val error: String? = null,
    val choices: List<ScanEntity> = emptyList()
)

private const val SAME_VALUE_COOLDOWN_MS = 2_500L

/**
 * Owns decode-result policy: duplicate suppression, history recording,
 * single vs continuous mode, and image-import decoding.
 */
class ScannerViewModel(
    private val scans: ScanRepository,
    private val settings: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    private val _sessionScans = MutableStateFlow<List<ScanEntity>>(emptyList())
    val sessionScans: StateFlow<List<ScanEntity>> = _sessionScans.asStateFlow()

    private val _imageImport = MutableStateFlow(ImageImportState())
    val imageImport: StateFlow<ImageImportState> = _imageImport.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var lastValue = ""
    private var lastEmitMs = 0L
    private var singleShotConsumed = false

    /** Called when the scanner screen (re)appears. */
    fun onScannerVisible() {
        singleShotConsumed = false
    }

    fun onClearSession() {
        _sessionScans.value = emptyList()
    }

    fun onImageChoicesDismissed() {
        _imageImport.update { it.copy(choices = emptyList()) }
    }

    fun onImageErrorDismissed() {
        _imageImport.update { it.copy(error = null) }
    }

    /** Camera decode path. [rawValues] are the barcodes found in one frame. */
    fun onCameraBarcodes(rawValues: List<String>) {
        val raw = rawValues.firstOrNull { it.isNotBlank() } ?: return
        val now = System.currentTimeMillis()
        if (raw == lastValue && now - lastEmitMs < SAME_VALUE_COOLDOWN_MS) return
        if (!appSettings.value.continuousMode && singleShotConsumed) return
        lastValue = raw
        lastEmitMs = now
        if (!appSettings.value.continuousMode) singleShotConsumed = true
        viewModelScope.launch { recordAndEmit(raw, ScanSource.CAMERA) }
    }

    /** Gallery / photo-picker decode path. */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _imageImport.update { it.copy(decoding = true, error = null) }
            when (val result = ImageQrDecoder.decode(appContext, uri)) {
                is ImageDecodeResult.Found -> {
                    val ids = result.values.map { value ->
                        scans.recordScan(value, ScanSource.IMAGE)
                    }
                    _imageImport.update { it.copy(decoding = false) }
                    feedback()
                    if (ids.size == 1) {
                        _events.emit(ScanEvent.OpenResult(ids.first()))
                    } else {
                        _imageImport.update { it.copy(choices = ids.mapNotNull { scans.getById(it) }) }
                    }
                }
                ImageDecodeResult.NoCodeFound -> {
                    _imageImport.update {
                        it.copy(decoding = false, error = "No QR code found in that image.")
                    }
                }
                is ImageDecodeResult.Error -> {
                    _imageImport.update { it.copy(decoding = false, error = result.message) }
                }
            }
        }
    }

    /** Clipboard-paste path: treats clipboard text as a scanned payload. */
    fun onClipboardText(text: String) {
        val raw = text.trim()
        if (raw.isEmpty()) {
            viewModelScope.launch { _snackbar.emit("Clipboard is empty.") }
            return
        }
        viewModelScope.launch { recordAndEmit(raw, ScanSource.CLIPBOARD) }
    }

    private suspend fun recordAndEmit(raw: String, source: ScanSource) {
        val continuous = appSettings.value.continuousMode && source == ScanSource.CAMERA
        val id = scans.recordScan(raw, source)
        if (com.phonapps.qrscanner.BuildConfig.DEBUG) {
            val kind = com.phonapps.qrscanner.data.model.QrClassifier.classify(raw).kind
            android.util.Log.d("QrScanner", "decoded ${raw.length} chars kind=$kind source=$source")
        }
        feedback()
        if (continuous) {
            scans.getById(id)?.let { entity ->
                _sessionScans.update { current ->
                    if (current.any { it.rawValue == entity.rawValue }) current
                    else (current + entity).takeLast(20)
                }
                _events.emit(ScanEvent.SessionHit(entity))
            }
        } else {
            _events.emit(ScanEvent.OpenResult(id))
        }
    }

    private fun feedback() {
        val cfg = appSettings.value
        Feedback.vibrate(appContext, cfg.vibrate)
        Feedback.beep(cfg.sound)
    }

    fun toggleContinuousMode(enabled: Boolean) {
        viewModelScope.launch { settings.setContinuousMode(enabled) }
    }
}
