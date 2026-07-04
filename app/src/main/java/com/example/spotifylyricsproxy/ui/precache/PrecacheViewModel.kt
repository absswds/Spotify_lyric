package com.example.spotifylyricsproxy.ui.precache

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.PlaylistCacheJobEntity
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiAuth
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.example.spotifylyricsproxy.worker.PlaylistPrecacheWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrecacheViewModel(application: Application) : AndroidViewModel(application) {

    private val clientId = com.example.spotifylyricsproxy.BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "spotifylyricsproxy://callback"

    private val webApiAuth = SpotifyWebApiAuth(clientId, redirectUri)
    private val db = AppDatabase.getInstance(application)

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

    fun authorize(activity: Activity) {
        webApiAuth.authorize(activity)
    }

    fun onAuthResult(resultCode: Int, data: Intent?): Boolean {
        val handled = webApiAuth.handleResponse(resultCode, data)
        if (handled) {
            _isAuthorized.value = true
            loadPlaylists()
        }
        return handled
    }

    fun loadPlaylists() {
        val token = webApiAuth.getAccessToken() ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = SpotifyWebApiClient.api.getPlaylists(
                    auth = "Bearer $token"
                )
                _playlists.value = response.items
            } catch (e: Exception) {
                _playlists.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun precachePlaylist(playlist: SpotifyPlaylistItem) {
        val token = webApiAuth.getAccessToken() ?: return
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

    fun isPrecaching(playlistId: String): Boolean {
        val workInfos = WorkManager.getInstance(getApplication())
            .getWorkInfosByTag("precache_$playlistId")
        return workInfos.get().any { !it.state.isFinished }
    }

    fun logout() {
        webApiAuth.clearToken()
        _isAuthorized.value = false
        _playlists.value = emptyList()
    }
}
