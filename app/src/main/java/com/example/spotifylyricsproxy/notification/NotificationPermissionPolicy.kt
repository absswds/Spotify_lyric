package com.example.spotifylyricsproxy.notification

import android.os.Build

object NotificationPermissionPolicy {
    fun shouldRequestPostNotifications(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted
}
