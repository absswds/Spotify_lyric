package com.example.spotifylyricsproxy.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * App-wide settings that are read outside the UI (lyric sync in the repository/service).
 * Snapshot state so Compose screens observe changes directly.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_GLOBAL_OFFSET = "global_offset_ms"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    /** Clamp for the global offset stepper: ±5 s. */
    const val GLOBAL_OFFSET_LIMIT_MS = 5_000L

    private var prefs: SharedPreferences? = null

    /** Positive = lyrics later. Added to every song's own offset. */
    val globalOffsetMs: MutableLongState = mutableLongStateOf(0L)

    /** False until the first-launch guide has been finished or skipped. */
    val onboardingDone = mutableStateOf(true)

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        globalOffsetMs.longValue = prefs?.getLong(KEY_GLOBAL_OFFSET, 0L) ?: 0L
        onboardingDone.value = prefs?.getBoolean(KEY_ONBOARDING_DONE, false) ?: true
    }

    fun finishOnboarding() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_DONE, true)?.apply()
        onboardingDone.value = true
    }

    fun setGlobalOffsetMs(value: Long) {
        val clamped = value.coerceIn(-GLOBAL_OFFSET_LIMIT_MS, GLOBAL_OFFSET_LIMIT_MS)
        prefs?.edit()?.putLong(KEY_GLOBAL_OFFSET, clamped)?.apply()
        globalOffsetMs.longValue = clamped
    }
}
