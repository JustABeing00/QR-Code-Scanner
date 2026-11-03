package com.phonapps.qrscanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonapps.qrscanner.BuildConfig
import com.phonapps.qrscanner.data.repo.ThemeMode
import com.phonapps.qrscanner.ui.common.ConfirmDialog
import com.phonapps.qrscanner.ui.common.appViewModel
import com.phonapps.qrscanner.ui.common.rememberAppDeps
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val deps = rememberAppDeps()
    val vm: SettingsViewModel = appViewModel(deps) { SettingsViewModel(it.settings, it.scans) }
    val prefs by vm.state.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("Scanning") {
                ToggleRow(
                    title = "Vibration",
                    body = "Short buzz when a code is detected",
                    checked = prefs.vibrate,
                    onChange = vm::setVibrate
                )
                ToggleRow(
                    title = "Sound",
                    body = "Play a confirmation tone on detection",
                    checked = prefs.sound,
                    onChange = vm::setSound
                )
                ToggleRow(
                    title = "Auto-open links",
                    body = "Open safe website links immediately. Off keeps every link on screen for review first.",
                    checked = prefs.autoOpenUrls,
                    onChange = vm::setAutoOpenUrls
                )
                ToggleRow(
                    title = "Continuous scanning",
                    body = "Keep scanning code after code without leaving the camera",
                    checked = prefs.continuousMode,
                    onChange = vm::setContinuousMode
                )
                ToggleRow(
                    title = "Detect all barcode formats",
                    body = "Also recognize linear barcodes (EAN, Code 128, …) in addition to QR codes",
                    checked = prefs.detectAllFormats,
                    onChange = vm::setDetectAllFormats
                )
                ToggleRow(
                    title = "Flashlight on at startup",
                    body = "Start the scanner with the torch enabled",
                    checked = prefs.torchOnByDefault,
                    onChange = vm::setTorchDefault
                )
            }

            SettingsSection("Appearance") {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == mode,
                                    onClick = { onThemeChange(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System default"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (themeMode == mode) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection("History & data") {
                InfoRow(
                    title = "Stored on this device",
                    body = "$total scans kept locally. Nothing is uploaded anywhere."
                )
                InfoRow(
                    title = "Clearing asks first",
                    body = "Deleting history always asks for confirmation."
                )
                TextButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Clear scan history",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            SettingsSection("About") {
                InfoRow(title = "QR Scanner", body = "Version ${BuildConfig.VERSION_NAME}")
                InfoRow(
                    title = "Private by design",
                    body = "No account, no ads, no analytics. Scanning works fully offline."
                )
                ActionRow(
                    icon = Icons.Filled.Shield,
                    title = "Privacy details",
                    onClick = onOpenPrivacy
                )
                ActionRow(
                    icon = Icons.Filled.Info,
                    title = "Component credits",
                    onClick = {
                        scope.launch {
                            snackbar.showSnackbar(
                                "Built with CameraX, ML Kit, Room, DataStore and Jetpack Compose."
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all history?",
            body = "All $total scans will be permanently deleted from this device.",
            confirmLabel = "Clear all",
            destructive = true,
            onConfirm = {
                confirmClear = false
                vm.clearAll {
                    scope.launch { snackbar.showSnackbar("History cleared.") }
                }
            },
            onDismiss = { confirmClear = false }
        )
    }
}

// ------------------------------------------------------------------ rows

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}
