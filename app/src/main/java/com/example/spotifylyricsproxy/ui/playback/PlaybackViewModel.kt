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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private var fullTranslationJob: kotlinx.coroutines.Job? = null

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
        observeLyricsForFullTranslation()
        autoReconnectOnFailure()
        viewModelScope.launch {
            ConnectivityObserver.observe(getApplication()).collect { state ->
                currentMeteredState = state
                LyricsForegroundService.setMeteredState(state)
            }
        }
        // Auto-search on startup: wait for App Remote to connect and provide a
        // valid track, then search for lyrics. Uses a reactive observe approach
        // so it works regardless of timing (App Remote may fire late).
        viewModelScope.launch {
            // Give the initial connection a moment
            delay(2000)
            val track: SpotifyTrackInfo = repository.currentTrack.first { t ->
                t.trackId.isNotBlank() && t.title.isNotBlank()
            }
            if (lyricsRepo.parsedLyrics.value.isEmpty()) {
                Log.i(TAG, "Auto-search triggered for '${track.title}' (${track.trackId.take(8)})")
                reSearchLyrics()
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
                        // Pass the specific track URI for reliable playback.
                        repository.playContext(req.uri, req.contextTrackIndex, req.trackUri)
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
                    // Do NOT call lyricsRepo.updatePosition() here.
                    // The LyricsForegroundService owns the single source of truth for
                    // position tracking and lyric sync. Having two independent clocks
                    // (one in the VM, one in the service) both calling updatePosition()
                    // causes the currentLine to twitch when the app returns from background
                    // — the VM's fresh clock and the service's drifted clock fight over
                    // which position to use.
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
        val track = repository.currentTrack.value
        viewModelScope.launch {
            lyricsRepo.reSearch(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )
            // Always show the picker when there are multiple candidates.
            // The user explicitly asked to re-search — they want to see all options.
            val count = lyricsRepo.candidates.value.size
            if (count > 1) {
                _showCandidatePicker.value = true
            }
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
        pendingFetchTrack = null
        val t = repository.currentTrack.value
        if (t.trackId.isBlank()) return
        viewModelScope.launch {
            lyricsRepo.fetchLyrics(
                trackId = t.trackId, title = t.title, artist = t.artist,
                album = t.album, durationMs = t.durationMs, forceOnline = true
            )
        }
    }

    /** User declined mobile data — try cache only. */
    fun dismissMobileDataDialog() {
        _showMobileDataDialog.value = false
        pendingFetchTrack = null
        val t = repository.currentTrack.value
        if (t.trackId.isBlank()) return
        viewModelScope.launch {
            lyricsRepo.fetchLyrics(
                trackId = t.trackId, title = t.title, artist = t.artist,
                album = t.album, durationMs = t.durationMs, forceOnline = false
            )
        }
    }

    /** Select a candidate by index and apply it. */
    fun selectCandidate(index: Int) {
        viewModelScope.launch {
            lyricsRepo.selectCandidate(index)
            _showCandidatePicker.value = false
            // When user manually selects a different source, reset chinese form
            // to original so the new lyrics are shown as-is from the source.
            LyricDisplayPreferences.setChineseForm("original")
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
            lyricsRepo.rejectCurrentMatch("User rejected manually")
        }
    }

    // ---- Translation ----

    fun setTranslationEnabled(enabled: Boolean) {
        _isTranslationEnabled.value = enabled
        if (enabled) {
            // Sync chineseForm with the current translation target
            syncChineseFormFromTarget(_targetTranslationLang.value)
            // Re-translate the current lyrics with the active target.
            val lines = lyricsRepo.parsedLyrics.value
            if (lines.isNotEmpty()) {
                translationMap = emptyMap()
                translateLyricsInFull(lines)
            }
        } else {
            fullTranslationJob?.cancel()
            translationJob?.cancel()
            translationMap = emptyMap()
            _translatedLine.value = null
            _detectedLyricsLang.value = null
            _fullTranslation.value = null
            // Reset chinese form to original when translation is off
            LyricDisplayPreferences.setChineseForm("original")
        }
    }

    fun setTranslationTargetLang(lang: String) {
        _targetTranslationLang.value = lang
        TranslationPrefs.saveTargetLang(getApplication(), lang)
        syncChineseFormFromTarget(lang)
        // Re-translate in the new target language.
        if (_isTranslationEnabled.value) {
            translationMap = emptyMap()
            _translatedLine.value = null
            val lines = lyricsRepo.parsedLyrics.value
            if (lines.isNotEmpty()) {
                translateLyricsInFull(lines)
            }
        }
    }

    private fun syncChineseFormFromTarget(lang: String) {
        when (lang) {
            "zh-TW" -> LyricDisplayPreferences.setChineseForm("traditional")
            else -> LyricDisplayPreferences.setChineseForm("simplified")
        }
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
                // Full translation covers every line; only fall back to per-line
                // translation when the full pass is not running or failed.
                if (fullTranslationJob?.isActive == true) {
                    return@collect
                }
                translationJob?.cancel()
                _translatedLine.value = null
                translateCurrentLine(line.text)
            }
        }
    }

    /** Translate the entire lyrics block in one call so context is preserved. */
    private fun observeLyricsForFullTranslation() {
        viewModelScope.launch {
            lyricsRepo.parsedLyrics.collect { lines ->
                if (lines.isNotEmpty() && _isTranslationEnabled.value) {
                    translateLyricsInFull(lines)
                }
            }
        }
    }

    /**
     * Whether a translation pass is meaningful for [detected] -> [target].
     * zh -> zh-TW still needs work (simplified -> traditional conversion),
     * but nothing to do when the lyrics are already traditional.
     */
    private fun needsTranslation(detected: String, target: String): Boolean {
        val d = translationService.normalizeLang(detected)
        val t = translationService.normalizeLang(target)
        if (d != t) return true
        val tradTarget = target == "zh-TW" || target == "zh-Hant" || target == "zh-HK"
        val detectedAlreadyTrad =
            detected == "zh-TW" || detected == "zh-Hant" || detected == "zh-HK"
        return tradTarget && !detectedAlreadyTrad
    }

    /** Translate all lines together (better context than line-by-line). */
    private fun translateLyricsInFull(lines: List<com.example.spotifylyricsproxy.core.model.LrcLine>) {
        fullTranslationJob?.cancel()
        fullTranslationJob = viewModelScope.launch {
            val target = _targetTranslationLang.value
            val fullText = lines.joinToString("\n") { it.text }
            if (fullText.isBlank()) return@launch
            try {
                val detected = translationService.detectLanguage(fullText)
                _detectedLyricsLang.value = detected
                if (detected == null || !needsTranslation(detected, target)) {
                    // Same language — no translation needed.
                    _fullTranslation.value = null
                    _translatedLine.value = null
                    return@launch
                }
                val translated = translationService.translate(fullText, detected, target)
                if (translated == null) {
                    _fullTranslation.value = null
                    _translatedLine.value = null
                    return@launch
                }
                val translatedLines = translated.split("\n")
                if (translatedLines.size == lines.size) {
                    translationMap = lines.indices.associate { i -> lines[i].startMs to translatedLines[i] }
                    _fullTranslation.value = translated
                    val cur = lyricsRepo.currentLine.value
                    _translatedLine.value = cur?.let { translationMap[it.startMs] }
                    Log.i(TAG, "Full translation done: ${lines.size} lines -> $target")
                } else {
                    // Line count mismatch (translator merged/split lines): fall back per-line.
                    Log.w(TAG, "Full translation line mismatch (${translatedLines.size} vs ${lines.size}), falling back per-line")
                    _fullTranslation.value = null
                    translationMap = emptyMap()
                    val cur = lyricsRepo.currentLine.value
                    if (cur != null) translateCurrentLine(cur.text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Full translation failed", e)
                _fullTranslation.value = null
                _translatedLine.value = null
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
                if (detected == null || !needsTranslation(detected, target)) {
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
