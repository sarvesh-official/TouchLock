package com.sarvesh.touchlock

import android.content.Context

/**
 * Stores the user's preferred gesture configuration.
 * The config is a list of 8 gesture values, one per slot in OpoProtocol.ALL_SLOTS order:
 *   [L-single, L-double, L-triple, L-hold, R-single, R-double, R-triple, R-hold]
 *
 * When the user taps "Restore", these values are sent to the buds instead of hardcoded defaults.
 * This ensures the user gets back THEIR preferred gestures, not ours.
 */
object GestureConfigStore {
    private const val PREFS_NAME = "gesture_config"
    private const val KEY_GESTURES = "gesture_values"
    private const val KEY_CONFIGURED = "is_configured"

    // Sensible defaults matching common Realme Link settings
    private val DEFAULT_GESTURES = listOf(
        OpoProtocol.GESTURE_PLAY_PAUSE,       // L single tap
        OpoProtocol.GESTURE_NEXT,             // L double tap
        OpoProtocol.GESTURE_PREVIOUS,         // L triple tap
        OpoProtocol.GESTURE_NOISE_CONTROL,    // L long press
        OpoProtocol.GESTURE_PLAY_PAUSE,       // R single tap
        OpoProtocol.GESTURE_NEXT,             // R double tap
        OpoProtocol.GESTURE_PREVIOUS,         // R triple tap
        OpoProtocol.GESTURE_NOISE_CONTROL,    // R long press
    )

    fun getGestureValues(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_GESTURES, null)
        if (saved != null) {
            return saved.split(",").mapNotNull { it.toIntOrNull() }
        }
        return DEFAULT_GESTURES
    }

    fun setGestureValues(context: Context, values: List<Int>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GESTURES, values.joinToString(","))
            .putBoolean(KEY_CONFIGURED, true)
            .apply()
    }

    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CONFIGURED, false)
    }
}
