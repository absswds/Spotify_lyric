package com.example.spotifylyricsproxy.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationPermissionPolicy {
    fun shouldRequestPostNotifications(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted

    /**
     * Whether this app has notification-access (通知使用权) granted.
     * Needed on Android 11+ to enumerate other apps' MediaSessions, which
     * the offline lyric fallback relies on.
     */
    fun hasNotificationListenerAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /** Open the system screen where the user can toggle notification access. */
    fun promptNotificationListenerAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some OEMs rename the settings screen; fall back to app details.
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(fallback)
        }
    }
}
