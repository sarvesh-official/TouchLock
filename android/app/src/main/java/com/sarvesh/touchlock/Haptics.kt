package com.sarvesh.touchlock

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback utility using the Vibrator API for consistent feedback across OEMs.
 */
object Haptics {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Light tap for general interactions (toggles, button presses). */
    fun click() {
        vibrate(10, 80)
    }

    /** Medium tap for state changes (lock engaged). */
    fun medium() {
        vibrate(20, 120)
    }

    /** Strong tap for destructive/important actions (lock both, find device). */
    fun heavy() {
        vibrate(35, 180)
    }

    /**
     * Heartbeat pattern — two quick pulses (thump-thump) like a heartbeat.
     * The gap between beats and the intensity scale with proximity.
     *
     * @param level 0 to 4 — higher = faster + stronger heartbeat
     *              0 = far (slow, gentle), 4 = right here (fast, strong)
     */
    fun heartbeat(level: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val (firstAmp, secondAmp, gapBetweenBeats) = when (level) {
            0 -> Triple(60, 40, 200L)   // far — slow, gentle
            1 -> Triple(100, 70, 150L)  // nearby
            2 -> Triple(140, 100, 120L) // close
            3 -> Triple(180, 140, 90L)  // very close
            else -> Triple(255, 200, 60L) // right here — fast, strong
        }

        val timings = longArrayOf(0, 30, gapBetweenBeats, 50)
        val amplitudes = intArrayOf(0, firstAmp, 0, secondAmp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 30, gapBetweenBeats, 50), -1)
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }
}
