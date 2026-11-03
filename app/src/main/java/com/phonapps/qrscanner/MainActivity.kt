package com.phonapps.qrscanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.phonapps.qrscanner.data.db.ScanSource
import com.phonapps.qrscanner.data.repo.AppSettings
import com.phonapps.qrscanner.ui.navigation.AppNavGraph
import com.phonapps.qrscanner.ui.theme.QrScannerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Single-activity host. Text shared from other apps is classified, stored as
 * a scan, and opened as a result — cold start and warm share alike.
 */
class MainActivity : ComponentActivity() {

    private val externalResultId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as QrScannerApp).container
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsState(initial = AppSettings())
            val externalId by externalResultId.collectAsState()
            val scope = rememberCoroutineScope()

            QrScannerTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(
                        themeMode = settings.themeMode,
                        onThemeChange = {
                            scope.launch { container.settingsRepository.setThemeMode(it) }
                        },
                        externalResultId = externalId,
                        onExternalResultConsumed = { externalResultId.value = null }
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (com.phonapps.qrscanner.BuildConfig.DEBUG) {
            android.util.Log.d(
                "QrScanner",
                "handleIntent action=${intent.action} type=${intent.type} " +
                    "hasResultId=${intent.hasExtra(EXTRA_RESULT_ID)}"
            )
        }
        val container = (application as QrScannerApp).container
        if (intent.hasExtra(EXTRA_RESULT_ID)) {
            externalResultId.value =
                intent.getLongExtra(EXTRA_RESULT_ID, -1L).takeIf { it > 0 }
            intent.removeExtra(EXTRA_RESULT_ID)
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action = null // consume: rotation must not re-import the share
            if (!text.isNullOrBlank()) {
                lifecycleScope.launch {
                    try {
                        val id = container.scanRepository
                            .recordScan(text, ScanSource.SHARED, forceNew = true)
                        if (com.phonapps.qrscanner.BuildConfig.DEBUG) {
                            android.util.Log.d("QrScanner", "shared text stored as scan id=$id")
                        }
                        externalResultId.value = id
                    } catch (e: Exception) {
                        android.util.Log.e("QrScanner", "failed to store shared text", e)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_ID = "extra_result_id"
    }
}
