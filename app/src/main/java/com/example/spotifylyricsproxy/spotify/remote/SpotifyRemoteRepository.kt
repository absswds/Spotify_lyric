package com.example.spotifylyricsproxy.spotify.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyTokenStore
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerOptions
import com.spotify.protocol.types.PlayerRestrictions
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/** Repeat mode constants matching Spotify's PlayerOptions.repeatMode */
object RepeatMode {
    const val OFF = 0
    const val TRACK = 1
    const val CONTEXT = 2
}

data class PlaybackOptions(
    val isShuffling: Boolean = false,
    val repeatMode: Int = RepeatMode.OFF,
    val canToggleShuffle: Boolean = true,
    val canRepeatTrack: Boolean = true,
    val canRepeatContext: Boolean = true,
    val canSkipNext: Boolean = true,
    val canSkipPrev: Boolean = true,
    val canSeek: Boolean = true
)

class SpotifyRemoteRepository(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String,
    private val albumArtDimension: Image.Dimension = Image.Dimension.LARGE
) {
    companion object {
        private const val TAG = "SpotifyRemoteRepo"
        const val AUTH_REQUEST_CODE = 0x10
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageHttpClient = OkHttpClient()
    private val albumArtCache = AlbumArtCache.getInstance(context)

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var albumArtJob: Job? = null
    private var lastImageUri: String = ""
    private var connectionTimeoutJob: Job? = null

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(
        SpotifyConnectionState.Disconnected
    )
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _currentTrack = MutableStateFlow(SpotifyTrackInfo())
    val currentTrack: StateFlow<SpotifyTrackInfo> = _currentTrack.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

    private val _playbackOptions = MutableStateFlow(PlaybackOptions())
    val playbackOptions: StateFlow<PlaybackOptions> = _playbackOptions.asStateFlow()

    fun authorize(activity: Activity) {
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )
            .setScopes(arrayOf("app-remote-control"))
            .build()

        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST_CODE, request)
    }

    fun handleAuthResponse(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != AUTH_REQUEST_CODE) return false

        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                Log.i(TAG, "Auth successful, token received")
                tryConnect()
                return true
            }

            AuthorizationResponse.Type.ERROR -> {
                Log.e(TAG, "Auth error: ${response.error}")
                _connectionState.value = SpotifyConnectionState.Error(
                    response.error ?: "授权失败"
                )
                return true
            }

            else -> {
                _connectionState.value = SpotifyConnectionState.Disconnected
                return true
            }
        }
    }

    fun tryConnect() {
        // Already connected — nothing to do
        if (_connectionState.value is SpotifyConnectionState.Connected) {
            Log.d(TAG, "tryConnect: already connected, skipping")
            return
        }

        Log.i(TAG, "tryConnect: state=${_connectionState.value}, attempting connection")

        // Cancel any pending connection timeout
        connectionTimeoutJob?.cancel()

        // If already Connecting from a prior call, just let this call
        // proceed — the SDK's second connect() replaces the first.
        _connectionState.value = SpotifyConnectionState.Connecting

        // Start a timeout — if the SDK never calls back, reset to
        // Disconnected so the user can tap "reconnect" again.
        connectionTimeoutJob = repositoryScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_connectionState.value is SpotifyConnectionState.Connecting) {
                Log.w(TAG, "Connection timed out after ${CONNECTION_TIMEOUT_MS}ms")
                _connectionState.value = SpotifyConnectionState.Disconnected
            }
        }

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(
            context,
            connectionParams,
            object : com.spotify.android.appremote.api.Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    connectionTimeoutJob?.cancel()
                    Log.i(TAG, "Connected to Spotify")
                    spotifyAppRemote = appRemote
                    _connectionState.value = SpotifyConnectionState.Connected
                    subscribeToPlayerState()
                }

                override fun onFailure(throwable: Throwable) {
                    connectionTimeoutJob?.cancel()
                    Log.e(TAG, "Connection failed", throwable)
                    val message = throwable.message ?: "Unknown error"
                    _connectionState.value = when {
                        message.contains("not installed", ignoreCase = true) ||
                            message.contains("unavailable", ignoreCase = true) ||
                            message.contains("Unable to connect", ignoreCase = true) ||
                            message.contains("Can't connect", ignoreCase = true) -> {
                            SpotifyConnectionState.SpotifyNotInstalled
                        }

                        message.contains("not logged in", ignoreCase = true) -> {
                            SpotifyConnectionState.SpotifyNotLoggedIn
                        }

                        message.contains("UserNotAuthorized") ||
                            message.contains("user is required to use Spotify") -> {
                            SpotifyConnectionState.Error(context.getString(R.string.error_auth_required))
                        }

                        else -> SpotifyConnectionState.Error(message)
                    }
                }
            }
        )
    }

    fun disconnect() {
        connectionTimeoutJob?.cancel()
        albumArtJob?.cancel()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        lastImageUri = ""
        _connectionState.value = SpotifyConnectionState.Disconnected
        _currentTrack.value = SpotifyTrackInfo()
        _albumArt.value = null
    }

    /** Force a fresh connection — disconnect first, then reconnect.
     *  Use when the user taps the "reconnect" button. */
    fun forceReconnect() {
        Log.i(TAG, "forceReconnect: disconnecting then reconnecting")
        disconnect()
        // Small delay to let the SDK finish cleanup before reconnecting.
        // Without this, SpotifyAppRemote.connect() can silently fail.
        repositoryScope.launch {
            delay(500)
            Log.i(TAG, "forceReconnect: attempting connect after delay")
            tryConnect()
        }
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { playerState: PlayerState ->
                val rawUri = playerState.track?.imageUri?.raw ?: ""
                val track = SpotifyTrackInfo(
                    trackId = playerState.track?.uri?.split(":")?.lastOrNull() ?: "",
                    trackUri = playerState.track?.uri ?: "",
                    title = playerState.track?.name ?: "",
                    artist = playerState.track?.artist?.name ?: "",
                    album = playerState.track?.album?.name ?: "",
                    durationMs = playerState.track?.duration ?: 0,
                    playbackPositionMs = playerState.playbackPosition,
                    isPaused = playerState.isPaused,
                    imageUri = rawUri
                )
                _currentTrack.value = track

                // Extract playback options (shuffle / repeat) and restrictions
                val opts = playerState.playbackOptions
                val restrictions = playerState.playbackRestrictions
                if (opts != null || restrictions != null) {
                    _playbackOptions.value = PlaybackOptions(
                        isShuffling = opts?.isShuffling ?: _playbackOptions.value.isShuffling,
                        repeatMode = opts?.repeatMode ?: _playbackOptions.value.repeatMode,
                        canToggleShuffle = restrictions?.canToggleShuffle ?: true,
                        canRepeatTrack = restrictions?.canRepeatTrack ?: true,
                        canRepeatContext = restrictions?.canRepeatContext ?: true,
                        canSkipNext = restrictions?.canSkipNext ?: true,
                        canSkipPrev = restrictions?.canSkipPrev ?: true,
                        canSeek = restrictions?.canSeek ?: true
                    )
                }

                if (rawUri != lastImageUri) {
                    lastImageUri = rawUri
                    loadAlbumArt(
                        trackId = track.trackId,
                        imageUri = playerState.track?.imageUri
                    )
                }
            }
            ?.setErrorCallback { error: Throwable ->
                Log.e(TAG, "Player state subscription error", error)
            }
    }

    fun play() {
        spotifyAppRemote?.playerApi?.resume()
    }

    fun playUri(uri: String) {
        if (uri.isBlank()) return
        spotifyAppRemote?.playerApi?.play(uri)
    }

    /** Play a context URI (playlist/album) starting at [startIndex].
     *  When shuffle is active, temporarily disable it so skipToIndex
     *  uses the original playlist order, then re-enable shuffle after
     *  the target track starts playing.
     *
     *  [specificTrackUri] is the individual track URI. When provided,
     *  use it directly (more reliable than play + skipToIndex which
     *  suffers from a race condition on slow connections). */
    fun playContext(contextUri: String, startIndex: Int, specificTrackUri: String = "") {
        if (contextUri.isBlank()) return
        val api = spotifyAppRemote?.playerApi ?: return

        // Playing a specific track URI is always more reliable.
        // App Remote automatically loads the track's context (playlist/album)
        // so skip-next/previous still work within the correct playlist.
        if (specificTrackUri.isNotBlank()) {
            val wasShuffling = _playbackOptions.value.isShuffling
            if (wasShuffling) {
                api.setShuffle(false).setResultCallback {
                    api.play(specificTrackUri)
                    api.setShuffle(true)
                }
            } else {
                api.play(specificTrackUri)
            }
            return
        }

        val wasShuffling = _playbackOptions.value.isShuffling

        if (wasShuffling) {
            api.setShuffle(false).setResultCallback {
                api.play(contextUri)
                if (startIndex > 0) {
                    api.skipToIndex(contextUri, startIndex)
                }
                api.setShuffle(true).setResultCallback {
                    Log.i(TAG, "Shuffle re-enabled after playContext")
                }
            }
        } else {
            api.play(contextUri)
            if (startIndex > 0) {
                api.skipToIndex(contextUri, startIndex)
            }
        }
    }

    fun pause() {
        spotifyAppRemote?.playerApi?.pause()
    }

    fun skipNext() {
        spotifyAppRemote?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        spotifyAppRemote?.playerApi?.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        spotifyAppRemote?.playerApi?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        // Optimistic UI update — immediately reflect the new state
        val currentOpts = _playbackOptions.value
        val newShuffling = !currentOpts.isShuffling
        _playbackOptions.value = currentOpts.copy(isShuffling = newShuffling)
        Log.i(TAG, "toggleShuffle → $newShuffling")

        spotifyAppRemote?.playerApi?.setShuffle(newShuffling)
            ?.setResultCallback { Log.i(TAG, "Shuffle set to $newShuffling succeeded") }
            ?.setErrorCallback { e -> Log.w(TAG, "Shuffle set to $newShuffling failed", e) }
    }

    /** Cycle repeat: OFF → CONTEXT → TRACK → OFF → ... */
    fun cycleRepeat() {
        val current = _playbackOptions.value
        val next = when (current.repeatMode) {
            RepeatMode.OFF -> RepeatMode.CONTEXT
            RepeatMode.CONTEXT -> RepeatMode.TRACK
            else -> RepeatMode.OFF
        }
        // Optimistic UI update
        _playbackOptions.value = current.copy(repeatMode = next)
        Log.i(TAG, "cycleRepeat → $next")

        spotifyAppRemote?.playerApi?.setRepeat(next)
            ?.setResultCallback { Log.i(TAG, "Repeat set to $next succeeded") }
            ?.setErrorCallback { e -> Log.w(TAG, "Repeat set to $next failed", e) }
    }

    fun isConnected(): Boolean =
        _connectionState.value is SpotifyConnectionState.Connected

    fun isPlaying(): Boolean =
        isConnected() && !_currentTrack.value.isPaused && _currentTrack.value.trackId.isNotEmpty()

    private fun loadAlbumArt(trackId: String, imageUri: ImageUri?) {
        albumArtJob?.cancel()
        if (trackId.isBlank() && imageUri == null) {
            _albumArt.value = null
            return
        }

        albumArtJob = repositoryScope.launch {
            // 1. Try the in-memory / file cache first — works offline, no network.
            val cached = albumArtCache.get(trackId)
            if (cached != null) {
                if (trackId == _currentTrack.value.trackId) _albumArt.value = cached
                return@launch
            }

            // 2. Cache miss: fetch from network. Order is Web API (highest res,
            //    needs token) → scdn direct URL (no token, original size) →
            //    App Remote imagesApi (lowest).
            val bitmap = fetchHighResAlbumArt(trackId)
                ?: fetchScdnDirectAlbumArt(imageUri)
                ?: fetchAppRemoteAlbumArt(imageUri)
            if (bitmap != null) {
                albumArtCache.put(trackId, bitmap)
            }
            if (trackId == _currentTrack.value.trackId) {
                _albumArt.value = bitmap
            }
        }
    }

    /**
     * Download the original-size artwork directly from Spotify's CDN using
     * the imageUri hash. No access token needed. Used as a high-res fallback
     * when the Web API token is expired (401).
     *
     * imageUri raw format: "spotify:image:ab67616d0000b273..."
     * scdn URL:           "https://i.scdn.co/image/ab67616d0000b273..."
     */
    private suspend fun fetchScdnDirectAlbumArt(imageUri: ImageUri?): Bitmap? {
        val raw = imageUri?.raw?.takeIf { it.isNotBlank() } ?: return null
        val hash = raw.substringAfterLast(":")
        if (hash.isBlank() || hash == raw) return null
        val scdnUrl = "https://i.scdn.co/image/$hash"

        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(scdnUrl).build()
                imageHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "fetchScdnDirectAlbumArt: HTTP ${response.code}")
                        return@withContext null
                    }
                    response.body?.byteStream()?.use(BitmapFactory::decodeStream)
                }
            }
        }.onFailure {
            Log.w(TAG, "Unable to download scdn album art", it)
        }.getOrNull()
    }

    private suspend fun fetchHighResAlbumArt(trackId: String): Bitmap? {
        if (trackId.isBlank()) return null

        val accessToken = SpotifyTokenStore.getAccessToken()
        val imageUrl = runCatching {
            withContext(Dispatchers.IO) {
                SpotifyWebApiClient.api.getTrack(
                    SpotifyWebApiClient.authHeader(accessToken ?: ""),
                    trackId
                )
            }?.album?.images
                ?.filter { it.url.isNotBlank() }
                ?.maxByOrNull { it.width ?: 0 }
                ?.url
        }.onFailure {
            Log.w(TAG, "Unable to query Spotify Web API album art", it)
        }.getOrNull() ?: run {
            if (accessToken == null) Log.w(TAG, "fetchHighResAlbumArt: no access token — falling back")
            return null
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(imageUrl).build()
                imageHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "fetchHighResAlbumArt: HTTP ${response.code}")
                        return@withContext null
                    }
                    response.body?.byteStream()?.use(BitmapFactory::decodeStream)
                }
            }
        }.onFailure {
            Log.w(TAG, "Unable to download Spotify album art", it)
        }.getOrNull()
    }

    private suspend fun fetchAppRemoteAlbumArt(imageUri: ImageUri?): Bitmap? {
        if (imageUri == null) return null

        return suspendCancellableCoroutine { continuation ->
            spotifyAppRemote?.imagesApi?.getImage(imageUri, albumArtDimension)
                ?.setResultCallback { bitmap ->
                    if (continuation.isActive) {
                        continuation.resume(bitmap)
                    }
                }
                ?.setErrorCallback { error ->
                    Log.w(TAG, "Unable to fetch App Remote album art", error)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                ?: continuation.resume(null)
        }
    }
}
