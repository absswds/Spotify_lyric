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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.core.content.ContextCompat
import com.example.spotifylyricsproxy.BuildConfig
import com.example.spotifylyricsproxy.MainActivity
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.util.MeteredState
import com.example.spotifylyricsproxy.SpotifyAuthHolder
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.lyrics.LyricsRepository
import com.example.spotifylyricsproxy.lyrics.TranslationService
import com.example.spotifylyricsproxy.mediasession.MediaSessionController
import com.example.spotifylyricsproxy.ui.playback.LyricDisplayPreferences
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
    // currentLine is observed directly from lyricsRepository.currentLine
    // (no separate forwarding StateFlow — that caused a race where combine
    //  could fire with newTrack + stale lyrics before reset took effect)

    private lateinit var spotifyRepository: SpotifyRemoteRepository
    private lateinit var lyricsRepository: LyricsRepository
    private lateinit var playbackClock: PlaybackClock
    private lateinit var mediaSessionController: MediaSessionController
    private var observerJob: Job? = null
    private var lastFetchedTrackId = ""
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        spotifyRepository = SpotifyRemoteRepository(
            context = applicationContext,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            redirectUri = REDIRECT_URI,
            albumArtDimension = com.spotify.protocol.types.Image.Dimension.LARGE
        )
        lyricsRepository = LyricsRepository.getInstance(AppDatabase.getInstance(applicationContext))
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
                    title = lyricsRepository.currentLine.value?.text?.takeIf { it.isNotBlank() } ?: track.title.ifBlank { getString(R.string.playback_title_waiting) },
                    subtitle = if (track.title.isNotBlank() && track.artist.isNotBlank()) "${track.title} - ${track.artist}" else track.title,
                    isPlaying = wasPaused // toggled
                )
                notificationGate.reset()
                publishNotification(snapshot, currentAlbumArt.value)
                // Also update MediaSession immediately
                mediaSessionController.updateForTrack(
                    track = track.copy(isPaused = wasPaused),
                    currentLine = lyricsRepository.currentLine.value,
                    albumArt = currentAlbumArt.value,
                    playbackPositionMs = playbackClock.estimatedPositionMs()
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
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startNotificationLoop() {
        if (observerJob != null) return

        acquireWakeLock()
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
                playbackClock.tick(400).collect { positionMs ->
                    lyricsRepository.updatePosition(positionMs)
                    mediaSessionController.updatePlaybackState(
                        isPlaying = currentTrack.value.trackId.isNotBlank() && !currentTrack.value.isPaused,
                        positionMs = positionMs
                    )
                }
            }

            launch {
                combine(
                    currentTrack,
                    lyricsRepository.currentLine,
                    currentAlbumArt
                ) { track, line, albumArt ->
                    // Update MediaSession right here with the combined values,
                    // so track, line, and albumArt are guaranteed consistent
                    // (no race with concurrent coroutines updating the flows).
                    mediaSessionController.updateForTrack(
                        track = track,
                        currentLine = line,
                        albumArt = albumArt,
                        playbackPositionMs = playbackClock.estimatedPositionMs()
                    )
                    val snapshot = LyricsNotificationSnapshot.from(
                        track = track,
                        currentLine = line,
                        hasAlbumArt = albumArt != null,
                        waitingTitle = getString(R.string.generic_waiting_for_playback),
                        waitingSubtitle = getString(R.string.generic_waiting_subtitle)
                    )
                    snapshot to albumArt
                }.collect { (snapshot, albumArt) ->
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
        Log.i(TAG, "Track change received: id=${track.trackId.take(8)} title='${track.title}' artist='${track.artist}' album='${track.album}' dur=${track.durationMs} metered=${getMeteredState()}")
        if (track.trackId.isBlank() || track.trackId == lastFetchedTrackId) {
            Log.w(TAG, "Skipping fetch: blank=${track.trackId.isBlank()} sameAsLast=${track.trackId == lastFetchedTrackId}")
            return
        }

        // Fetch lyrics to populate the shared LyricsRepository singleton.
        // The ViewModel reads the same singleton for UI display.
        // IMPORTANT: do NOT call lyricsRepository.reset() here.
        lastFetchedTrackId = track.trackId
        val strategy = LyricDisplayPreferences.mobileDataStrategy.value
        val isMetered = getMeteredState() == MeteredState.METERED

        when {
            !isMetered || strategy == "allow" -> {
                serviceScope.launch {
                    Log.i(TAG, "Calling fetchLyrics forceOnline=${!isMetered} strategy=$strategy")
                    lyricsRepository.fetchLyrics(
                        trackId = track.trackId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        forceOnline = !isMetered
                    )
                }
            }
            strategy == "deny" -> {
                serviceScope.launch {
                    lyricsRepository.setMobileDataRestricted()
                }
            }
            else -> { // "ask" — ViewModel will show dialog and call confirmMobileDataFetch
                serviceScope.launch {
                    lyricsRepository.setMobileDataRestricted()
                }
            }
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
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setStyle(MediaStyle().setMediaSession(mediaSessionController.getSessionToken()))
        .setPriority(
            if (snapshot.isPlaying) NotificationCompat.PRIORITY_HIGH
            else NotificationCompat.PRIORITY_DEFAULT
        )
        .addAction(android.R.drawable.ic_media_previous, getString(R.string.notification_action_previous), serviceIntent(ACTION_PREVIOUS))
        .addAction(
            if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (snapshot.isPlaying) getString(R.string.notification_action_pause) else getString(R.string.notification_action_play),
            serviceIntent(ACTION_PLAY_PAUSE)
        )
        .addAction(android.R.drawable.ic_media_next, getString(R.string.notification_action_next), serviceIntent(ACTION_NEXT))
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
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun waitingSnapshot() = LyricsNotificationSnapshot(
        trackId = "",
        title = getString(R.string.generic_waiting_for_playback),
        subtitle = getString(R.string.generic_waiting_subtitle),
        isPlaying = false
    )

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire(12 * 60 * 60 * 1000L) // 12 hours — release earlier via releaseWakeLock()
        }
        Log.i(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "LyricsForegroundSvc"
        private const val CHANNEL_ID = "lyrics_foreground"
        private const val NOTIFICATION_ID = 4001
        private const val REQUEST_OPEN_APP = 4002
        private const val REDIRECT_URI = "spotifylyricsproxy://callback"
        private const val WAKE_LOCK_TAG = "SpotifyLyricProxy::WakeLock"

        @Volatile
        private var _meteredStateOverride: MeteredState = MeteredState.NONE

        /** Called from PlaybackViewModel when connectivity type changes. */
        @JvmStatic
        fun setMeteredState(state: MeteredState) {
            _meteredStateOverride = state
        }
        fun getMeteredState(): MeteredState = _meteredStateOverride

        const val ACTION_START = "com.example.spotifylyricsproxy.notification.START"
        const val ACTION_PLAY_PAUSE = "com.example.spotifylyricsproxy.notification.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.spotifylyricsproxy.notification.PREVIOUS"
        const val ACTION_NEXT = "com.example.spotifylyricsproxy.notification.NEXT"
        const val ACTION_STOP = "com.example.spotifylyricsproxy.notification.STOP"
        const val EXTRA_FROM_MEDIA_BUTTON = "from_media_button"

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
