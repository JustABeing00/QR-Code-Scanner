package com.phonapps.qrscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ImageDecodeResult {
    data class Found(val values: List<String>) : ImageDecodeResult
    data object NoCodeFound : ImageDecodeResult
    data class Error(val message: String) : ImageDecodeResult
}

/**
 * Decodes QR codes from a user-picked image. Large images are downsampled
 * before analysis so screenshots and photos cannot exhaust memory.
 */
object ImageQrDecoder {

    private const val MAX_DIMENSION = 2048

    suspend fun decode(context: Context, uri: Uri): ImageDecodeResult =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadDownsampledBitmap(context, uri)
                    ?: return@withContext ImageDecodeResult.Error("Could not read that image.")
                val client = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )
                try {
                    val barcodes = client.process(InputImage.fromBitmap(bitmap, 0)).await()
                    val values = barcodes.mapNotNull { it.rawValue?.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    if (values.isEmpty()) ImageDecodeResult.NoCodeFound
                    else ImageDecodeResult.Found(values)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    runCatching { client.close() }
                }
            } catch (e: SecurityException) {
                ImageDecodeResult.Error("Permission to read that image was denied.")
            } catch (e: OutOfMemoryError) {
                ImageDecodeResult.Error("That image is too large to analyze.")
            } catch (e: Exception) {
                ImageDecodeResult.Error(e.message ?: "Could not analyze that image.")
            }
        }

    private fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null
        var sampleSize = 1
        while (largest / sampleSize > MAX_DIMENSION) sampleSize *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
