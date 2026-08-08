package com.example.spotifylyricsproxy.spotify.remote

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification-listener bridge used to read other apps' MediaSessions.
 *
 * Since Android 11, plain apps cannot enumerate another app's MediaSession
 * (`getActiveSessions(null)` throws "Missing permission to control media").
 * The sanctioned workaround — used by lyric apps like Musixmatch — is a
 * NotificationListenerService: once the user grants notification access
 * (设置 → 通知使用权), `MediaSessionManager.getActiveSessions(ourComponent)`
 * returns every active session, including Spotify's.
 */
class MediaSessionNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // No-op: we only need the listener to exist so the OS grants us
        // media-session visibility.
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }
}
