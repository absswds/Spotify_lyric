package com.example.spotifylyricsproxy.mediasession

import android.content.Context
import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.spotify.remote.SpotifyRemoteRepository
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo

/**
 * MediaSessionCompat controller that proxies to Spotify App Remote.
 * Shows lyrics as the track title in the system media card.
 */
class MediaSessionController(
    private val spotifyRepository: SpotifyRemoteRepository
) {
    private var mediaSession: MediaSessionCompat? = null
    private var callback: ProxyCallback? = null

    private var currentTrack: SpotifyTrackInfo = SpotifyTrackInfo()

    fun create(context: Context) {
        val cb = ProxyCallback()
        callback = cb

        mediaSession = MediaSessionCompat(context, TAG).apply {
            setCallback(cb)
            isActive = true
        }
    }

    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken

    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        callback = null
    }

    fun updateForTrack(
        track: SpotifyTrackInfo,
        currentLine: LrcLine?,
        albumArt: Bitmap?
    ) {
        currentTrack = track
        val isPlaying = track.trackId.isNotBlank() && !track.isPaused

        val title = currentLine?.text?.takeIf { it.isNotBlank() }
            ?: track.title.ifBlank { "等待播放" }
        val subtitle = if (track.title.isNotBlank() && track.artist.isNotBlank()) {
            "${track.title} - ${track.artist}"
        } else {
            track.title.ifBlank { track.artist }
        }

        // Update metadata
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
            .apply {
                albumArt?.let { bitmap ->
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                }
            }
            .build()
        mediaSession?.setMetadata(metadata)

        // Update playback state
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else if (track.trackId.isNotBlank()) PlaybackStateCompat.STATE_PAUSED
        else PlaybackStateCompat.STATE_NONE

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, track.playbackPositionMs, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    /**
     * Callback that proxies MediaSession commands to Spotify.
     */
    private inner class ProxyCallback : MediaSessionCompat.Callback() {
        override fun onPlay() { spotifyRepository.play() }
        override fun onPause() { spotifyRepository.pause() }
        override fun onSkipToNext() { spotifyRepository.skipNext() }
        override fun onSkipToPrevious() { spotifyRepository.skipPrevious() }
        override fun onSeekTo(pos: Long) { spotifyRepository.seekTo(pos) }
    }

    companion object {
        private const val TAG = "LyricsMediaSession"
    }
}
