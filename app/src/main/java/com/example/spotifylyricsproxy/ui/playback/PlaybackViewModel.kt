package com.example.spotifylyricsproxy.ui.playback

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.lyrics.LyricsRepository
import com.example.spotifylyricsproxy.lyrics.TranslationService
import com.example.spotifylyricsproxy.notification.LyricsForegroundService
import com.example.spotifylyricsproxy.ui.theme.TranslationPrefs
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.util.ConnectivityObserver
import com.example.spotifylyricsproxy.util.MeteredState
import com.example.spotifylyricsproxy.playback.clock.PlaybackClock
import com.example.spotifylyricsproxy.spotify.remote.PlaybackOptions
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyRemoteRepository
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackVM"
    }

    private val clientId = com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifylyricsproxy://callback"

    private val repository = SpotifyRemoteRepository(application, clientId, redirectUri)
    private val db = AppDatabase.getInstance(application)
    private val lyricsRepo = LyricsRepository.getInstance(db)
    private val clock = PlaybackClock()

    private val _estimatedPositionMs = MutableStateFlow(0L)
    val estimatedPositionMs: StateFlow<Long> = _estimatedPositionMs.asStateFlow()

    val connectionState: StateFlow<SpotifyConnectionState>
        get() = repository.connectionState

    val playbackOptions: StateFlow<PlaybackOptions>
        get() = repository.playbackOptions

    val currentTrack: StateFlow<SpotifyTrackInfo>
        get() = repository.currentTrack

    val albumArt: StateFlow<Bitmap?>
        get() = repository.albumArt

    val currentLyricLine: StateFlow<LrcLine?>
        get() = lyricsRepo.currentLine

    val parsedLyrics: StateFlow<List<LrcLine>>
        get() = lyricsRepo.parsedLyrics

    val lyricStatus: StateFlow<LyricStatus>
        get() = lyricsRepo.lyricStatus

    val candidates: StateFlow<List<LyricCandidate>>
        get() = lyricsRepo.candidates

    val currentOffsetMs: StateFlow<Long>
        get() = lyricsRepo.currentOffsetMs

    val lyricSource: StateFlow<String>
        get() = lyricsRepo.lyricSource

    // ---- Translation state ----

    private val translationService = TranslationService(application)

    private val _translatedLine = MutableStateFlow<String?>(null)
    val translatedLine: StateFlow<String?> = _translatedLine.asStateFlow()

    private val _fullTranslation = MutableStateFlow<String?>(null)
    private var translationMap: Map<Long, String> = emptyMap()

    private var translationJob: kotlinx.coroutines.Job? = null

    private val _isTranslationEnabled = MutableStateFlow(false)
    val isTranslationEnabled: StateFlow<Boolean> = _isTranslationEnabled.asStateFlow()

    private val _targetTranslationLang = MutableStateFlow(TranslationPrefs.loadTargetLang(application))
    val targetTranslationLang: StateFlow<String> = _targetTranslationLang.asStateFlow()

    private val _detectedLyricsLang = MutableStateFlow<String?>(null)
    val detectedLyricsLang: StateFlow<String?> = _detectedLyricsLang.asStateFlow()

    /** True when the candidate selection dialog should be shown. */
    private val _showCandidatePicker = MutableStateFlow(false)
    val showCandidatePicker: StateFlow<Boolean> = _showCandidatePicker.asStateFlow()

    private var lastFetchedTrackId: String = ""
    private var authTokenReceived: Boolean = false
    private var pendingConnectionOnResume: Boolean = false

    // ---- Mobile data / connectivity ----

    private val _showMobileDataDialog = MutableStateFlow(false)
    val showMobileDataDialog: StateFlow<Boolean> = _showMobileDataDialog.asStateFlow()

    /** Track info saved while the user is being asked about mobile data. */
    private var pendingFetchTrack: SpotifyTrackInfo? = null
    private var currentMeteredState: MeteredState = MeteredState.NONE

    init {
        repository.tryConnect()
        startClock()
        observeTrackChanges()
        observePlayRequests()
        observeCurrentLineForTranslation()
        autoReconnectOnFailure()
        viewModelScope.launch {
            ConnectivityObserver.observe(getApplication()).collect { state ->
                currentMeteredState = state
                LyricsForegroundService.setMeteredState(state)
            }
        }
    }

    /**
     * Watches connection state. If initial connection fails (e.g. Spotify not
     * running), auto-launches Spotify after a short delay. This covers both
     * app startup and background-to-foreground transitions.
     */
    private fun autoReconnectOnFailure() {
        viewModelScope.launch {
            // Wait a moment for the initial connection attempt to resolve
            kotlinx.coroutines.delay(4000)
            val state = repository.connectionState.value
            if (state is SpotifyConnectionState.Error ||
                state is SpotifyConnectionState.Disconnected ||
                state is SpotifyConnectionState.SpotifyNotInstalled ||
                state is SpotifyConnectionState.SpotifyNotLoggedIn
            ) {
                android.util.Log.i("PlaybackVM", "autoReconnect: state=$state, launching Spotify")
                openSpotifyAndConnect()
            }
        }
    }

    private fun observePlayRequests() {
        viewModelScope.launch {
            PlayRequestHolder.request.collect { req ->
                if (req != null && req.uri.isNotBlank()) {
                    if (req.contextTrackIndex >= 0) {
                        // Play context (playlist/album) starting at this track.
                        // App Remote queues the full playlist then skips to the
                        // selected track index.
                        repository.playContext(req.uri, req.contextTrackIndex)
                    } else {
                        repository.playUri(req.uri)
                    }
                    PlayRequestHolder.consume()
                }
            }
        }
    }

    fun handleAuthResponse(response: AuthorizationResponse) {
        if (authTokenReceived) return
        authTokenReceived = true
        android.util.Log.i("PlaybackVM", "Auth token received")
        repository.tryConnect()
    }

    fun handleRestoredToken(token: String) {
        android.util.Log.i("PlaybackVM", "Restored token, reconnecting")
        repository.tryConnect()
    }

    private fun startClock() {
        viewModelScope.launch {
            launch {
                repository.currentTrack.collect { track ->
                    if (track.trackId.isNotEmpty()) {
                        clock.update(
                            positionMs = track.playbackPositionMs,
                            paused = track.isPaused,
                            duration = track.durationMs
                        )
                    }
                }
            }
            launch {
                clock.tick(300).collect { pos ->
                    _estimatedPositionMs.value = pos
                    lyricsRepo.updatePosition(pos)
                }
            }
        }
    }

    private fun observeTrackChanges() {
        viewModelScope.launch {
            repository.currentTrack.collect { track ->
                Log.d(TAG, "observeTrackChanges: trackId=${track.trackId}, lastFetched=$lastFetchedTrackId, isEmpty=${track.trackId.isEmpty()}")
                if (track.trackId.isNotEmpty() && track.trackId != lastFetchedTrackId) {
                    Log.i(TAG, "Track changed → fetching lyrics for ${track.title}")
                    LyricsForegroundService.start(getApplication())
                    lastFetchedTrackId = track.trackId

                    // Lyrics fetching is handled entirely by the foreground service.
                    // The ViewModel starts the service above and reads the shared
                    // LyricsRepository singleton for display. Do NOT call
                    // lyricsRepo.fetchLyrics() here — that would race with the
                    // service and can overwrite successfully-loaded lyrics with
                    // a failed search result from a different source order.
                }
            }
        }
    }

    fun authorize(activity: Activity) {
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )
            .setScopes(arrayOf("app-remote-control", "playlist-read-private", "playlist-read-collaborative", "user-read-private"))
            .build()
        SpotifyAuthHolder.startAuth?.invoke(request)
    }

    fun disconnect() {
        LyricsForegroundService.stop(getApplication())
        repository.disconnect()
        lyricsRepo.reset()
        lastFetchedTrackId = ""
    }

    fun connect() {
        android.util.Log.i("PlaybackVM", "connect() called — force reconnecting")
        // Try a fresh connection. If Spotify isn't running, the SDK will
        // call onFailure quickly; we also auto-launch Spotify so the user
        // doesn't have to do it manually.
        pendingConnectionOnResume = true
        repository.forceReconnect()
        // If connection fails (e.g. Spotify not running), auto-open Spotify
        // so the user doesn't get stuck with "no response".
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            val state = repository.connectionState.value
            if (state is SpotifyConnectionState.Disconnected ||
                state is SpotifyConnectionState.Error ||
                state is SpotifyConnectionState.SpotifyNotInstalled ||
                state is SpotifyConnectionState.SpotifyNotLoggedIn
            ) {
                android.util.Log.i("PlaybackVM", "connect() timed out (state=$state), launching Spotify")
                openSpotifyAndConnect()
            }
        }
    }

    fun openSpotifyAndConnect() {
        val app = getApplication<Application>()
        val launchIntent = app.packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            pendingConnectionOnResume = true
            android.util.Log.i("PlaybackVM", "Launched Spotify, will retry connection on resume")
        }
        // Don't try connecting now — when the user returns from Spotify,
        // onResume() will pick up the pending flag and attempt a fresh connection.
    }

    /** Called from MainActivity.onResume — gentle auto-reconnect when returning
     *  to foreground (e.g. after opening Spotify). */
    fun onResume() {
        if (pendingConnectionOnResume) {
            pendingConnectionOnResume = false
            android.util.Log.i("PlaybackVM", "onResume: retrying connection after opening Spotify")
            repository.forceReconnect()
        } else if (repository.connectionState.value is SpotifyConnectionState.Disconnected ||
                repository.connectionState.value is SpotifyConnectionState.Error
        ) {
            android.util.Log.i("PlaybackVM", "onResume: gentle reconnect attempt")
            repository.tryConnect()
        }
    }

    fun togglePlayPause() {
        if (repository.currentTrack.value.isPaused) {
            repository.play()
        } else {
            repository.pause()
        }
    }

    fun skipNext() {
        repository.skipNext()
    }

    fun skipPrevious() {
        repository.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        repository.seekTo(positionMs)
        clock.update(
            positionMs = positionMs,
            paused = repository.currentTrack.value.isPaused,
            duration = repository.currentTrack.value.durationMs
        )
        _estimatedPositionMs.value = positionMs
        lyricsRepo.updatePosition(positionMs)
    }

    // ---- Playback mode controls ----

    fun toggleShuffle() {
        repository.toggleShuffle()
    }

    fun cycleRepeat() {
        repository.cycleRepeat()
    }

    // ---- Offset controls ----

    fun adjustOffset(deltaMs: Long) {
        viewModelScope.launch {
            lyricsRepo.adjustOffset(deltaMs)
        }
    }

    fun resetOffset() {
        viewModelScope.launch {
            lyricsRepo.setOffsetMs(0)
        }
    }

    // ---- Manual re-match ----

    /** Trigger a fresh search and show the candidate picker if multiple candidates found. */
    fun reSearchLyrics() {
        viewModelScope.launch {
            lyricsRepo.reSearch()
            _showCandidatePicker.value = lyricsRepo.candidates.value.size > 1
        }
    }

    /** Dismiss the candidate picker without selecting. */
    fun dismissCandidatePicker() {
        _showCandidatePicker.value = false
    }

    // ---- Mobile data confirmation ----

    /** User confirmed: fetch lyrics online even on mobile data. */
    fun confirmMobileDataFetch() {
        _showMobileDataDialog.value = false
        val track = pendingFetchTrack ?: return
        pendingFetchTrack = null
        viewModelScope.launch {
            lyricsRepo.fetchLyrics(
                trackId = track.trackId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                forceOnline = true
            )
        }
    }

    /** User declined: set restricted status, don't fetch. */
    fun dismissMobileDataDialog() {
        _showMobileDataDialog.value = false
        pendingFetchTrack = null
        viewModelScope.launch {
            lyricsRepo.setMobileDataRestricted()
        }
    }

    /** Select a candidate by index and apply it. */
    fun selectCandidate(index: Int) {
        viewModelScope.launch {
            lyricsRepo.selectCandidate(index)
            _showCandidatePicker.value = false
        }
    }

    /** Show candidate picker for manual selection. */
    fun showCandidateSelection() {
        _showCandidatePicker.value = true
    }

    // ---- Blacklist ----

    /** Mark current match as wrong and blacklist it. */
    fun rejectCurrentMatch() {
        viewModelScope.launch {
            lyricsRepo.rejectCurrentMatch("用户手动标记为错误")
        }
    }

    // ---- Translation ----

    fun setTranslationEnabled(enabled: Boolean) {
        _isTranslationEnabled.value = enabled
        if (!enabled) {
            _translatedLine.value = null
            _detectedLyricsLang.value = null
        }
    }

    fun setTranslationTargetLang(lang: String) {
        _targetTranslationLang.value = lang
        TranslationPrefs.saveTargetLang(getApplication(), lang)
    }

    // ---- Manual Lyrics Import ----

    /** Import manually provided LRC text for the current track. */
    fun importManualLyrics(lrcText: String) {
        val track = currentTrack.value
        viewModelScope.launch {
            lyricsRepo.saveManualLyrics(
                trackId = track.trackId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                lrcText = lrcText
            )
        }
    }

    private fun observeCurrentLineForTranslation() {
        viewModelScope.launch {
            lyricsRepo.currentLine.collect { line ->
                if (line == null || !_isTranslationEnabled.value) {
                    _translatedLine.value = null
                    return@collect
                }
                val tlyricText = translationMap[line.startMs]
                if (tlyricText != null) {
                    _translatedLine.value = tlyricText
                    return@collect
                }
                translationJob?.cancel()
                _translatedLine.value = null
                translateCurrentLine(line.text)
            }
        }
    }

    /** Translate a single line of lyrics on the IO dispatcher. */
    private fun translateCurrentLine(text: String) {
        translationJob = viewModelScope.launch {
            val target = _targetTranslationLang.value
            try {
                val detected = translationService.detectLanguage(text)
                _detectedLyricsLang.value = detected
                if (detected == null || detected == target) {
                    _translatedLine.value = null
                    return@launch
                }
                val translated = translationService.translate(text, detected, target)
                _translatedLine.value = translated
            } catch (_: Exception) {
                _translatedLine.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
        translationService.onCleared()
    }
}
