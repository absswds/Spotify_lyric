package com.example.spotifylyricsproxy.ui.playback

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.lyrics.LyricsRepository
import com.example.spotifylyricsproxy.notification.LyricsForegroundService
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.playback.clock.PlaybackClock
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyRemoteRepository
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistTrackItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackPlaylistTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)

internal fun List<SpotifyPlaylistTrackItem>.toPlaybackPlaylistTracks(): List<PlaybackPlaylistTrack> =
    mapNotNull { item ->
        val track = item.track ?: return@mapNotNull null
        if (track.id.isBlank() || track.uri.isBlank()) return@mapNotNull null
        PlaybackPlaylistTrack(
            id = track.id,
            uri = track.uri,
            title = track.name,
            artist = track.artists.joinToString(", ") { it.name },
            durationMs = track.durationMs
        )
    }

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val clientId = com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifylyricsproxy://callback"

    private val repository = SpotifyRemoteRepository(application, clientId, redirectUri)
    private val db = AppDatabase.getInstance(application)
    private val lyricsRepo = LyricsRepository(db)
    private val clock = PlaybackClock()

    private val _estimatedPositionMs = MutableStateFlow(0L)
    val estimatedPositionMs: StateFlow<Long> = _estimatedPositionMs.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylistItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylistItem>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<SpotifyPlaylistItem?>(null)
    val selectedPlaylist: StateFlow<SpotifyPlaylistItem?> = _selectedPlaylist.asStateFlow()

    private val _playlistTracks = MutableStateFlow<List<PlaybackPlaylistTrack>>(emptyList())
    val playlistTracks: StateFlow<List<PlaybackPlaylistTrack>> = _playlistTracks.asStateFlow()

    private val _playlistLoading = MutableStateFlow(false)
    val playlistLoading: StateFlow<Boolean> = _playlistLoading.asStateFlow()

    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError: StateFlow<String?> = _playlistError.asStateFlow()

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
    private var authTokenReceived: Boolean = false

    init {
        repository.tryConnect()
        startClock()
        observeTrackChanges()
    }

    fun handleAuthResponse(response: AuthorizationResponse) {
        if (authTokenReceived) return
        authTokenReceived = true
        android.util.Log.i("PlaybackVM", "Auth token received")
        repository.tryConnect()
        loadPlaylists()
    }

    /** Called when a persisted token is restored on app startup. */
    fun handleRestoredToken(token: String) {
        android.util.Log.i("PlaybackVM", "Restored token, reconnecting and loading playlists")
        repository.tryConnect()
        loadPlaylists()
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

    fun loadPlaylists() {
        val token = SpotifyAuthHolder.accessToken
        if (token.isNullOrBlank()) {
            _playlistError.value = "需要先连接 Spotify"
            return
        }

        _playlistLoading.value = true
        _playlistError.value = null
        viewModelScope.launch {
            try {
                val response = SpotifyWebApiClient.api.getPlaylists(
                    auth = SpotifyWebApiClient.authHeader(token)
                )
                _playlists.value = response.items
                if (_selectedPlaylist.value == null) {
                    response.items.firstOrNull()?.let { selectPlaylist(it) }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackVM", "Failed to load playlists", e)
                _playlistError.value = "歌单加载失败"
            } finally {
                _playlistLoading.value = false
            }
        }
    }

    fun selectPlaylist(playlist: SpotifyPlaylistItem) {
        val token = SpotifyAuthHolder.accessToken
        if (token.isNullOrBlank()) {
            _playlistError.value = "需要先连接 Spotify"
            return
        }

        _selectedPlaylist.value = playlist
        _playlistTracks.value = emptyList()
        _playlistLoading.value = true
        _playlistError.value = null
        viewModelScope.launch {
            try {
                val response = SpotifyWebApiClient.api.getPlaylistTracks(
                    auth = SpotifyWebApiClient.authHeader(token),
                    playlistId = playlist.id
                )
                _playlistTracks.value = response.items.toPlaybackPlaylistTracks()
            } catch (e: Exception) {
                android.util.Log.e("PlaybackVM", "Failed to load playlist tracks", e)
                _playlistError.value = "歌曲加载失败"
            } finally {
                _playlistLoading.value = false
            }
        }
    }

    fun playTrack(track: PlaybackPlaylistTrack) {
        repository.playUri(track.uri)
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
