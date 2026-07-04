package com.example.spotifylyricsproxy.ui.playlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.ui.playback.PlayRequestHolder
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)

class PlaylistViewModel : ViewModel() {

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
        loadPlaylists()
    }

    private fun token(): String? = SpotifyAuthHolder.accessToken

    fun loadPlaylists() {
        val t = token() ?: run {
            _error.value = "未登录 Spotify"
            return
        }
        _loadingPlaylists.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val response = SpotifyWebApiClient.api.getPlaylists(
                    auth = SpotifyWebApiClient.authHeader(t)
                )
                _playlists.value = response.items
                // Auto-select first if none selected
                if (_selectedPlaylist.value == null && response.items.isNotEmpty()) {
                    selectPlaylist(response.items.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlists", e)
                _error.value = "歌单加载失败"
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

                while (offset < total && offset < 2000) { // safety cap
                    val response = SpotifyWebApiClient.api.getPlaylistTracks(
                        auth = SpotifyWebApiClient.authHeader(t),
                        playlistId = playlist.id,
                        limit = PAGE_LIMIT,
                        offset = offset
                    )
                    if (total == Int.MAX_VALUE) total = response.total
                    for (item in response.items) {
                        val track = item.track ?: continue
                        if (track.id.isBlank()) continue
                        allTracks.add(
                            PlaylistTrack(
                                id = track.id,
                                uri = track.uri,
                                title = track.name,
                                artist = track.artists.joinToString(", ") { it.name },
                                durationMs = track.durationMs
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
                _error.value = "歌曲加载失败"
            } finally {
                _loadingTracks.value = false
                _trackProgress.value = null
            }
        }
    }

    fun playTrack(track: PlaylistTrack) {
        Log.i(TAG, "Requesting play: ${track.title} (${track.uri})")
        PlayRequestHolder.play(track.uri)
    }
}
