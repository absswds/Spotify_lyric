package com.example.spotifylyricsproxy.notification

import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo

data class LyricsNotificationSnapshot(
    val trackId: String,
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val hasAlbumArt: Boolean = false
) {
    companion object {
        fun from(
            track: SpotifyTrackInfo,
            currentLine: LrcLine?,
            hasAlbumArt: Boolean = false,
            waitingTitle: String = "Waiting for playback",
            waitingSubtitle: String = "Connect to Spotify to see lyrics"
        ): LyricsNotificationSnapshot {
            val trackTitle = track.title.ifBlank { waitingTitle }
            val artist = track.artist.trim()
            val subtitle = if (currentLine?.text.isNullOrBlank()) {
                artist.ifBlank { waitingSubtitle }
            } else {
                if (artist.isBlank()) trackTitle else "$trackTitle - $artist"
            }

            return LyricsNotificationSnapshot(
                trackId = track.trackId,
                title = currentLine?.text?.takeIf { it.isNotBlank() } ?: trackTitle,
                subtitle = subtitle,
                isPlaying = track.trackId.isNotBlank() && !track.isPaused,
                hasAlbumArt = hasAlbumArt
            )
        }
    }
}

class LyricsNotificationUpdateGate {
    private var lastSnapshot: LyricsNotificationSnapshot? = null

    fun shouldPublish(next: LyricsNotificationSnapshot): Boolean {
        if (next == lastSnapshot) return false
        lastSnapshot = next
        return true
    }

    fun reset() {
        lastSnapshot = null
    }
}
