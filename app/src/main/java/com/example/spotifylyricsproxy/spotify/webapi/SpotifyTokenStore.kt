package com.example.spotifylyricsproxy.spotify.webapi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persists the Spotify Web API access token so it survives process death.
 * Tokens expire after ~1 hour; the caller should handle 401s gracefully.
 */
object SpotifyTokenStore {

    private const val PREFS_NAME = "spotify_webapi_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_SAVED_AT = "saved_at_ms"
    private const val TAG = "SpotifyTokenStore"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.i(TAG, "TokenStore initialized")
    }

    fun save(accessToken: String) {
        val p = prefs ?: return
        p.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "Token saved (length=${accessToken.length})")
    }

    fun getAccessToken(): String? {
        val p = prefs ?: return null
        val token = p.getString(KEY_ACCESS_TOKEN, null)
        if (token.isNullOrBlank()) return null
        return token
    }

    fun clear() {
        val p = prefs ?: return
        p.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_SAVED_AT)
            .apply()
        Log.i(TAG, "Token cleared")
    }

    /** Returns the age of the saved token in milliseconds, or -1 if none. */
    fun ageMs(): Long {
        val p = prefs ?: return -1
        val savedAt = p.getLong(KEY_SAVED_AT, -1L)
        return if (savedAt < 0) -1 else System.currentTimeMillis() - savedAt
    }
}
