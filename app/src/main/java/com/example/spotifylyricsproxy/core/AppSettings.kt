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
    private const val KEY_TOUR_DONE = "tour_done"
    private const val KEY_CACHE_UNOFFICIAL = "cache_unofficial_sources"

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
        tourDone.value = prefs?.getBoolean(KEY_TOUR_DONE, false) ?: true
        cacheUnofficial.value = prefs?.takeIf { it.contains(KEY_CACHE_UNOFFICIAL) }?.getBoolean(KEY_CACHE_UNOFFICIAL, false)
    }

    /** False until the player's button tour has been shown once. */
    val tourDone = mutableStateOf(true)

    fun finishTour() {
        prefs?.edit()?.putBoolean(KEY_TOUR_DONE, true)?.apply()
        tourDone.value = true
    }

    fun replayTour() {
        prefs?.edit()?.putBoolean(KEY_TOUR_DONE, false)?.apply()
        tourDone.value = false
    }

    fun finishOnboarding() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_DONE, true)?.apply()
        onboardingDone.value = true
    }

    /** A song's own offset for one lyric source; sources are timed differently. */
    fun sourceOffsetMs(trackId: String, source: String): Long? =
        prefs?.takeIf { it.contains("offset|$trackId|$source") }?.getLong("offset|$trackId|$source", 0L)

    fun setSourceOffsetMs(trackId: String, source: String, value: Long) {
        prefs?.edit()?.putLong("offset|$trackId|$source", value)?.apply()
    }

    /**
     * Whether lyrics from unofficial sources (NetEase, QQ Music, Kugou) may be saved to
     * the offline cache: null until the user has been asked once.
     */
    val cacheUnofficial = mutableStateOf<Boolean?>(null)

    fun setCacheUnofficial(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CACHE_UNOFFICIAL, value)?.apply()
        cacheUnofficial.value = value
    }

    fun setGlobalOffsetMs(value: Long) {
        val clamped = value.coerceIn(-GLOBAL_OFFSET_LIMIT_MS, GLOBAL_OFFSET_LIMIT_MS)
        prefs?.edit()?.putLong(KEY_GLOBAL_OFFSET, clamped)?.apply()
        globalOffsetMs.longValue = clamped
    }
}
