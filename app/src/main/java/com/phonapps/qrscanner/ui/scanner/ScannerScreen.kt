package com.phonapps.qrscanner.ui.scanner

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonapps.qrscanner.data.db.ScanEntity
import com.phonapps.qrscanner.data.model.ContentKind
import com.phonapps.qrscanner.scanner.QrBarcodeAnalyzer
import com.phonapps.qrscanner.ui.common.KindBadge
import com.phonapps.qrscanner.ui.common.appViewModel
import com.phonapps.qrscanner.ui.common.iconForKind
import com.phonapps.qrscanner.ui.common.rememberAppDeps

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onScanResult: (scanId: Long, continuous: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deps = rememberAppDeps()
    val vm: ScannerViewModel = appViewModel(deps) {
        ScannerViewModel(it.scans, it.settings, context.applicationContext)
    }

    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val sessionScans by vm.sessionScans.collectAsStateWithLifecycle()
    val imageImport by vm.imageImport.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var successFlash by remember { mutableStateOf(false) }
    var successTick by remember { mutableLongStateOf(0L) }
    var lastHit by remember { mutableStateOf<ScanEntity?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var torchOn by remember(settings.torchOnByDefault) { mutableStateOf(settings.torchOnByDefault) }
    var askInSettings by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity

    fun isGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(isGranted()) }

    // Re-check whenever the screen returns to the foreground: the user may
    // have granted (or revoked) the permission in system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = isGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            // After a repeated denial the system stops showing its dialog,
            // so the only path left is the system settings screen.
            askInSettings = activity?.let {
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.CAMERA
                )
            } ?: true
        }
    }

    // Ask once on first open; afterwards the user taps the prompt card.
    LaunchedEffect(Unit) {
        if (!isGranted()) permissionLauncher.launch(Manifest.permission.CAMERA)
        vm.onScannerVisible()
    }

    // Success flash auto-reset.
    LaunchedEffect(successTick) {
        if (successTick > 0) {
            kotlinx.coroutines.delay(1_200)
            successFlash = false
        }
    }

    // Route decode events.
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ScanEvent.OpenResult -> {
                    successFlash = true
                    successTick = SystemClock.elapsedRealtime()
                    onScanResult(event.scanId, false)
                }
                is ScanEvent.SessionHit -> {
                    successFlash = true
                    successTick = SystemClock.elapsedRealtime()
                    lastHit = event.scan
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.snackbar.collect { snackbarHost.showSnackbar(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.onImagePicked(uri) }

    // ---- Camera controller (survives recompositions, torn down on dispose) ----
    val controller = remember {
        LifecycleCameraController(context).apply {
            // Preview is implicit with a controller; only analysis needs enabling.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // PreviewView then handles pinch-to-zoom and tap-to-focus automatically.
            isPinchToZoomEnabled = true
            isTapToFocusEnabled = true
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }
    var analyzerRef by remember { mutableStateOf<QrBarcodeAnalyzer?>(null) }

    DisposableEffect(hasPermission, settings.detectAllFormats) {
        if (hasPermission) {
            try {
                controller.bindToLifecycle(lifecycleOwner)
                val analyzer = QrBarcodeAnalyzer(settings.detectAllFormats) { barcodes ->
                    vm.onCameraBarcodes(barcodes.mapNotNull { it.rawValue })
                }
                analyzerRef = analyzer
                controller.setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(context), analyzer
                )
                if (torchOn) runCatching { controller.enableTorch(true) }
                cameraError = null
            } catch (e: Exception) {
                cameraError = "Camera unavailable: ${e.message}"
            }
            onDispose {
                runCatching {
                    controller.clearImageAnalysisAnalyzer()
                    controller.unbind()
                }
                analyzerRef?.shutdown()
                analyzerRef = null
            }
        } else {
            onDispose { }
        }
    }

    // Torch follows the toggle whenever the controller is live.
    LaunchedEffect(torchOn, hasPermission) {
        if (hasPermission) runCatching { controller.enableTorch(torchOn) }
    }

    val zoomState by controller.zoomState.observeAsState()
    val torchState by controller.torchState.observeAsState()
    // torchState only emits once a camera is bound; the button appears then.
    val hasFlash = torchState != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        ) {
            // ---------------- Top bar ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("QR Scanner", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (settings.continuousMode) "Continuous scanning" else "Point at any QR code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasFlash) {
                    IconButton(onClick = { torchOn = !torchOn }) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                            tint = if (torchOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Scan history")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            // ---------------- Camera area ----------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                when {
                    !hasPermission -> PermissionPrompt(
                        askInSettings = askInSettings,
                        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                    cameraError != null -> CameraErrorCard(message = cameraError!!)
                    else -> {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    this.controller = controller
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        ScanFrameOverlay(success = successFlash, modifier = Modifier.fillMaxSize())

                        // Continuous-mode hit card, with a soft slide-and-fade entrance.
                        val hit = lastHit
                        val hitVisible = settings.continuousMode && hit != null
                        val hitAlpha by animateFloatAsState(
                            targetValue = if (hitVisible) 1f else 0f,
                            label = "hitCard"
                        )
                        if (hit != null && (hitVisible || hitAlpha > 0.02f)) {
                            SessionHitCard(
                                hit = hit,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                                    .graphicsLayer {
                                        alpha = hitAlpha
                                        translationY = (1f - hitAlpha) * 48f
                                    },
                                onView = { onScanResult(hit.id, true) }
                            )
                        }
                    }
                }
            }

            // ---------------- Zoom ----------------
            val zoom = zoomState
            if (hasPermission && zoom != null && zoom.maxZoomRatio > 1.05f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = zoom.linearZoom,
                        onValueChange = {
                            runCatching { controller.setLinearZoom(it) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(1 + zoom.linearZoom * (zoom.maxZoomRatio - 1)).toInt()}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            // ---------------- Session strip (continuous) ----------------
            if (settings.continuousMode && sessionScans.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessionScans, key = { it.id }) { scan ->
                        FilterChip(
                            selected = false,
                            onClick = { onScanResult(scan.id, true) },
                            label = {
                                Text(
                                    scan.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ---------------- Bottom controls ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomAction(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Gallery",
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                BottomAction(
                    icon = Icons.Filled.ContentPaste,
                    label = "Paste",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        vm.onClipboardText(text.orEmpty())
                    },
                    modifier = Modifier.weight(0.85f)
                )
                ContinuousPill(
                    checked = settings.continuousMode,
                    onCheckedChange = { vm.toggleContinuousMode(it) },
                    modifier = Modifier.weight(2.1f)
                )
            }
        }
    }

    // ---- Image import states ----
    if (imageImport.decoding) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Analyzing image…")
                }
            }
        )
    }
    imageImport.error?.let { message ->
        LaunchedEffect(message) { snackbarHost.showSnackbar(message); vm.onImageErrorDismissed() }
    }
    if (imageImport.choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.onImageChoicesDismissed() },
            title = { Text("Found ${imageImport.choices.size} codes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    imageImport.choices.forEach { entity ->
                        val kind = runCatching { ContentKind.valueOf(entity.contentType) }
                            .getOrDefault(ContentKind.TEXT)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    vm.onImageChoicesDismissed()
                                    onScanResult(entity.id, false)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KindBadge(kind)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(kind.label, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    entity.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.onImageChoicesDismissed() }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun BottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Pill holding the continuous-mode label + switch. Same container treatment
 * as the other bottom actions, with generous internal spacing so the label
 * never crowds the switch. Tapping anywhere on the pill toggles the mode.
 */
@Composable
private fun ContinuousPill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Continuous",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun PermissionPrompt(askInSettings: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Camera access needed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                if (askInSettings)
                    "Camera permission was denied. Enable it in system settings to scan, or keep using gallery import."
                else
                    "Allow camera access to scan QR codes instantly. Gallery import works without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            if (askInSettings) {
                // Re-requesting is a no-op once permanently denied; settings
                // is the only path, so it is the only button offered.
                Button(onClick = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text("Open settings") }
            } else {
                Button(onClick = onRequest) { Text("Allow camera") }
            }
        }
    }
}

@Composable
private fun CameraErrorCard(message: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Something went wrong", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SessionHitCard(
    hit: ScanEntity,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = runCatching { ContentKind.valueOf(hit.contentType) }.getOrDefault(ContentKind.TEXT)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        onClick = onView
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KindBadge(kind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.labelSmall)
                Text(
                    hit.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                iconForKind(kind),
                contentDescription = "View result",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
