package com.example.spotifylyricsproxy.spotify.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
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
        const val AUTH_REQUEST_CODE = 0x10
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(
        SpotifyConnectionState.Disconnected
    )
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _currentTrack = MutableStateFlow(SpotifyTrackInfo())
    val currentTrack: StateFlow<SpotifyTrackInfo> = _currentTrack.asStateFlow()

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
                connectAfterAuth()
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

    private fun connectAfterAuth() {
        if (_connectionState.value is SpotifyConnectionState.Connecting ||
            _connectionState.value is SpotifyConnectionState.Connected) {
            return
        }

        _connectionState.value = SpotifyConnectionState.Connecting

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(context, connectionParams,
            object : com.spotify.android.appremote.api.Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    Log.i(TAG, "Connected to Spotify")
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
                        message.contains("not logged in", ignoreCase = true) ->
                            SpotifyConnectionState.SpotifyNotLoggedIn
                        message.contains("UserNotAuthorized") ||
                        message.contains("user is required to use Spotify") ->
                            SpotifyConnectionState.Error("需要先授权 — 请重试")
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
