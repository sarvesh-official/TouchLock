package com.sarvesh.touchlock

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AccentColor(
    val displayName: String,
    val light: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val dark: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val onPrimaryLight: Color,
    val onPrimaryDark: Color,
) {
    Teal(
        displayName = "Teal",
        light = Color(0xFF2D7A6F),
        lightContainer = Color(0xFFD4ECE6),
        lightOnContainer = Color(0xFF1A4A43),
        dark = Color(0xFF5BA89A),
        darkContainer = Color(0xFF1A3F3A),
        darkOnContainer = Color(0xFF7BC4B6),
        onPrimaryLight = Color(0xFFFFFFFF),
        onPrimaryDark = Color(0xFF0A2A25),
    ),
    Coral(
        displayName = "Coral",
        light = Color(0xFFC44A3A),
        lightContainer = Color(0xFFF5DAD0),
        lightOnContainer = Color(0xFF4A1A0E),
        dark = Color(0xFFE07060),
        darkContainer = Color(0xFF3A1A14),
        darkOnContainer = Color(0xFFFFB4A0),
        onPrimaryLight = Color(0xFFFFFFFF),
        onPrimaryDark = Color(0xFF2A0A05),
    ),
    Lavender(
        displayName = "Lavender",
        light = Color(0xFF6B5B95),
        lightContainer = Color(0xFFE8E0F0),
        lightOnContainer = Color(0xFF2A1F4A),
        dark = Color(0xFF9B8BC5),
        darkContainer = Color(0xFF2A1F3A),
        darkOnContainer = Color(0xFFC5B5E5),
        onPrimaryLight = Color(0xFFFFFFFF),
        onPrimaryDark = Color(0xFF150A2A),
    ),
    Amber(
        displayName = "Amber",
        light = Color(0xFFB8860B),
        lightContainer = Color(0xFFF5E6C8),
        lightOnContainer = Color(0xFF3A2A0A),
        dark = Color(0xFFD4A73C),
        darkContainer = Color(0xFF2A1F0A),
        darkOnContainer = Color(0xFFE8D080),
        onPrimaryLight = Color(0xFFFFFFFF),
        onPrimaryDark = Color(0xFF1A1005),
    ),
    Sage(
        displayName = "Sage",
        light = Color(0xFF5A7A4A),
        lightContainer = Color(0xFFD8E8D0),
        lightOnContainer = Color(0xFF1A3A1A),
        dark = Color(0xFF8AB570),
        darkContainer = Color(0xFF1A2A14),
        darkOnContainer = Color(0xFFB0D098),
        onPrimaryLight = Color(0xFFFFFFFF),
        onPrimaryDark = Color(0xFF0A1A05),
    ),
    Sky(
        displayName = "Sky",
        light = Color(0xFF3A7AB5),
        lightContainer = Color(0xFFD0E5F5),
        lightOnContainer = Color(0xFF0A2A4A),
        dark = Color(0xFF6BA8E0),
        darkContainer = Color(0xFF0F2A3A),
        darkOnContainer = Color(0xFF9BC8F0),
        onPrimaryLight = Color(0xFFFFFFFF),
        onPrimaryDark = Color(0xFF05152A),
    ),
    ;

    companion object {
        fun fromName(name: String?): AccentColor =
            entries.firstOrNull { it.name == name } ?: Teal
    }
}

object AccentColorStore {
    private const val PREFS = "accent_color"
    private const val KEY = "accent"

    fun get(context: Context): AccentColor {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AccentColor.fromName(prefs.getString(KEY, null))
    }

    fun set(context: Context, color: AccentColor) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, color.name)
            .apply()
    }
}
