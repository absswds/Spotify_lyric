package com.example.spotifylyricsproxy.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Wraps a Context so that its resources resolve to the user's chosen locale.
 * Call [wrap] from [android.app.Activity.attachBaseContext].
 *
 * Reads directly from SharedPreferences (not from ThemePreferences State)
 * because [attachBaseContext] runs before [android.app.Activity.onCreate].
 */
object LocaleHelper {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_LOCALE = "app_locale"
    private const val SYSTEM = "system"

    fun wrap(context: Context): Context {
        val saved = getSavedLocale(context)
        if (saved == SYSTEM) return context

        val targetLocale = when (saved) {
            "zh" -> Locale.CHINESE
            "zh-TW" -> Locale.forLanguageTag("zh-Hant-TW")
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            else -> return context
        }

        Locale.setDefault(targetLocale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(targetLocale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(targetLocale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }

    fun saveLocale(context: Context, locale: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, locale)
            .apply()
    }

    fun getLocale(context: Context): String {
        return getSavedLocale(context)
    }

    private fun getSavedLocale(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, SYSTEM) ?: SYSTEM
    }
}
