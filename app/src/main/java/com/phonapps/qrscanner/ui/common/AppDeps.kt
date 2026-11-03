package com.phonapps.qrscanner.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonapps.qrscanner.QrScannerApp
import com.phonapps.qrscanner.data.repo.ScanRepository
import com.phonapps.qrscanner.data.repo.SettingsRepository

data class AppDeps(
    val scans: ScanRepository,
    val settings: SettingsRepository
)

@Composable
fun rememberAppDeps(): AppDeps {
    val app = LocalContext.current.applicationContext as QrScannerApp
    return AppDeps(app.container.scanRepository, app.container.settingsRepository)
}

/** Minimal factory helper so ViewModels with constructor args stay testable. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    deps: AppDeps,
    crossinline create: (AppDeps) -> VM
): VM = viewModel(
    modelClass = VM::class.java,
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(deps) as T
    }
)
