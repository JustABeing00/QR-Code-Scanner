package com.phonapps.qrscanner.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonapps.qrscanner.data.db.ScanEntity
import com.phonapps.qrscanner.data.model.ContentKind
import com.phonapps.qrscanner.data.model.QrClassifier
import com.phonapps.qrscanner.data.model.ScanContent
import com.phonapps.qrscanner.ui.common.ConfirmDialog
import com.phonapps.qrscanner.ui.common.KindBadge
import com.phonapps.qrscanner.ui.common.appViewModel
import com.phonapps.qrscanner.ui.common.rememberAppDeps
import com.phonapps.qrscanner.util.IntentActions
import com.phonapps.qrscanner.util.WifiConnector
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    scanId: Long,
    onBack: () -> Unit,
    onScanAgain: () -> Unit
) {
    val context = LocalContext.current
    val deps = rememberAppDeps()
    val vm: ResultViewModel = appViewModel(deps) {
        ResultViewModel(scanId, it.scans, it.settings)
    }
    val scan by vm.scan.collectAsStateWithLifecycle()
    val settings by vm.autoOpenUrls.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf(false) }
    var autoOpened by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val entity = scan
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entity?.let {
                            runCatching { ContentKind.valueOf(it.contentType) }
                                .getOrDefault(ContentKind.TEXT).label
                        } ?: "Result"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    entity?.let {
                        IconButton(onClick = { vm.toggleFavorite(it.favorite) }) {
                            Icon(
                                if (it.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (it.favorite) "Remove favorite" else "Save favorite",
                                tint = if (it.favorite) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { IntentActions.shareText(context, it.rawValue) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            entity?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { IntentActions.copyText(context, "QR content", it.rawValue) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy")
                    }
                    Button(onClick = onScanAgain, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan again")
                    }
                }
            }
        }
    ) { padding ->
        if (entity == null) {
            // Still loading (or deleted elsewhere).
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Loading result…", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val content = remember(entity.rawValue) { QrClassifier.classify(entity.rawValue) }

            // Explicit opt-in auto-open: show the URL first, open once, never for
            // flagged links.
            LaunchedEffect(content, settings.autoOpenUrls) {
                if (!autoOpened && settings.autoOpenUrls && content is ScanContent.Url &&
                    !content.suspicious
                ) {
                    autoOpened = true
                    if (!IntentActions.openUrl(context, content.url)) {
                        snackbar.showSnackbar("No browser available to open the link.")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeaderCard(entity)
                when (content) {
                    is ScanContent.Url -> UrlCard(content)
                    is ScanContent.Text -> TextCard(content)
                    is ScanContent.Wifi -> WifiCard(content)
                    is ScanContent.Contact -> ContactCard(content)
                    is ScanContent.Email -> EmailCard(content)
                    is ScanContent.Phone -> PhoneCard(content)
                    is ScanContent.Sms -> SmsCard(content)
                    is ScanContent.Geo -> GeoCard(content)
                    is ScanContent.Calendar -> CalendarCard(content)
                    is ScanContent.DeepLink -> DeepLinkCard(content)
                    is ScanContent.Unsafe -> UnsafeCard(content)
                }
                RawCard(raw = entity.rawValue, showByDefault = content is ScanContent.Text)
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this scan?",
            body = "It will be removed from your history permanently.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun HeaderCard(entity: ScanEntity) {
    val kind = runCatching { ContentKind.valueOf(entity.contentType) }.getOrDefault(ContentKind.TEXT)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KindBadge(kind, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.labelLarge)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(entity.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when (entity.source) {
                        "IMAGE" -> "From image"
                        "CLIPBOARD" -> "From clipboard"
                        "SHARED" -> "Shared from another app"
                        else -> "From camera"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: @Composable () -> Unit) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            body()
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun UrlCard(content: ScanContent.Url) {
    val context = LocalContext.current
    InfoCard("Website") {
        Text(content.domain, style = MaterialTheme.typography.headlineSmall)
        SelectionContainer {
            Text(
                content.url,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (content.suspicious) {
            WarningBanner(
                "This link looks unusual (odd format, IP address or non-secure scheme). " +
                    "Inspect it carefully before opening."
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Open in browser") {
            if (!IntentActions.openUrl(context, content.url)) {
                IntentActions.toastUnavailable(context)
            }
        }
        FilledTonalButton(
            onClick = { IntentActions.shareText(context, content.url) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share link") }
    }
}

@Composable
private fun TextCard(content: ScanContent.Text) {
    InfoCard("Text") {
        SelectionContainer {
            Text(content.text.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun WifiCard(content: ScanContent.Wifi) {
    val context = LocalContext.current
    var reveal by remember { mutableStateOf(false) }
    InfoCard("Wi-Fi network") {
        Field("Network name", content.ssid)
        Field("Security", content.security + if (content.hidden) " · hidden" else "")
        if (content.password.isNotEmpty()) {
            Text("Password", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        if (reveal) content.password else "••••••••",
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (reveal) "Hide password" else "Show password"
                    )
                }
            }
            OutlinedButton(
                onClick = { IntentActions.copyText(context, "Wi-Fi password", content.password) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copy password") }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Connect to this network") {
            if (!WifiConnector.connect(context, content.ssid, content.password, content.security)) {
                IntentActions.toastUnavailable(context)
            }
        }
    }
}

@Composable
private fun ContactCard(content: ScanContent.Contact) {
    val context = LocalContext.current
    InfoCard("Contact") {
        if (content.name.isNotBlank()) {
            Text(content.name, style = MaterialTheme.typography.headlineSmall)
        }
        content.phones.forEach { Field("Phone", it) }
        content.emails.forEach { Field("Email", it) }
        Field("Organization", content.organization)
        Field("Address", content.address)
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Save to contacts") {
            if (!IntentActions.saveContact(context, content)) {
                IntentActions.toastUnavailable(context)
            }
        }
        content.phones.firstOrNull()?.let { phone ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (!IntentActions.dial(context, phone)) IntentActions.toastUnavailable(context)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Call") }
                FilledTonalButton(
                    onClick = {
                        if (!IntentActions.sendSms(context, phone, "")) IntentActions.toastUnavailable(context)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Message") }
            }
        }
    }
}

@Composable
private fun EmailCard(content: ScanContent.Email) {
    val context = LocalContext.current
    InfoCard("Email") {
        Field("To", content.address)
        Field("Subject", content.subject)
        Field("Message", content.body)
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Compose email") {
            if (!IntentActions.sendEmail(context, content.address, content.subject, content.body)) {
                IntentActions.toastUnavailable(context)
            }
        }
    }
}

@Composable
private fun PhoneCard(content: ScanContent.Phone) {
    val context = LocalContext.current
    InfoCard("Phone number") {
        SelectionContainer {
            Text(
                content.number,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Call") {
            if (!IntentActions.dial(context, content.number)) IntentActions.toastUnavailable(context)
        }
        FilledTonalButton(
            onClick = {
                if (!IntentActions.sendSms(context, content.number, "")) IntentActions.toastUnavailable(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send SMS instead") }
    }
}

@Composable
private fun SmsCard(content: ScanContent.Sms) {
    val context = LocalContext.current
    InfoCard("SMS") {
        Field("To", content.number)
        Field("Message", content.message)
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Send SMS") {
            if (!IntentActions.sendSms(context, content.number, content.message)) {
                IntentActions.toastUnavailable(context)
            }
        }
    }
}

@Composable
private fun GeoCard(content: ScanContent.Geo) {
    val context = LocalContext.current
    InfoCard("Location") {
        if (content.query.isNotBlank()) {
            Text(content.query, style = MaterialTheme.typography.headlineSmall)
        }
        if (content.latitude != null && content.longitude != null) {
            SelectionContainer {
                Text(
                    "${content.latitude}, ${content.longitude}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Open in Maps") {
            if (!IntentActions.openMap(context, content)) IntentActions.toastUnavailable(context)
        }
    }
}

@Composable
private fun CalendarCard(content: ScanContent.Calendar) {
    val context = LocalContext.current
    InfoCard("Calendar event") {
        if (content.summary.isNotBlank()) {
            Text(content.summary, style = MaterialTheme.typography.headlineSmall)
        }
        Field("Starts", content.start)
        Field("Ends", content.end)
        Field("Location", content.location)
        Field("Description", content.description)
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Add to calendar") {
            if (!IntentActions.addCalendarEvent(context, content)) IntentActions.toastUnavailable(context)
        }
    }
}

@Composable
private fun DeepLinkCard(content: ScanContent.DeepLink) {
    val context = LocalContext.current
    InfoCard("App link") {
        WarningBanner(
            "This link opens another app on your device. " +
                "Only continue if you recognize where it leads."
        )
        SelectionContainer {
            Text(
                content.raw,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryAction("Open link") {
            if (!IntentActions.openDeepLink(context, content.raw)) {
                IntentActions.toastUnavailable(context)
            }
        }
    }
}

@Composable
private fun UnsafeCard(content: ScanContent.Unsafe) {
    InfoCard("Blocked link") {
        WarningBanner(
            "This QR code uses the \"${content.scheme}:\" scheme, which apps must never open directly. " +
                "The payload is shown below for inspection only."
        )
        SelectionContainer {
            Text(
                content.raw,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Text(
            "Copy and share are still available if you need the raw payload.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RawCard(raw: String, showByDefault: Boolean) {
    var expanded by remember { mutableStateOf(showByDefault) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(if (expanded) "Hide raw data" else "Show raw data")
            }
            if (expanded) {
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        raw,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
