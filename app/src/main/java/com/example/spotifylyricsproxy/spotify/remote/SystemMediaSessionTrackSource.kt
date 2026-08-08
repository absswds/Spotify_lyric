package com.example.spotifylyricsproxy.spotify.remote

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback track source used when Spotify App Remote cannot connect —
 * which happens whenever the device is offline (App Remote's connection
 * handshake requires a network). The Spotify client itself keeps publishing
 * a system MediaSession (com.spotify.music) even when playing downloaded
 * tracks without a network, so we read the current track (and album art)
 * from there so the lyric pipeline can still start and hit the LRCLIB cache.
 *
 * Transport controls (play/pause/next/prev/seek) are forwarded directly to
 * Spotify's system MediaSession, so they keep working offline too.
 */
class SystemMediaSessionTrackSource(context: Context) {

    companion object {
        private const val TAG = "SysMediaSession"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"

        /** Spotify's media button receiver, used to locate its session. */
        private val SPOTIFY_SESSION_COMPONENT = ComponentName(
            SPOTIFY_PACKAGE,
            "com.spotify.mediasession.mediasession.receiver.MediaButtonReceiver"
        )
    }

    private val appContext = context.applicationContext
    private val sessionManager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val _currentTrack = MutableStateFlow(SpotifyTrackInfo())
    val currentTrack: StateFlow<SpotifyTrackInfo> = _currentTrack.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

    /** True when the user has granted notification-access (needed to see
     *  other apps' MediaSessions on Android 11+). */
    val hasListenerPermission: Boolean
        get() = NotificationManagerCompat.getEnabledListenerPackages(appContext)
            .contains(appContext.packageName)

    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var started = false

    // ---- Transport controls (work offline, straight to Spotify) ----

    fun play() = controller?.transportControls?.play()
    fun pause() = controller?.transportControls?.pause()
    fun skipNext() = controller?.transportControls?.skipToNext()
    fun skipPrevious() = controller?.transportControls?.skipToPrevious()
    fun seekTo(positionMs: Long) = controller?.transportControls?.seekTo(positionMs)

    // ---- Lifecycle ----

    /** Start observing Spotify's system MediaSession. Idempotent. */
    fun start() {
        if (started) return
        started = true
        if (!hasListenerPermission) {
            Log.w(TAG, "Notification access not granted — cannot read Spotify session")
            // Allow retry once the user grants notification access.
            started = false
            return
        }
        try {
            // On Android 11+ only a notification listener may enumerate other
            // apps' sessions; getActiveSessions(null) throws SecurityException.
            val listenerComponent = ComponentName(
                appContext, MediaSessionNotificationListener::class.java
            )
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            val spotify = controllers.firstOrNull {
                it.packageName == SPOTIFY_PACKAGE
            } ?: run {
                Log.d(TAG, "No active Spotify MediaSession found")
                // Spotify may not be playing yet; allow retry on next start.
                started = false
                return
            }
            controller = spotify
            callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    publish(metadata, spotify.playbackState)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    publish(spotify.metadata, state)
                }

                override fun onSessionDestroyed() {
                    _currentTrack.value = SpotifyTrackInfo()
                    _albumArt.value = null
                }
            }
            spotify.registerCallback(callback!!)
            publish(spotify.metadata, spotify.playbackState)
            Log.i(TAG, "Observing Spotify system MediaSession")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to observe system MediaSession", e)
        }
    }

    /** Stop observing. Idempotent. */
    fun stop() {
        if (!started) return
        started = false
        try {
            callback?.let { controller?.unregisterCallback(it) }
        } catch (_: Exception) {
        }
        controller = null
        callback = null
        _currentTrack.value = SpotifyTrackInfo()
        _albumArt.value = null
    }

    private fun publish(metadata: MediaMetadata?, state: PlaybackState?) {
        if (metadata == null) return
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
        val uri = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: mediaId
        // Spotify's mediaId is the spotify:track:<id> URI; App Remote's
        // trackId is the bare <id>. Extract the id so lyric cache keys match.
        val trackId = uri.substringAfterLast(":", uri).ifBlank { mediaId }

        val track = SpotifyTrackInfo(
            trackId = trackId,
            trackUri = uri,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            playbackPositionMs = state?.position ?: 0L,
            isPaused = state?.state != PlaybackState.STATE_PLAYING,
            imageUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: ""
        )
        // Album art: prefer the embedded bitmap (works offline); fall back to
        // the URI so the repository can try to fetch it when online.
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) {
            _albumArt.value = art
        }
        Log.i(TAG, "System session track: ${track.title} - ${track.artist} id=${track.trackId.take(8)} paused=${track.isPaused} art=${art != null}")
        _currentTrack.value = track
    }
}
