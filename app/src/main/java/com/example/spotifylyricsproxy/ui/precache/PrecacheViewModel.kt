package com.example.spotifylyricsproxy.ui.precache

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.PlaylistCacheJobEntity
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.example.spotifylyricsproxy.worker.PlaylistPrecacheWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrecacheViewModel(application: Application) : AndroidViewModel(application) {

    private val clientId = com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifylyricsproxy://callback"

    private val db = AppDatabase.getInstance(application)
    private var accessToken: String? = null

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylistItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylistItem>> = _playlists.asStateFlow()

    private val _cachedJobs = MutableStateFlow<List<PlaylistCacheJobEntity>>(emptyList())
    val cachedJobs: StateFlow<List<PlaylistCacheJobEntity>> = _cachedJobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            db.playlistCacheJobDao().observeAll().collect { jobs ->
                _cachedJobs.value = jobs
            }
        }
    }

    fun handleAuthResponse(response: AuthorizationResponse) {
        if (response.type == AuthorizationResponse.Type.TOKEN) {
            android.util.Log.i("PrecacheVM", "Web API token received")
            accessToken = response.accessToken
            _isAuthorized.value = true
            loadPlaylists()
        }
    }

    fun authorize(activity: Activity) {
        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )
            .setScopes(arrayOf("playlist-read-private", "playlist-read-collaborative"))
            .build()
        SpotifyAuthHolder.startAuth?.invoke(request)
    }

    fun loadPlaylists() {
        val token = accessToken ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = SpotifyWebApiClient.api.getPlaylists(
                    auth = "Bearer $token"
                )
                android.util.Log.i("PrecacheVM", "Got ${response.items.size} playlists")
                response.items.forEachIndexed { i, p ->
                    android.util.Log.i("PrecacheVM", "  [$i] name=${p.name} id=${p.id} tracksTotal=${p.tracks.total}")
                }
                _playlists.value = response.items
            } catch (e: Exception) {
                android.util.Log.e("PrecacheVM", "Failed to load playlists", e)
                _playlists.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun precachePlaylist(playlist: SpotifyPlaylistItem) {
        val token = accessToken ?: return
        val workRequest = OneTimeWorkRequestBuilder<PlaylistPrecacheWorker>()
            .setInputData(
                workDataOf(
                    PlaylistPrecacheWorker.KEY_PLAYLIST_ID to playlist.id,
                    PlaylistPrecacheWorker.KEY_PLAYLIST_NAME to playlist.name,
                    PlaylistPrecacheWorker.KEY_ACCESS_TOKEN to token
                )
            )
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                "precache_${playlist.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    fun logout() {
        accessToken = null
        _isAuthorized.value = false
        _playlists.value = emptyList()
    }
}
