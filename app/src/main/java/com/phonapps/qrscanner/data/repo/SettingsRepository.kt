package com.phonapps.qrscanner.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class AppSettings(
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    /** Disabled by default: scans never open links without an explicit choice. */
    val autoOpenUrls: Boolean = false,
    val continuousMode: Boolean = false,
    val detectAllFormats: Boolean = false,
    val torchOnByDefault: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

/** DataStore-backed user preferences. Everything persists across restarts. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val VIBRATE = booleanPreferencesKey("vibrate")
        val SOUND = booleanPreferencesKey("sound")
        val AUTO_OPEN = booleanPreferencesKey("auto_open_urls")
        val CONTINUOUS = booleanPreferencesKey("continuous_mode")
        val ALL_FORMATS = booleanPreferencesKey("detect_all_formats")
        val TORCH_DEFAULT = booleanPreferencesKey("torch_default")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            vibrate = prefs[Keys.VIBRATE] ?: true,
            sound = prefs[Keys.SOUND] ?: true,
            autoOpenUrls = prefs[Keys.AUTO_OPEN] ?: false,
            continuousMode = prefs[Keys.CONTINUOUS] ?: false,
            detectAllFormats = prefs[Keys.ALL_FORMATS] ?: false,
            torchOnByDefault = prefs[Keys.TORCH_DEFAULT] ?: false,
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME])
        )
    }

    suspend fun setVibrate(value: Boolean) = edit { it[Keys.VIBRATE] = value }
    suspend fun setSound(value: Boolean) = edit { it[Keys.SOUND] = value }
    suspend fun setAutoOpenUrls(value: Boolean) = edit { it[Keys.AUTO_OPEN] = value }
    suspend fun setContinuousMode(value: Boolean) = edit { it[Keys.CONTINUOUS] = value }
    suspend fun setDetectAllFormats(value: Boolean) = edit { it[Keys.ALL_FORMATS] = value }
    suspend fun setTorchOnByDefault(value: Boolean) = edit { it[Keys.TORCH_DEFAULT] = value }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.key }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
