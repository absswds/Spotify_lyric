package com.example.spotifylyricsproxy.mediasession

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.spotifylyricsproxy.MainActivity
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

        val mediaButtonReceiverComponent = ComponentName(context, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(context, TAG, mediaButtonReceiverComponent, null).apply {
            setCallback(cb)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            // Tell the system that tapping the media card opens our app
            setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            setQueueTitle("Spotify 歌词")
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
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .apply {
                albumArt?.let { bitmap ->
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                }
            }
            .build()
        mediaSession?.setMetadata(metadata)

        // Update queue (single item so the system recognizes us as a media player)
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(track.trackId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                albumArt?.let { bitmap ->
                    setIconBitmap(bitmap)
                }
            }
            .build()
        @Suppress("DEPRECATION")
        val queue = android.support.v4.media.session.MediaSessionCompat.QueueItem(
            description, track.trackId.hashCode().toLong()
        )
        mediaSession?.setQueue(listOf(queue))

        updatePlaybackState(isPlaying, track.playbackPositionMs)
    }

    /**
     * Update just the playback state (position, playing/paused).
     * Called both during track updates and on every position tick so that
     * our session's lastPositionChangedTime stays fresher than Spotify's.
     */
    fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else if (currentTrack.trackId.isNotBlank()) PlaybackStateCompat.STATE_PAUSED
        else PlaybackStateCompat.STATE_NONE

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1.0f)
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
