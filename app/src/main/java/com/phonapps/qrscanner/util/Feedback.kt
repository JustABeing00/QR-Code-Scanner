package com.phonapps.qrscanner.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Subtle, configurable feedback for successful scans. */
object Feedback {

    fun vibrate(context: Context, enabled: Boolean) {
        if (!enabled) return
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (it.hasVibrator()) {
                    it.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) {
            // Haptics are best-effort; never fail a scan over them.
        }
    }

    fun beep(enabled: Boolean) {
        if (!enabled) return
        try {
            // Short confirmation tone; released immediately after use.
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                release()
            }
        } catch (_: Exception) {
            // Best-effort only.
        }
    }
}
