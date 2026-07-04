package com.example.spotifylyricsproxy.ui.playback

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.lyrics.LyricsRepository
import com.example.spotifylyricsproxy.notification.LyricsForegroundService
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.playback.clock.PlaybackClock
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

    private val clientId = com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifylyricsproxy://callback"

    private val repository = SpotifyRemoteRepository(application, clientId, redirectUri)
    private val db = AppDatabase.getInstance(application)
    private val lyricsRepo = LyricsRepository(db)
    private val clock = PlaybackClock()

    private val _estimatedPositionMs = MutableStateFlow(0L)
    val estimatedPositionMs: StateFlow<Long> = _estimatedPositionMs.asStateFlow()

    val connectionState: StateFlow<SpotifyConnectionState>
        get() = repository.connectionState

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

    /** True when the candidate selection dialog should be shown. */
    private val _showCandidatePicker = MutableStateFlow(false)
    val showCandidatePicker: StateFlow<Boolean> = _showCandidatePicker.asStateFlow()

    private var lastFetchedTrackId: String = ""
    private var authTokenReceived: Boolean = false

    init {
        repository.tryConnect()
        startClock()
        observeTrackChanges()
        observePlayRequests()
    }

    private fun observePlayRequests() {
        viewModelScope.launch {
            PlayRequestHolder.requestUri.collect { uri ->
                if (!uri.isNullOrBlank()) {
                    repository.playUri(uri)
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
                if (track.trackId.isNotEmpty() && track.trackId != lastFetchedTrackId) {
                    LyricsForegroundService.start(getApplication())
                    lastFetchedTrackId = track.trackId
                    lyricsRepo.reset()
                    viewModelScope.launch {
                        lyricsRepo.fetchLyrics(
                            trackId = track.trackId,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = track.durationMs
                        )
                    }
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

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
