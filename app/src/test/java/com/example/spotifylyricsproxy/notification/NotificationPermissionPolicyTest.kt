package com.example.spotifylyricsproxy.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {

    @Test
    fun android13WithoutPermissionShouldRequestNotifications() {
        assertTrue(NotificationPermissionPolicy.shouldRequestPostNotifications(33, false))
    }

    @Test
    fun android12DoesNotNeedRuntimeNotificationPermission() {
        assertFalse(NotificationPermissionPolicy.shouldRequestPostNotifications(32, false))
    }

    @Test
    fun android13WithPermissionDoesNotRequestAgain() {
        assertFalse(NotificationPermissionPolicy.shouldRequestPostNotifications(33, true))
    }
}
