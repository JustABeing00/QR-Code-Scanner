package com.phonapps.qrscanner.scanner

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX analyzer backed by the ML Kit bundled barcode model (fully offline).
 *
 * Performance design:
 * - [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] (set by the caller) keeps the
 *   pipeline fed with the newest frame instead of a stale queue.
 * - A time gate drops frames while a detection is in flight or too recent, so
 *   the ML Kit client is never stacked with concurrent requests.
 * - Every [ImageProxy] is closed exactly once on all paths.
 */
class QrBarcodeAnalyzer(
    detectAllFormats: Boolean,
    private val minIntervalMs: Long = 350L,
    private val onBarcodes: (List<Barcode>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                if (detectAllFormats) Barcode.FORMAT_ALL_FORMATS
                else Barcode.FORMAT_QR_CODE
            )
            .build()
    )

    @Volatile
    private var inFlight = false
    @Volatile
    private var lastDispatchMs = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val mediaImage = imageProxy.image
        if (inFlight || now - lastDispatchMs < minIntervalMs || mediaImage == null) {
            imageProxy.close()
            return
        }
        inFlight = true
        lastDispatchMs = now
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                // Only wake the UI thread when something was actually found.
                if (barcodes.isNotEmpty()) onBarcodes(barcodes)
            }
            // Swallow per-frame errors: a blurry frame must never kill scanning.
            .addOnCompleteListener {
                inFlight = false
                imageProxy.close()
            }
    }

    fun shutdown() {
        runCatching { scanner.close() }
    }
}
