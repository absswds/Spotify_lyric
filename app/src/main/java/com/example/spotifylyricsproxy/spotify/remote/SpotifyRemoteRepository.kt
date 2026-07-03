package com.example.spotifylyricsproxy.spotify.remote

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpotifyRemoteRepository(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String
) {
    companion object {
        private const val TAG = "SpotifyRemoteRepo"
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(
        SpotifyConnectionState.Disconnected
    )
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _currentTrack = MutableStateFlow(SpotifyTrackInfo())
    val currentTrack: StateFlow<SpotifyTrackInfo> = _currentTrack.asStateFlow()

    fun connect() {
        if (_connectionState.value is SpotifyConnectionState.Connecting ||
            _connectionState.value is SpotifyConnectionState.Connected) {
            return
        }

        _connectionState.value = SpotifyConnectionState.Connecting

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, connectionParams,
            object : com.spotify.android.appremote.api.Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    spotifyAppRemote = appRemote
                    _connectionState.value = SpotifyConnectionState.Connected
                    subscribeToPlayerState()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(TAG, "Connection failed", throwable)
                    val message = throwable.message ?: "Unknown error"
                    _connectionState.value = when {
                        message.contains("not installed", ignoreCase = true) ||
                        message.contains("unavailable", ignoreCase = true) ->
                            SpotifyConnectionState.SpotifyNotInstalled
                        message.contains("not logged in", ignoreCase = true) ||
                        message.contains("authentication", ignoreCase = true) ||
                        message.contains("auth", ignoreCase = true) ->
                            SpotifyConnectionState.SpotifyNotLoggedIn
                        else ->
                            SpotifyConnectionState.Error(message)
                    }
                }
            })
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        _connectionState.value = SpotifyConnectionState.Disconnected
        _currentTrack.value = SpotifyTrackInfo()
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { playerState: PlayerState ->
                _currentTrack.value = SpotifyTrackInfo(
                    trackId = playerState.track?.uri?.split(":")?.lastOrNull() ?: "",
                    trackUri = playerState.track?.uri ?: "",
                    title = playerState.track?.name ?: "",
                    artist = playerState.track?.artist?.name ?: "",
                    album = playerState.track?.album?.name ?: "",
                    durationMs = playerState.track?.duration ?: 0,
                    playbackPositionMs = playerState.playbackPosition,
                    isPaused = playerState.isPaused,
                    imageUri = playerState.track?.imageUri?.raw ?: ""
                )
            }
            ?.setErrorCallback { error: Throwable ->
                Log.e(TAG, "Player state subscription error", error)
            }
    }

    fun play() {
        spotifyAppRemote?.playerApi?.resume()
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

    fun isConnected(): Boolean =
        _connectionState.value is SpotifyConnectionState.Connected

    fun isPlaying(): Boolean =
        isConnected() && !_currentTrack.value.isPaused && _currentTrack.value.trackId.isNotEmpty()
}
