package com.example.spotifylyricsproxy.ui.theme

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Persists lyrics translation target language preference.
 * Follows UI locale by default; user override is persisted.
 */
object TranslationPrefs {

    private const val PREFS_NAME = "translation_prefs"
    private const val KEY_TARGET_LANG = "lyrics_translation_target"
    private const val KEY_USER_OVERRIDE = "user_overridden"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Load translation target language.
     * If user has never manually overridden, default to current UI locale.
     * Otherwise return persisted value.
     */
    fun loadTargetLang(context: Context): String {
        init(context)
        if (prefs?.getBoolean(KEY_USER_OVERRIDE, false) == false) {
            return resolveByUiLocale(context)
        }
        return prefs?.getString(KEY_TARGET_LANG, null) ?: resolveByUiLocale(context)
    }

    fun saveTargetLang(context: Context, lang: String) {
        init(context)
        prefs?.edit()?.putString(KEY_TARGET_LANG, lang)?.putBoolean(KEY_USER_OVERRIDE, true)?.apply()
    }

    /**
     * Resolve target language from current UI locale.
     * Falls back to "en" if locale is unrecognized.
     */
    private fun resolveByUiLocale(context: Context): String {
        val appLocale = ThemePreferences.locale.value
        return when (appLocale) {
            ThemePreferences.LOCALE_ZH -> "zh"
            ThemePreferences.LOCALE_EN -> "en"
            ThemePreferences.LOCALE_JA -> "ja"
            else -> {
                val sysLang = Locale.getDefault().language
                when (sysLang) {
                    "zh" -> "zh"
                    "ja" -> "ja"
                    else -> "en"
                }
            }
        }
    }
}
