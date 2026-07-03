package com.example.spotifylyricsproxy.ui.playback

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val clock = PlaybackClock()

    private val _estimatedPositionMs = MutableStateFlow(0L)
    val estimatedPositionMs: StateFlow<Long> = _estimatedPositionMs.asStateFlow()

    val connectionState: StateFlow<SpotifyConnectionState>
        get() = repository.connectionState

    val currentTrack: StateFlow<SpotifyTrackInfo>
        get() = repository.currentTrack

    val albumArt: StateFlow<Bitmap?>
        get() = repository.albumArt

    init {
        repository.tryConnect()
        startClock()
    }

    private fun startClock() {
        viewModelScope.launch {
            // Feed clock with latest track info
            launch {
                repository.currentTrack.collect { track ->
                    clock.update(
                        positionMs = track.playbackPositionMs,
                        paused = track.isPaused,
                        duration = track.durationMs
                    )
                }
            }
            // Tick the clock and emit estimated position
            launch {
                clock.tick(300).collect { pos ->
                    _estimatedPositionMs.value = pos
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
        repository.disconnect()
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

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
