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
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val AUTH_REQUEST_CODE = 0x10
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val WATCHDOG_MS = 3_000L
        private const val OTHER_DEVICE_POLL_MS = 5_000L
        private const val IDLE_POLL_MS = 15_000L
        private const val LOCAL_CHECK_POLL_MS = 30_000L
        private const val IDLE_POLL_MAX_MS = 120_000L
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageHttpClient = OkHttpClient()

    /**
     * Playback is on another Spotify Connect device. The phone's Spotify then keeps
     * reporting the last local state (paused, old track) through both App Remote and its
     * MediaSession, so the Web API is the only source; local paused events are ignored.
     */
    private val _otherDevicePlaying = MutableStateFlow(false)
    val otherDevicePlayingFlow: StateFlow<Boolean> = _otherDevicePlaying.asStateFlow()
    private var otherDevicePlaying: Boolean
        get() = _otherDevicePlaying.value
        set(value) { _otherDevicePlaying.value = value }

    /** Off for the UI's copy while the app is in the background; the service keeps polling. */
    @Volatile var webPollEnabled = true

    // Nothing playing anywhere: back off from IDLE_POLL_MS up to IDLE_POLL_MAX_MS.
    private var idlePollMs = IDLE_POLL_MS

    /** The track Spotify plays next (from the Web API queue), or null when unknown. */
    private val _nextTrack = MutableStateFlow<com.example.spotifylyricsproxy.spotify.webapi.SpotifyTrack?>(null)
    val nextTrack: StateFlow<com.example.spotifylyricsproxy.spotify.webapi.SpotifyTrack?> = _nextTrack.asStateFlow()

    /** False when the queue could not be read (signed out, expired token, offline). */
    private suspend fun refreshQueue(): Boolean {
        val token = SpotifyTokenStore.getAccessToken()
        if (token == null || SpotifyTokenStore.needsRefresh()) {
            Log.d(TAG, "Queue skipped: token=${token != null} age=${SpotifyTokenStore.ageMs()}")
            return false
        }
        val response = runCatching {
            withContext(Dispatchers.IO) { SpotifyWebApiClient.api.getQueue(SpotifyWebApiClient.authHeader(token)) }
        }.getOrNull()
        if (response?.isSuccessful != true) {
            Log.w(TAG, "Queue lookup failed: ${response?.code()}")
            return false
        }
        _nextTrack.value = response.body()?.queue?.firstOrNull()?.takeIf { it.id.isNotBlank() }
        Log.i(TAG, "Next up: ${_nextTrack.value?.name}")
        return true
    }

    init {
        // One queue lookup per track change; the queue itself rarely changes mid-song.
        repositoryScope.launch(Dispatchers.Main) {
            var seenId = ""
            var queuedFor = ""
            while (true) {
                val id = _currentTrack.value.trackId
                if (id != seenId) {
                    seenId = id
                    _nextTrack.value = null
                    delay(1_500) // let Spotify settle the new queue
                }
                // Retried until it works, e.g. once the user signs in.
                if (id.isNotBlank() && id != queuedFor && refreshQueue()) queuedFor = id
                delay(if (id == queuedFor) 1_000 else 10_000)
            }
        }
        // Plain Main (not immediate): the loop must not run before the fields below exist.
        repositoryScope.launch(Dispatchers.Main) {
            while (true) {
                // Polled even while the phone reports playing: its Spotify can be stuck on a
                // stale "playing" state too while another device has moved on.
                val localPlaying = !_currentTrack.value.isPaused && !otherDevicePlaying
                if (localPlaying) idlePollMs = IDLE_POLL_MS
                if (!com.example.spotifylyricsproxy.core.AppSettings.followOtherDevices.value) {
                    // Off: only this phone counts; drop a leftover "other device" state.
                    if (otherDevicePlaying) otherDevicePlaying = false
                } else if (webPollEnabled && !SpotifyTokenStore.needsRefresh()) pollOtherDevice()
                delay(
                    when {
                        otherDevicePlaying -> OTHER_DEVICE_POLL_MS
                        localPlaying -> LOCAL_CHECK_POLL_MS
                        else -> idlePollMs
                    }
                )
            }
        }
    }

    /**
     * The phone's Spotify can't control another Connect device's playback, so while one
     * plays, controls go through the Web API; then poll soon to pick up the result.
     */
    private fun webControl(call: suspend (com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApi, String) -> retrofit2.Response<Unit>) {
        val token = SpotifyTokenStore.getAccessToken() ?: return
        repositoryScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { call(SpotifyWebApiClient.api, SpotifyWebApiClient.authHeader(token)) }
            }.getOrNull()?.isSuccessful == true
            if (!ok) Log.w(TAG, "Web API playback control failed")
            delay(600)
            pollOtherDevice()
        }
    }

    private suspend fun pollOtherDevice() {
        val token = SpotifyTokenStore.getAccessToken() ?: return
        // A 401 means the token expired; it is renewed the next time the app opens.
        val response = runCatching {
            withContext(Dispatchers.IO) { SpotifyWebApiClient.api.getPlayer(SpotifyWebApiClient.authHeader(token)) }
        }.getOrNull()?.takeIf { it.isSuccessful } ?: return
        val state = response.body()
        val item = state?.item
        if (state != null && state.isPlaying && item != null && item.id.isNotBlank()) {
            idlePollMs = IDLE_POLL_MS
            val here = isThisDevice(state.device, item.id)
            Log.i(TAG, "Web player: device='${state.device?.name}' type=${state.device?.type} thisDevice=$here local=$localDeviceNames")
            // Playing on this device: App Remote reports it directly.
            if (here) {
                otherDevicePlaying = false
                return
            }
            otherDevicePlaying = true
            watchdogJob?.cancel()
            _playbackOptions.value = _playbackOptions.value.copy(
                isShuffling = state.shuffleState,
                repeatMode = when (state.repeatState) {
                    "track" -> RepeatMode.TRACK
                    "context" -> RepeatMode.CONTEXT
                    else -> RepeatMode.OFF
                }
            )
            val changed = item.id != _currentTrack.value.trackId
            _currentTrack.value = SpotifyTrackInfo(
                trackId = item.id,
                trackUri = item.uri,
                title = item.name,
                artist = item.artists.firstOrNull()?.name ?: "",
                album = item.album?.name ?: "",
                durationMs = item.durationMs,
                playbackPositionMs = state.progressMs ?: 0L,
                isPaused = false
            )
            if (changed) {
                lastImageUri = ""
                loadAlbumArt(item.id, null)
            }
        } else if (!otherDevicePlaying) {
            idlePollMs = (idlePollMs * 2).coerceAtMost(IDLE_POLL_MAX_MS)
        } else {
            otherDevicePlaying = false
            if (state == null || isThisDevice(state.device, state.item?.id)) {
                // No active device, or playback just moved back to this device: the web state
                // lags, so forcing "paused" here left the player stuck while music played.
                // Let the phone's own state decide.
                refreshState()
            } else {
                _currentTrack.value = _currentTrack.value.copy(
                    isPaused = true,
                    playbackPositionMs = state.progressMs ?: _currentTrack.value.playbackPositionMs
                )
            }
        }
    }

    /** Names Spotify may list this device under (the system device name, or the model). */
    private val localDeviceNames: Set<String> by lazy {
        setOfNotNull(
            runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull(),
            android.os.Build.MODEL
        ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * Whether the Web API's active device is this one. Its type alone isn't enough: a tablet
     * reports "Tablet", and the user's phone is a "Smartphone" too when this app runs on the
     * tablet. Match the name first; failing that, a phone or tablet playing the same track
     * this device's Spotify says it is playing.
     */
    private fun isThisDevice(device: com.example.spotifylyricsproxy.spotify.webapi.SpotifyDevice?, itemId: String?): Boolean {
        if (device == null) return false
        if (device.name.trim().lowercase() in localDeviceNames) return true
        val handheld = device.type.equals("Smartphone", ignoreCase = true) || device.type.equals("Tablet", ignoreCase = true)
        val local = _currentTrack.value
        return handheld && !local.isPaused && !otherDevicePlaying && itemId != null && local.trackId == itemId
    }

    private val albumArtCache = AlbumArtCache.getInstance(context)

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var albumArtJob: Job? = null
    private var lastImageUri: String = ""
    private var connectionTimeoutJob: Job? = null
    private var watchdogJob: Job? = null

    /**
     * Offline fallback: App Remote requires a network to connect, so when the
     * device is offline we read the track from Spotify's system MediaSession
     * instead. This keeps the lyric pipeline (and LRCLIB cache) working.
     */
    private val systemTrackSource = SystemMediaSessionTrackSource(context)
    private var systemTrackJob: Job? = null
    private var systemAlbumArtJob: Job? = null

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

    private var wakeBrowser: android.media.browse.MediaBrowser? = null

    /**
     * Start Spotify's process without showing its UI, by binding its MediaBrowserService
     * (the same path Android Auto and headunits use). App Remote cannot bind a Spotify that
     * is not running, but once this wakes it the next [tryConnect] succeeds.
     */
    fun wakeSpotifyInBackground() {
        if (wakeBrowser != null) return
        val browser = android.media.browse.MediaBrowser(
            context,
            android.content.ComponentName(
                SPOTIFY_PACKAGE,
                "com.spotify.mediabrowserservice.mediabrowserservice.SpotifyMediaBrowserService"
            ),
            object : android.media.browse.MediaBrowser.ConnectionCallback() {
                override fun onConnected() {
                    Log.i(TAG, "Spotify woken via MediaBrowserService")
                    release()
                    tryConnect()
                }

                override fun onConnectionFailed() {
                    Log.w(TAG, "MediaBrowserService wake refused")
                    release()
                }

                private fun release() {
                    wakeBrowser?.disconnect()
                    wakeBrowser = null
                }
            },
            null
        )
        wakeBrowser = browser
        try {
            browser.connect()
        } catch (e: Exception) {
            Log.w(TAG, "MediaBrowserService wake failed: ${e.message}")
            wakeBrowser = null
        }
    }

    fun tryConnect() {
        // Already connected — nothing to do
        if (_connectionState.value is SpotifyConnectionState.Connected) {
            if (spotifyAppRemote?.isConnected == true) {
                Log.d(TAG, "tryConnect: already connected, skipping")
                return
            }
            // The SDK dropped the connection without telling us (common after
            // hours in the background): start over.
            dropRemote()
        }

        Log.i(TAG, "tryConnect: state=${_connectionState.value}, attempting connection")

        // Start the system-MediaSession fallback immediately — do NOT wait for
        // the 15s timeout or onFailure. Offline users get the current track
        // right away; when App Remote connects, onConnected stops the fallback
        // and App Remote takes over. Idempotent, and harmless while online
        // (the same trackId means the fallback never overwrites App Remote).
        startSystemTrackFallback()

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
                // Offline devices usually hit this path: the SDK hangs in the
                // handshake and never calls onFailure. Fall back to Spotify's
                // system MediaSession so cached lyrics still work.
                startSystemTrackFallback()
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
                    // App Remote is now the source of truth; the system
                    // MediaSession keeps running only as a watchdog.
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
                            message.contains("authorization is required", ignoreCase = true) ||
                            message.contains("auth-flow", ignoreCase = true) ||
                            message.contains("user is required to use Spotify") -> {
                            SpotifyConnectionState.Error(context.getString(R.string.error_auth_required))
                        }

                        else -> SpotifyConnectionState.Error(message)
                    }
                    // App Remote cannot connect offline (its handshake needs a
                    // network). Fall back to Spotify's system MediaSession so
                    // cached lyrics still work while the device is offline.
                    // startSystemTrackFallback is idempotent and no-ops when
                    // no Spotify session exists, so try it on any failure.
                    startSystemTrackFallback()
                }
            }
        )
    }

    fun disconnect() {
        connectionTimeoutJob?.cancel()
        albumArtJob?.cancel()
        stopSystemTrackFallback()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        lastImageUri = ""
        _connectionState.value = SpotifyConnectionState.Disconnected
        _currentTrack.value = SpotifyTrackInfo()
        _albumArt.value = null
    }

    /** Start reading the current track from Spotify's system MediaSession. */
    private fun startSystemTrackFallback() {
        if (systemTrackJob?.isActive == true) return
        systemTrackSource.start()
        systemTrackJob = repositoryScope.launch {
            systemTrackSource.currentTrack.collect { track ->
                // Update on EVERY emission, not just track changes: the system
                // MediaSession also emits paused/position updates, which must
                // reach the clock so seek/pause in Spotify syncs to our UI and
                // lyrics stay aligned.
                if (track.trackId.isBlank()) return@collect
                if (otherDevicePlaying) {
                    if (track.isPaused) return@collect
                    otherDevicePlaying = false
                }
                if (_connectionState.value !is SpotifyConnectionState.Connected) {
                    _currentTrack.value = track
                } else {
                    checkRemoteAlive(track.trackId)
                }
            }
        }
        // Forward album art from the system MediaSession (embedded bitmap works
        // offline; the URI is kept for online fetch attempts).
        systemAlbumArtJob = repositoryScope.launch {
            systemTrackSource.albumArt.collect { art ->
                if (art != null && _connectionState.value !is SpotifyConnectionState.Connected) {
                    _albumArt.value = art
                }
            }
        }
    }

    /** Stop the system MediaSession fallback and clear its state. */
    private fun stopSystemTrackFallback() {
        systemTrackJob?.cancel()
        systemTrackJob = null
        systemAlbumArtJob?.cancel()
        systemAlbumArtJob = null
        systemTrackSource.stop()
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

    /**
     * Spotify's own MediaSession reports a different track than App Remote
     * (e.g. another device skipped): if that lasts, App Remote's subscription
     * is dead, so reconnect.
     */
    private fun checkRemoteAlive(systemTrackId: String) {
        watchdogJob?.cancel()
        if (systemTrackId == _currentTrack.value.trackId) return
        watchdogJob = repositoryScope.launch {
            delay(WATCHDOG_MS)
            if (_connectionState.value is SpotifyConnectionState.Connected &&
                systemTrackId != _currentTrack.value.trackId
            ) {
                Log.w(TAG, "App Remote is stale (system=$systemTrackId), reconnecting")
                dropRemote()
                tryConnect()
            }
        }
    }

    /** Forget a dead App Remote connection, keeping the system fallback running. */
    private fun dropRemote() {
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyAppRemote = null
        lastImageUri = ""
        _connectionState.value = SpotifyConnectionState.Disconnected
    }

    private var playerSubscription: com.spotify.protocol.client.PendingResult<PlayerState>? = null

    /**
     * The app came back to the foreground: a frozen process may have missed events, so
     * subscribe again (the first event is the current state) or reconnect.
     */
    fun refreshState(hard: Boolean = false) {
        if (hard) {
            // After a long stretch in the background the SDK connection can look alive while
            // no events arrive any more, so the lyric line stays frozen: start over.
            dropRemote()
            tryConnect()
        } else if (spotifyAppRemote?.isConnected == true) subscribeToPlayerState() else tryConnect()
    }

    private fun subscribeToPlayerState() {
        playerSubscription?.cancel()
        playerSubscription = spotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { playerState: PlayerState ->
                if (otherDevicePlaying) {
                    if (playerState.isPaused) return@setEventCallback
                    otherDevicePlaying = false
                }
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
                watchdogJob?.cancel()

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
                dropRemote()
                tryConnect()
            }
    }

    fun play() {
        if (otherDevicePlaying) {
            webControl { api, auth -> api.resume(auth) }
        } else if (spotifyAppRemote != null) {
            spotifyAppRemote?.playerApi?.resume()
        } else {
            // Offline fallback: drive Spotify's system MediaSession directly.
            systemTrackSource.play()
        }
    }

    fun playUri(uri: String) {
        if (uri.isBlank()) return
        spotifyAppRemote?.playerApi?.play(uri)
    }

    /** Play a track from a playlist context.
     *
     *  Plays the exact track URI first — `api.play(playlistUri)` silently
     *  fails / never calls back on some Spotify clients (observed on-device:
     *  no result/error callback fired, playback did not change), while a
     *  bare track URI always plays. After the track starts, `skipToIndex`
     *  binds the playlist as the playback context so the queue continues
     *  with the NEXT playlist track instead of Spotify autoplay (random
     *  songs).
     *
     *  When shuffle is active, temporarily disable it so the skip lands
     *  on the tapped track, then re-enable. */
    fun playContext(contextUri: String, startIndex: Int, specificTrackUri: String = "") {
        if (contextUri.isBlank()) return
        val api = spotifyAppRemote?.playerApi ?: return

        Log.i(TAG, "playContext: context=$contextUri index=$startIndex track=$specificTrackUri")
        val wasShuffling = _playbackOptions.value.isShuffling
        if (wasShuffling) {
            api.setShuffle(false).setErrorCallback { e ->
                Log.w(TAG, "playContext: setShuffle(false) failed: ${e.message}")
            }
        }

        if (specificTrackUri.isNotBlank()) {
            // Reliable path: play the exact track, then bind the playlist
            // context so playback continues within the playlist.
            api.play(specificTrackUri).setResultCallback {
                Log.i(TAG, "playContext: track playing, binding playlist context")
                if (startIndex >= 0) {
                    api.skipToIndex(contextUri, startIndex)
                        .setResultCallback {
                            Log.i(TAG, "playContext: bound to playlist index $startIndex in $contextUri")
                        }
                        .setErrorCallback { e ->
                            Log.w(TAG, "playContext: skipToIndex failed: ${e.message}")
                        }
                }
                if (wasShuffling) {
                    api.setShuffle(true).setResultCallback {
                        Log.i(TAG, "Shuffle re-enabled after playContext")
                    }
                }
            }.setErrorCallback { e ->
                Log.w(TAG, "playContext: play(track) failed: ${e.message}, falling back to playlist")
                api.play(contextUri).setResultCallback {
                    if (startIndex > 0) {
                        api.skipToIndex(contextUri, startIndex).setResultCallback {
                            Log.i(TAG, "playContext: skipped to index $startIndex in $contextUri")
                        }.setErrorCallback { e2 ->
                            Log.w(TAG, "playContext: skipToIndex failed: ${e2.message}")
                        }
                    }
                }.setErrorCallback { e2 ->
                    Log.w(TAG, "playContext: play(playlist) failed: ${e2.message}")
                }
            }
        } else {
            api.play(contextUri).setResultCallback {
                if (startIndex > 0) {
                    api.skipToIndex(contextUri, startIndex).setResultCallback {
                        Log.i(TAG, "playContext: skipped to index $startIndex in $contextUri")
                    }.setErrorCallback { e ->
                        Log.w(TAG, "playContext: skipToIndex failed: ${e.message}")
                    }
                }
            }.setErrorCallback { e ->
                Log.w(TAG, "playContext: play failed: ${e.message}")
            }
        }
    }

    fun pause() {
        if (otherDevicePlaying) {
            webControl { api, auth -> api.pause(auth) }
        } else if (spotifyAppRemote != null) {
            spotifyAppRemote?.playerApi?.pause()
        } else {
            systemTrackSource.pause()
        }
    }

    fun skipNext() {
        if (otherDevicePlaying) {
            webControl { api, auth -> api.next(auth) }
        } else if (spotifyAppRemote != null) {
            spotifyAppRemote?.playerApi?.skipNext()
        } else {
            systemTrackSource.skipNext()
        }
    }

    fun skipPrevious() {
        if (otherDevicePlaying) {
            webControl { api, auth -> api.previous(auth) }
        } else if (spotifyAppRemote != null) {
            spotifyAppRemote?.playerApi?.skipPrevious()
        } else {
            systemTrackSource.skipPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        if (otherDevicePlaying) {
            // Move our clock now so the lyrics don't wait for the next poll.
            _currentTrack.value = _currentTrack.value.copy(playbackPositionMs = positionMs)
            webControl { api, auth -> api.seek(auth, positionMs) }
        } else if (spotifyAppRemote != null) {
            spotifyAppRemote?.playerApi?.seekTo(positionMs)
        } else {
            systemTrackSource.seekTo(positionMs)
        }
    }

    fun toggleShuffle() {
        // Optimistic UI update — immediately reflect the new state
        val currentOpts = _playbackOptions.value
        val newShuffling = !currentOpts.isShuffling
        _playbackOptions.value = currentOpts.copy(isShuffling = newShuffling)
        Log.i(TAG, "toggleShuffle → $newShuffling")
        if (otherDevicePlaying) {
            webControl { api, auth -> api.shuffle(auth, newShuffling) }
            repositoryScope.launch { delay(1_500); refreshQueue() }
            return
        }
        repositoryScope.launch { delay(1_500); refreshQueue() }

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
        if (otherDevicePlaying) {
            val state = when (next) { RepeatMode.TRACK -> "track"; RepeatMode.CONTEXT -> "context"; else -> "off" }
            webControl { api, auth -> api.repeat(auth, state) }
            return
        }

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
            val bitmap = (fetchHighResAlbumArt(trackId)
                ?: fetchScdnDirectAlbumArt(imageUri)
                ?: fetchAppRemoteAlbumArt(imageUri))
                ?.let { albumArtCache.put(trackId, it) }
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
