package com.example.spotifylyricsproxy.ui.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayRequest(
    /** The URI to play — either a track URI or a context URI (playlist/album). */
    val uri: String,
    /** When non-null, plays [uri] as a context starting at this track index. */
    val contextTrackIndex: Int = -1,
    /** The track URI, for reference / logging. */
    val trackUri: String = ""
)

/**
 * Shared mechanism for PlaylistViewModel to request PlaybackViewModel play a URI.
 */
object PlayRequestHolder {
    private val _request = MutableStateFlow<PlayRequest?>(null)
    val request: StateFlow<PlayRequest?> = _request.asStateFlow()

    /** @deprecated Use [request] and [send] instead. */
    val requestUri: StateFlow<String?> = MutableStateFlow(null).asStateFlow()

    fun send(request: PlayRequest) {
        _request.value = request
    }

    @Deprecated("Use send(PlayRequest) instead", ReplaceWith("send(PlayRequest(uri))"))
    fun play(uri: String) {
        send(PlayRequest(uri))
    }

    fun consume() {
        _request.value = null
    }
}
