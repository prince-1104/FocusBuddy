package com.focusbuddy.data.preferences

import android.content.Context
import com.focusbuddy.domain.model.ThemeMode

/** Local appearance preference only; task persistence is intentionally not introduced here. */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun read(): ThemeMode = ThemeMode.entries.firstOrNull {
        it.name == preferences.getString("theme_mode", null)
    } ?: ThemeMode.System

    fun write(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
    }
}
