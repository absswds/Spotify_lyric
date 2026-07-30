package com.example.spotifylyricsproxy.ui.precache

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import com.example.spotifylyricsproxy.database.entity.PlaylistCacheJobEntity
import com.example.spotifylyricsproxy.lyrics.LyricMatcher
import com.example.spotifylyricsproxy.lyrics.lrclib.LrclibLyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyTokenStore
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val RETRY_AFTER_MS = 7 * 24 * 60 * 60 * 1000L

internal fun failedCacheEntry(
    trackId: String,
    title: String,
    artist: String,
    durationMs: Long,
    now: Long = System.currentTimeMillis()
): LyricCacheEntity = LyricCacheEntity(
    spotifyTrackId = trackId,
    title = title,
    artist = artist,
    durationMs = durationMs,
    fetchStatus = "failed",
    nextRetryAt = now + RETRY_AFTER_MS,
    lastTriedAt = now,
    updatedAt = now
)

data class PrecacheProgress(
    val isRunning: Boolean = false,
    val progressPercent: Int = 0,
    val totalTracks: Int = 0,
    val cachedTracks: Int = 0,
    val failedTracks: Int = 0,
    val notFoundTracks: Int = 0,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

class PrecacheViewModel(application: Application) : AndroidViewModel(application) {

    private val clientId = com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifylyricsproxy://callback"
    private val webApiScopes = listOf(
        "app-remote-control",
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-read-private"
    )

    private val db = AppDatabase.getInstance(application)
    private val lyricsSource = LrclibLyricsSource()

    /** Web API access token — shared with PlaybackVM via SpotifyAuthHolder */
    private val accessToken: String?
        get() = SpotifyAuthHolder.accessToken

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylistItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylistItem>> = _playlists.asStateFlow()

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private val _cachedJobs = MutableStateFlow<List<PlaylistCacheJobEntity>>(emptyList())
    val cachedJobs: StateFlow<List<PlaylistCacheJobEntity>> = _cachedJobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, PrecacheProgress>>(emptyMap())
    val progressMap: StateFlow<Map<String, PrecacheProgress>> = _progressMap.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            db.playlistCacheJobDao().observeAll().collect { jobs ->
                _cachedJobs.value = jobs
            }
        }
    }

    fun handleAuthResponse(response: AuthorizationResponse) {
        // Accept any valid token — PlaybackVM now requests ALL scopes upfront,
        // so the shared token (SpotifyAuthHolder.accessToken) has everything we need.
        if (response.type == AuthorizationResponse.Type.TOKEN && SpotifyAuthHolder.accessToken != null) {
            Log.i("PrecacheVM", "Web API token received")
            _isAuthorized.value = true
            loadPlaylists()
        }
    }

    /** Called when a persisted token is restored on app startup. */
    fun handleRestoredToken(token: String) {
        Log.i("PrecacheVM", "Restored token, checking authorization")
        _isAuthorized.value = true
        loadPlaylists()
    }

    fun authorize(activity: Activity) {
        // Generate PKCE parameters
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        pkceCodeVerifier = codeVerifier

        // Build authorization URL
        val scopes = webApiScopes.joinToString(" ")
        val authUrl = "https://accounts.spotify.com/authorize" +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=$redirectUri" +
            "&scope=${java.net.URLEncoder.encode(scopes, "UTF-8")}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$codeChallenge" +
            "&show_dialog=true"

        // Open in browser (bypasses Spotify App native auth cache)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
        activity.startActivity(intent)
    }

    /** Handle the redirect from the browser-based PKCE auth flow */
    suspend fun handlePKCEAuthRedirect(uri: android.net.Uri): Boolean {
        val code = uri.getQueryParameter("code") ?: return false
        val verifier = pkceCodeVerifier ?: return false
        pkceCodeVerifier = null

        return try {
            val token = exchangeCodeForToken(code, verifier)
            if (token != null) {
                SpotifyAuthHolder.accessToken = token
                SpotifyTokenStore.save(token)
                _isAuthorized.value = true
                loadPlaylists()
                Log.i("PrecacheVM", "PKCE auth successful, token with playlist scopes obtained")
                true
            } else {
                Log.e("PrecacheVM", "PKCE code exchange failed: token is null")
                _toastMessage.value = getApplication<Application>().getString(R.string.error_auth_required)
                false
            }
        } catch (e: Exception) {
            Log.e("PrecacheVM", "PKCE code exchange failed", e)
            _toastMessage.value = getApplication<Application>().getString(R.string.error_auth_required) + ": ${e.message}"
            false
        }
    }

    // ---- PKCE helpers ----

    /** Shared secret saved between browser redirect and token exchange */
    private var pkceCodeVerifier: String? = null

    /** Generate a cryptographically random code verifier (43-128 chars) */
    private fun generateCodeVerifier(): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val random = java.security.SecureRandom()
        return (1..64).map { allowed[random.nextInt(allowed.length)] }.joinToString("")
    }

    /** Compute base64url-encoded SHA-256 hash of the code verifier */
    private fun generateCodeChallenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }

    /** Exchange the authorization code for an access token */
    private suspend fun exchangeCodeForToken(code: String, verifier: String): String? = withContext(Dispatchers.IO) {
        val formBody = okhttp3.FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(formBody)
            .build()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        Log.i("PrecacheVM", "Token exchange -> HTTP ${response.code}")
        response.close()
        if (response.isSuccessful && responseBody != null) {
            val json = org.json.JSONObject(responseBody)
            val token = json.optString("access_token")
            if (token.isNotEmpty()) token else null
        } else {
            Log.e("PrecacheVM", "Token exchange failed: $responseBody")
            null
        }
    }

    fun loadPlaylists() {
        val token = accessToken ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val me = SpotifyWebApiClient.api.getMe(auth = "Bearer $token")
                _currentUserId.value = me.id
                val response = SpotifyWebApiClient.api.getPlaylists(auth = "Bearer $token")
                val playlists = response.items
                Log.i(
                    "PrecacheVM",
                    "Got ${playlists.size} playlists for user=${me.id}; " +
                        "${playlists.count { it.canReadItems(me.id) }} can be precached"
                )
                _playlists.value = playlists
            } catch (e: Exception) {
                Log.e("PrecacheVM", "Failed to load playlists", e)
                // Detect auth errors (invalid/insufficient token) and reset auth state
                val isAuthError = isAuthError(e)
                if (isAuthError) {
                    SpotifyAuthHolder.accessToken = null
                    _isAuthorized.value = false
                    _currentUserId.value = ""
                    _toastMessage.value = getApplication<Application>().getString(R.string.error_auth_required)
                } else {
                    _playlists.value = emptyList()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Returns true if the exception represents an auth/forbidden error */
    private fun isAuthError(e: Exception): Boolean {
        if (e is retrofit2.HttpException && e.code() in listOf(401, 403)) return true
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("401") || msg.contains("403") ||
               msg.contains("unauthorized") || msg.contains("forbidden")
    }

    fun precachePlaylist(playlist: SpotifyPlaylistItem) {
        val token = accessToken ?: return
        val playlistId = playlist.id
        val playlistName = playlist.name
        if (!playlist.canReadItems(_currentUserId.value)) {
            _toastMessage.value = getApplication<Application>().getString(R.string.precache_owner_only, 0)
            Log.w(
                "PrecacheVM",
                "Skip playlist=$playlistId owner=${playlist.owner?.id} collaborative=${playlist.collaborative}; " +
                    "currentUser=${_currentUserId.value}"
            )
            return
        }

        // Show immediate progress
        val estimatedTotal = playlist.tracks.total.coerceAtLeast(1)
        _progressMap.value = _progressMap.value + (playlistId to PrecacheProgress(
            isRunning = true,
            totalTracks = estimatedTotal
        ))
        _toastMessage.value = getApplication<Application>().getString(R.string.precache_cd_start) + " $playlistName..."
        Log.i("PrecacheVM", "Launch precache coroutine for $playlistName")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch all tracks from Spotify
                updateProgress(playlistId, -1, 0, 0, 0, getApplication<Application>().getString(R.string.playlist_loading_tracks))
                val tracks = fetchTracks(token, playlistId)
                val total = tracks.size

                if (total == 0) {
                    withContext(Dispatchers.Main) {
                        saveJob(playlistId, playlistName, 0, 0, 0, 0)
                        _toastMessage.value = getApplication<Application>().getString(R.string.precache_empty)
                        _progressMap.value = _progressMap.value - playlistId
                    }
                    return@launch
                }

                Log.i("PrecacheVM", "Fetched $total tracks for $playlistName")

                // 2. Process each track
                var cached = 0
                var failed = 0
                var notFound = 0
                var processed = 0

                for ((trackId, title, artist, durationMs) in tracks) {
                    if (trackId.isBlank()) continue

                    // Skip if already cached
                    val existing = db.lyricCacheDao().getByTrackId(trackId)
                    if (existing != null && existing.fetchStatus == "success") {
                        cached++
                    } else {
                        try {
                            val candidates = lyricsSource.search(
                                LyricsSearchRequest(title, artist, "", durationMs)
                            )
                            if (candidates.isNotEmpty()) {
                                val best = candidates.maxByOrNull {
                                    LyricMatcher.score(it, title, artist, "", durationMs).score
                                }
                                if (best != null) {
                                    val scored = LyricMatcher.score(best, title, artist, "", durationMs)
                                    val status = when {
                                        !scored.syncedLyrics.isNullOrEmpty() -> "success"
                                        !scored.plainLyrics.isNullOrEmpty() -> "plain_only"
                                        else -> "not_found"
                                    }
                                    db.lyricCacheDao().upsert(
                                        LyricCacheEntity(
                                            spotifyTrackId = trackId,
                                            title = title,
                                            artist = artist,
                                            durationMs = durationMs,
                                            source = "lrclib",
                                            syncedLyrics = scored.syncedLyrics,
                                            plainLyrics = scored.plainLyrics,
                                            fetchStatus = status,
                                            confidenceScore = scored.score
                                        )
                                    )
                                    if (status == "success") cached++ else notFound++
                                } else {
                                    notFound++
                                }
                            } else {
                                db.lyricCacheDao().upsert(
                                    LyricCacheEntity(
                                        spotifyTrackId = trackId,
                                        title = title,
                                        artist = artist,
                                        durationMs = durationMs,
                                        fetchStatus = "not_found",
                                        nextRetryAt = System.currentTimeMillis() + RETRY_AFTER_MS
                                    )
                                )
                                notFound++
                            }
                        } catch (e: Exception) {
                            Log.e("PrecacheVM", "Failed for $title", e)
                            db.lyricCacheDao().upsert(
                                failedCacheEntry(
                                    trackId = trackId,
                                    title = title,
                                    artist = artist,
                                    durationMs = durationMs
                                )
                            )
                            failed++
                        }
                    }

                    processed++
                    val pct = (processed * 100) / total
                    updateProgress(playlistId, pct, cached, failed, notFound, null)
                }

                // 3. Save result
                withContext(Dispatchers.Main) {
                    saveJob(playlistId, playlistName, total, cached, failed, notFound)
                    _toastMessage.value = getApplication<Application>().getString(R.string.precache_complete_format, cached, total)
                    _progressMap.value = _progressMap.value - playlistId
                }

            } catch (e: Exception) {
                Log.e("PrecacheVM", "Precache failed for $playlistName", e)
                withContext(Dispatchers.Main) {
                    _progressMap.value = _progressMap.value + (playlistId to PrecacheProgress(
                        isRunning = false, isError = true, errorMessage = e.message
                    ))
                    _toastMessage.value = getApplication<Application>().getString(R.string.error_precache_failed) + ": ${e.message}"
                }
            }
        }
    }

    private suspend fun fetchTracks(
        token: String,
        playlistId: String
    ): List<CachedTrack> {
        val result = mutableListOf<CachedTrack>()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // 1. Verify token by calling /v1/me
        try {
            val meReq = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $token")
                .build()
            val meResp = client.newCall(meReq).execute()
            val meBody = meResp.body?.string()
            Log.i("PrecacheVM", "GET /v1/me -> HTTP ${meResp.code}, body=${meBody?.take(200)}")
            if (meResp.code == 403) {
                Log.w("PrecacheVM", "Token lacks required scopes for Web API")
            }
            meResp.close()
        } catch (e: Exception) {
            Log.w("PrecacheVM", "GET /v1/me failed", e)
        }

        // 2. Fetch tracks with pagination. Spotify's current endpoint is /items;
        // /tracks is deprecated and can return 403 for newer apps.
        var offset = 0
        var batchTotal = 0

        do {
            val url = "https://api.spotify.com/v1/playlists/$playlistId/items?limit=100&offset=$offset"
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            Log.i("PrecacheVM", "GET playlist items offset=$offset -> HTTP ${resp.code}, body=${body?.take(500)}")

            if (!resp.isSuccessful || body == null) {
                val errorBody = body?.take(200) ?: "empty body"
                if (resp.code == 403) {
                    throw RuntimeException(getApplication<Application>().getString(R.string.error_fetch_playlist))
                }
                throw RuntimeException("HTTP ${resp.code}: $errorBody")
            }

            val json = org.json.JSONObject(body)
            if (batchTotal == 0) batchTotal = json.optInt("total", 0)
            val itemsArray = json.optJSONArray("items")
            var count = 0

            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    val trackJson = item.optJSONObject("item") ?: item.optJSONObject("track") ?: continue
                    val trackId = trackJson.optString("id", "")
                    val name = trackJson.optString("name", "")
                    val artistsArray = trackJson.optJSONArray("artists")
                    val artist = if (artistsArray != null) {
                        (0 until artistsArray.length()).joinToString(", ") { j ->
                            artistsArray.getJSONObject(j).optString("name", "")
                        }
                    } else ""
                    val durationMs = trackJson.optLong("duration_ms", 0)
                    if (trackId.isNotEmpty()) {
                        result.add(CachedTrack(trackId, name, artist, durationMs))
                        count++
                    }
                }
            }

            resp.close()
            offset += 100
            if (count == 0) break
        } while (offset < batchTotal)

        Log.i("PrecacheVM", "Fetched ${result.size} tracks total")
        return result
    }

    private fun updateProgress(
        playlistId: String,
        percent: Int,
        cached: Int,
        failed: Int,
        notFound: Int,
        label: String?
    ) {
        val current = _progressMap.value.getOrDefault(playlistId, PrecacheProgress())
        _progressMap.value = _progressMap.value + (playlistId to current.copy(
            isRunning = true,
            progressPercent = if (percent >= 0) percent else current.progressPercent,
            cachedTracks = cached,
            failedTracks = failed,
            notFoundTracks = notFound,
            totalTracks = current.totalTracks.coerceAtLeast(cached + failed + notFound)
        ))
    }

    private suspend fun saveJob(
        playlistId: String,
        playlistName: String,
        total: Int,
        cached: Int,
        failed: Int,
        notFound: Int
    ) {
        db.playlistCacheJobDao().upsert(
            PlaylistCacheJobEntity(
                playlistId = playlistId,
                playlistName = playlistName,
                totalTracks = total,
                cachedTracks = cached,
                failedTracks = failed,
                notFoundTracks = notFound,
                lastScanAt = System.currentTimeMillis()
            )
        )
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun logout() {
        SpotifyAuthHolder.accessToken = null
        _isAuthorized.value = false
        _playlists.value = emptyList()
        _currentUserId.value = ""
    }

    private data class CachedTrack(
        val trackId: String,
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    fun isPlaylistCacheable(playlist: SpotifyPlaylistItem): Boolean =
        playlist.canReadItems(_currentUserId.value)

    private fun SpotifyPlaylistItem.canReadItems(currentUserId: String): Boolean {
        if (currentUserId.isBlank()) return false
        return collaborative || owner?.id == currentUserId
    }
}
