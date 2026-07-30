package com.example.spotifylyricsproxy.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Persists the user's theme preference ("system" / "light" / "dark")
 * so it survives process death.
 *
 * Usage: call [init] once from MainActivity.onCreate, then read [themeMode]
 * in any Composable to get the current setting.
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val DEFAULT_MODE = "system"
    private const val KEY_LOCALE = "app_locale"
    private const val DEFAULT_LOCALE = "system"
    private const val LOCALE_SYSTEM = "system"
    const val LOCALE_ZH = "zh"
    const val LOCALE_TW = "zh-TW"
    const val LOCALE_EN = "en"
    const val LOCALE_JA = "ja"

    private var prefs: SharedPreferences? = null
    private val _themeMode = mutableStateOf(DEFAULT_MODE)
    private val _locale = mutableStateOf(DEFAULT_LOCALE)

    /** Compose-observable theme mode: "system", "light", or "dark". */
    val themeMode: State<String> = _themeMode

    /** Compose-observable locale: "system", "zh", or "en". */
    val locale: State<String> = _locale

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode.value = prefs?.getString(KEY_THEME_MODE, DEFAULT_MODE) ?: DEFAULT_MODE
        _locale.value = prefs?.getString(KEY_LOCALE, DEFAULT_LOCALE) ?: DEFAULT_LOCALE
    }

    fun setThemeMode(mode: String) {
        if (mode != "system" && mode != "light" && mode != "dark") return
        prefs?.edit()?.putString(KEY_THEME_MODE, mode)?.apply()
        _themeMode.value = mode
    }

    /** Persist locale preference. Returns true if the value actually changed. */
    fun setLocale(localeCode: String): Boolean {
        if (localeCode != LOCALE_SYSTEM && localeCode != LOCALE_ZH && localeCode != LOCALE_TW && localeCode != LOCALE_EN && localeCode != LOCALE_JA) return false
        val changed = localeCode != _locale.value
        prefs?.edit()?.putString(KEY_LOCALE, localeCode)?.apply()
        _locale.value = localeCode
        return changed
    }

    /**
     * Resolve the effective "is dark" boolean for the current theme mode.
     * - "dark" → true
     * - "light" → false
     * - "system" → delegates to [isSystemInDarkTheme]
     */
    @Composable
    fun isDarkTheme(): Boolean {
        return when (themeMode.value) {
            "dark" -> true
            "light" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
    }
}
