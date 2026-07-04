package com.example.spotifylyricsproxy.spotify.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyTokenStore
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

class SpotifyRemoteRepository(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String,
    private val albumArtDimension: Image.Dimension = Image.Dimension.LARGE
) {
    companion object {
        private const val TAG = "SpotifyRemoteRepo"
        const val AUTH_REQUEST_CODE = 0x10
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageHttpClient = OkHttpClient()

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var albumArtJob: Job? = null
    private var lastImageUri: String = ""

    private val _connectionState = MutableStateFlow<SpotifyConnectionState>(
        SpotifyConnectionState.Disconnected
    )
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _currentTrack = MutableStateFlow(SpotifyTrackInfo())
    val currentTrack: StateFlow<SpotifyTrackInfo> = _currentTrack.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

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
        if (_connectionState.value is SpotifyConnectionState.Connecting ||
            _connectionState.value is SpotifyConnectionState.Connected
        ) {
            return
        }

        _connectionState.value = SpotifyConnectionState.Connecting

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(
            context,
            connectionParams,
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
                            message.contains("unavailable", ignoreCase = true) -> {
                            SpotifyConnectionState.SpotifyNotInstalled
                        }

                        message.contains("not logged in", ignoreCase = true) -> {
                            SpotifyConnectionState.SpotifyNotLoggedIn
                        }

                        message.contains("UserNotAuthorized") ||
                            message.contains("user is required to use Spotify") -> {
                            SpotifyConnectionState.Error("需要先授权，请重试")
                        }

                        else -> SpotifyConnectionState.Error(message)
                    }
                }
            }
        )
    }

    fun disconnect() {
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

    private fun loadAlbumArt(trackId: String, imageUri: ImageUri?) {
        albumArtJob?.cancel()
        if (trackId.isBlank() && imageUri == null) {
            _albumArt.value = null
            return
        }

        albumArtJob = repositoryScope.launch {
            val bitmap = fetchHighResAlbumArt(trackId) ?: fetchAppRemoteAlbumArt(imageUri)
            if (trackId == _currentTrack.value.trackId) {
                _albumArt.value = bitmap
            }
        }
    }

    private suspend fun fetchHighResAlbumArt(trackId: String): Bitmap? {
        if (trackId.isBlank()) return null

        val accessToken = SpotifyTokenStore.getAccessToken() ?: return null
        val imageUrl = runCatching {
            withContext(Dispatchers.IO) {
                SpotifyWebApiClient.api.getTrack(
                    SpotifyWebApiClient.authHeader(accessToken),
                    trackId
                )
            }?.album?.images
                ?.filter { it.url.isNotBlank() }
                ?.maxByOrNull { it.width ?: 0 }
                ?.url
        }.onFailure {
            Log.w(TAG, "Unable to query Spotify Web API album art", it)
        }.getOrNull() ?: return null

        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(imageUrl).build()
                imageHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
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
