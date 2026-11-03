package com.phonapps.qrscanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrivacyBlock(
                title = "Everything stays on this device",
                body = "Scans and history are stored in a private on-device database. " +
                    "There is no account, no cloud sync and no server receiving your data."
            )
            PrivacyBlock(
                title = "Scanning works offline",
                body = "QR recognition runs entirely on your phone with an on-device model. " +
                    "No internet connection is required to scan."
            )
            PrivacyBlock(
                title = "No ads, no analytics",
                body = "The app ships no advertising SDKs, no analytics and no tracking. " +
                    "It never logs Wi-Fi passwords or full scan contents anywhere outside the local database."
            )
            PrivacyBlock(
                title = "Permissions",
                body = "Camera — used only to preview and scan, and only while the scanner screen is visible.\n\n" +
                    "No storage permission is needed: image import uses the system photo picker, " +
                    "and exports go through the system file saver you control."
            )
            PrivacyBlock(
                title = "Links are never opened blindly",
                body = "Scan results are always shown for review first. Links open only when you tap them, " +
                    "and risky schemes (such as javascript: or file:) can never be dispatched as actions."
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrivacyBlock(title: String, body: String) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
