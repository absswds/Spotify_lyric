package com.example.spotifylyricsproxy.ui.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.ui.playback.PlayRequestHolder
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlaylistTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val imageUrl: String? = null,
    val playlistIndex: Int = -1
)

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaylistVM"
        private const val PAGE_LIMIT = 100
    }

    private val _playlists = MutableStateFlow<List<SpotifyPlaylistItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylistItem>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<SpotifyPlaylistItem?>(null)
    val selectedPlaylist: StateFlow<SpotifyPlaylistItem?> = _selectedPlaylist.asStateFlow()

    private val _tracks = MutableStateFlow<List<PlaylistTrack>>(emptyList())
    val tracks: StateFlow<List<PlaylistTrack>> = _tracks.asStateFlow()

    private val _loadingPlaylists = MutableStateFlow(false)
    val loadingPlaylists: StateFlow<Boolean> = _loadingPlaylists.asStateFlow()

    private val _loadingTracks = MutableStateFlow(false)
    val loadingTracks: StateFlow<Boolean> = _loadingTracks.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _trackProgress = MutableStateFlow<Pair<Int, Int>?>(null) // (loaded, total)
    val trackProgress: StateFlow<Pair<Int, Int>?> = _trackProgress.asStateFlow()

    init {
        // Try to load playlists immediately; if token is null, trigger auth
        triggerAuthIfNeeded()
        loadPlaylists()
        // Retry when token becomes available after a fresh app start
        viewModelScope.launch {
            while (SpotifyAuthHolder.accessToken == null) {
                delay(500)
            }
            loadPlaylists()
        }
    }

    private fun triggerAuthIfNeeded() {
        if (SpotifyAuthHolder.accessToken != null) return
        Log.i(TAG, "Token missing — requesting Spotify auth")
        val request = com.spotify.sdk.android.auth.AuthorizationRequest.Builder(
            com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID,
            com.spotify.sdk.android.auth.AuthorizationResponse.Type.TOKEN,
            "spotifylyricsproxy://callback"
        )
            .setScopes(arrayOf(
                "app-remote-control",
                "playlist-read-private",
                "playlist-read-collaborative",
                "user-read-private"
            ))
            .build()
        SpotifyAuthHolder.startAuth?.invoke(request)
    }

    private fun token(): String? {
        val primary = SpotifyAuthHolder.accessToken
        if (primary != null) return primary
        Log.d(TAG, "token: SpotifyAuthHolder.accessToken is null")
        return null
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Local filter for tracks already loaded from the selected playlist. */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun loadPlaylists() {
        val t = token()
        Log.d(TAG, "loadPlaylists: tokenPresent=${t != null}")
        if (t == null) {
            _error.value = getApplication<Application>().getString(R.string.error_not_logged_in)
            return
        }
        _loadingPlaylists.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val response = SpotifyWebApiClient.api.getPlaylists(
                    auth = SpotifyWebApiClient.authHeader(t)
                )
                val playlists = response.items
                _playlists.value = playlists
                // Auto-select first if none selected
                if (_selectedPlaylist.value == null && playlists.isNotEmpty()) {
                    selectPlaylist(playlists.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlists", e)
                _error.value = getApplication<Application>().getString(R.string.playlist_load_failed)
            } finally {
                _loadingPlaylists.value = false
            }
        }
    }

    fun selectPlaylist(playlist: SpotifyPlaylistItem) {
        val t = token() ?: return
        _selectedPlaylist.value = playlist
        _tracks.value = emptyList()
        _loadingTracks.value = true
        _error.value = null
        _trackProgress.value = null
        viewModelScope.launch {
            try {
                val allTracks = mutableListOf<PlaylistTrack>()
                var offset = 0
                var total = Int.MAX_VALUE

                while (offset < total && offset < 2000) {
                    val response = SpotifyWebApiClient.api.getPlaylistTracks(
                        auth = SpotifyWebApiClient.authHeader(t),
                        playlistId = playlist.id,
                        limit = PAGE_LIMIT,
                        offset = offset
                    )
                    if (total == Int.MAX_VALUE) total = response.total
                    for ((pageIdx, item) in response.items.withIndex()) {
                        val track = item.track ?: continue
                        if (track.id.isBlank()) continue
                        val actualIndex = offset + pageIdx
                        val imageUrl = track.album?.images
                            ?.minByOrNull { it.width ?: Int.MAX_VALUE }
                            ?.url
                        allTracks.add(
                            PlaylistTrack(
                                id = track.id,
                                uri = track.uri,
                                title = track.name,
                                artist = track.artists.joinToString(", ") { it.name },
                                durationMs = track.durationMs,
                                imageUrl = imageUrl,
                                playlistIndex = actualIndex
                            )
                        )
                    }
                    offset += PAGE_LIMIT
                    val loaded = allTracks.size
                    _tracks.value = allTracks.toList()
                    _trackProgress.value = loaded to total
                    // If the batch returned fewer items than the limit, we're done
                    if (response.items.size < PAGE_LIMIT) break
                }
                Log.i(TAG, "Loaded ${allTracks.size}/$total tracks for ${playlist.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tracks", e)
                _error.value = getApplication<Application>().getString(R.string.playlist_load_failed)
            } finally {
                _loadingTracks.value = false
                _trackProgress.value = null
            }
        }
    }

    /** @param trackIndex 0-based index of [track] within the playlist. */
    fun playTrack(track: PlaylistTrack, playlistId: String, trackIndex: Int) {
        val playlistUri = "spotify:playlist:$playlistId"
        Log.i(TAG, "Requesting play: ${track.title} (context=$playlistUri, index=$trackIndex)")
        PlayRequestHolder.send(
            com.example.spotifylyricsproxy.ui.playback.PlayRequest(
                uri = playlistUri,
                contextTrackIndex = trackIndex,
                trackUri = track.uri
            )
        )
    }
}
