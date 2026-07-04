package com.example.spotifylyricsproxy.ui.playback

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.lyrics.LyricsRepository
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.notification.LyricsForegroundService
import com.example.spotifylyricsproxy.playback.clock.PlaybackClock
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyRemoteRepository
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private var lastFetchedTrackId: String = ""

    init {
        repository.tryConnect()
        startClock()
        observeTrackChanges()
    }

    private fun startClock() {
        viewModelScope.launch {
            launch {
                repository.currentTrack.collect { track ->
                    // Skip empty initial state to avoid resetting to 0
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
                android.util.Log.i("LyricsVM", "Track changed: id=${track.trackId}, title=${track.title}, artist=${track.artist}")
                if (track.trackId.isNotEmpty() && track.trackId != lastFetchedTrackId) {
                    LyricsForegroundService.start(getApplication())
                    android.util.Log.i("LyricsVM", "Fetching lyrics for: ${track.title} - ${track.artist}")
                    lastFetchedTrackId = track.trackId
                    lyricsRepo.reset()
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

    fun authorize(activity: Activity) {
        repository.authorize(activity)
    }

    fun onAuthResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return repository.handleAuthResponse(requestCode, resultCode, data)
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

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
