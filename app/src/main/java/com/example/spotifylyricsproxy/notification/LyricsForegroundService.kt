package com.example.spotifylyricsproxy.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.spotifylyricsproxy.BuildConfig
import com.example.spotifylyricsproxy.MainActivity
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.lyrics.LyricsRepository
import com.example.spotifylyricsproxy.mediasession.MediaSessionController
import com.example.spotifylyricsproxy.playback.clock.PlaybackClock
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyRemoteRepository
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LyricsForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notificationGate = LyricsNotificationUpdateGate()
    private val currentAlbumArt = MutableStateFlow<Bitmap?>(null)
    private val currentTrack = MutableStateFlow(SpotifyTrackInfo())
    private val currentLine = MutableStateFlow<LrcLine?>(null)

    private lateinit var spotifyRepository: SpotifyRemoteRepository
    private lateinit var lyricsRepository: LyricsRepository
    private lateinit var playbackClock: PlaybackClock
    private lateinit var mediaSessionController: MediaSessionController
    private var observerJob: Job? = null
    private var lastFetchedTrackId = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        spotifyRepository = SpotifyRemoteRepository(
            context = applicationContext,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            redirectUri = REDIRECT_URI,
            albumArtDimension = com.spotify.protocol.types.Image.Dimension.SMALL
        )
        lyricsRepository = LyricsRepository(AppDatabase.getInstance(applicationContext))
        playbackClock = PlaybackClock()
        mediaSessionController = MediaSessionController(spotifyRepository)
        mediaSessionController.create(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startNotificationLoop()
            ACTION_PLAY_PAUSE -> {
                val track = currentTrack.value
                val wasPaused = track.isPaused
                if (wasPaused) spotifyRepository.play() else spotifyRepository.pause()
                // Immediately update notification with toggled state
                val snapshot = LyricsNotificationSnapshot(
                    trackId = track.trackId,
                    title = currentLine.value?.text?.takeIf { it.isNotBlank() } ?: track.title.ifBlank { "等待播放" },
                    subtitle = if (track.title.isNotBlank() && track.artist.isNotBlank()) "${track.title} - ${track.artist}" else track.title,
                    isPlaying = wasPaused // toggled
                )
                notificationGate.reset()
                publishNotification(snapshot, currentAlbumArt.value)
                // Also update MediaSession immediately
                mediaSessionController.updateForTrack(
                    track = track.copy(isPaused = wasPaused),
                    currentLine = currentLine.value,
                    albumArt = currentAlbumArt.value
                )
            }
            ACTION_PREVIOUS -> spotifyRepository.skipPrevious()
            ACTION_NEXT -> spotifyRepository.skipNext()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        mediaSessionController.release()
        spotifyRepository.disconnect()
        super.onDestroy()
    }

    private fun startNotificationLoop() {
        if (observerJob != null) return

        startForeground(NOTIFICATION_ID, buildNotification(waitingSnapshot(), null))
        spotifyRepository.tryConnect()

        observerJob = serviceScope.launch {
            launch {
                spotifyRepository.currentTrack.collect { track ->
                    currentTrack.value = track
                    playbackClock.update(
                        positionMs = track.playbackPositionMs,
                        paused = track.isPaused,
                        duration = track.durationMs
                    )
                    fetchLyricsWhenTrackChanges(track)
                }
            }

            launch {
                spotifyRepository.albumArt.collect { currentAlbumArt.value = it }
            }

            launch {
                lyricsRepository.currentLine.collect { currentLine.value = it }
            }

            launch {
                playbackClock.tick(400).collect { positionMs ->
                    lyricsRepository.updatePosition(positionMs)
                }
            }

            launch {
                combine(
                    currentTrack,
                    currentLine,
                    currentAlbumArt
                ) { track, line, albumArt ->
                    Pair(
                        LyricsNotificationSnapshot.from(
                            track = track,
                            currentLine = line,
                            hasAlbumArt = albumArt != null
                        ),
                        albumArt
                    )
                }.collect { (snapshot, albumArt) ->
                    // Update MediaSession on every track/line/art change
                    mediaSessionController.updateForTrack(
                        track = currentTrack.value,
                        currentLine = currentLine.value,
                        albumArt = albumArt
                    )
                    if (notificationGate.shouldPublish(snapshot)) {
                        publishNotification(snapshot, albumArt)
                    }
                }
            }

            launch {
                spotifyRepository.connectionState.collect { state ->
                    if (state !is SpotifyConnectionState.Connected && currentTrack.value.trackId.isBlank()) {
                        val snapshot = waitingSnapshot()
                        if (notificationGate.shouldPublish(snapshot)) {
                            publishNotification(snapshot, currentAlbumArt.value)
                        }
                    }
                }
            }
        }
    }

    private fun fetchLyricsWhenTrackChanges(track: SpotifyTrackInfo) {
        if (track.trackId.isBlank() || track.trackId == lastFetchedTrackId) return

        lastFetchedTrackId = track.trackId
        lyricsRepository.reset()
        serviceScope.launch {
            lyricsRepository.fetchLyrics(
                trackId = track.trackId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )
        }
    }

    private fun publishNotification(snapshot: LyricsNotificationSnapshot, albumArt: Bitmap?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot, albumArt))
    }

    private fun buildNotification(
        snapshot: LyricsNotificationSnapshot,
        albumArt: Bitmap?
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(snapshot.title)
        .setContentText(snapshot.subtitle)
        .setLargeIcon(albumArt)
        .setContentIntent(openAppIntent())
        .setOngoing(snapshot.isPlaying)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(android.R.drawable.ic_media_previous, "上一首", serviceIntent(ACTION_PREVIOUS))
        .addAction(
            if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (snapshot.isPlaying) "暂停" else "播放",
            serviceIntent(ACTION_PLAY_PAUSE)
        )
        .addAction(android.R.drawable.ic_media_next, "下一首", serviceIntent(ACTION_NEXT))
        .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, LyricsForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "歌词通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示当前 Spotify 歌词行和播放控制"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun waitingSnapshot() = LyricsNotificationSnapshot(
        trackId = "",
        title = "等待 Spotify 播放",
        subtitle = "连接后显示当前歌词",
        isPlaying = false
    )

    companion object {
        private const val CHANNEL_ID = "lyrics_foreground"
        private const val NOTIFICATION_ID = 4001
        private const val REQUEST_OPEN_APP = 4002
        private const val REDIRECT_URI = "spotifylyricsproxy://callback"

        const val ACTION_START = "com.example.spotifylyricsproxy.notification.START"
        const val ACTION_PLAY_PAUSE = "com.example.spotifylyricsproxy.notification.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.spotifylyricsproxy.notification.PREVIOUS"
        const val ACTION_NEXT = "com.example.spotifylyricsproxy.notification.NEXT"
        const val ACTION_STOP = "com.example.spotifylyricsproxy.notification.STOP"

        fun start(context: Context) {
            val intent = Intent(context, LyricsForegroundService::class.java)
                .setAction(ACTION_START)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Log.w("LyricsForegroundSvc", "Unable to start foreground service", it)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LyricsForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
