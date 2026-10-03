package com.pronote.clay.data.local

import android.content.Context
import com.pronote.clay.domain.Preferences

/**
 * PreferencesStore — the DataStore equivalent for NON-sensitive settings only.
 * Never put credentials here: those belong in SecureCredentialsManager.
 */
class PreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("pronote_preferences", Context.MODE_PRIVATE)

    fun load(): Preferences = Preferences(
        autoSync = prefs.getBoolean("autoSync", true),
        notifyNewGrades = prefs.getBoolean("notifyNewGrades", true),
        notifyAverageChange = prefs.getBoolean("notifyAverageChange", true),
        darkTheme = if (prefs.contains("darkTheme")) prefs.getBoolean("darkTheme", false) else null,
        dynamicColor = prefs.getBoolean("dynamicColor", true),
        biometricLock = prefs.getBoolean("biometricLock", false),
    )

    fun save(preferences: Preferences) {
        prefs.edit()
            .putBoolean("autoSync", preferences.autoSync)
            .putBoolean("notifyNewGrades", preferences.notifyNewGrades)
            .putBoolean("notifyAverageChange", preferences.notifyAverageChange)
            .putBoolean("dynamicColor", preferences.dynamicColor)
            .putBoolean("biometricLock", preferences.biometricLock)
            .apply {
                preferences.darkTheme?.let { putBoolean("darkTheme", it) }
                if (preferences.darkTheme == null) remove("darkTheme")
            }
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}