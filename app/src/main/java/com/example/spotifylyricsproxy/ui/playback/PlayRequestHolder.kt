package com.example.spotifylyricsproxy.ui.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared mechanism for PlaylistViewModel to request PlaybackViewModel play a URI.
 */
object PlayRequestHolder {
    private val _requestUri = MutableStateFlow<String?>(null)
    val requestUri: StateFlow<String?> = _requestUri.asStateFlow()

    fun play(uri: String) {
        _requestUri.value = uri
    }

    fun consume() {
        _requestUri.value = null
    }
}
