package com.example.spotifylyricsproxy.mediasession

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.example.spotifylyricsproxy.notification.LyricsForegroundService

/**
 * Handles media button events (Bluetooth headsets, wired headsets).
 * Forwards them to [LyricsForegroundService] which already handles play/pause/next/prev.
 */
class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON != intent.action) return

        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (keyEvent == null || keyEvent.action != KeyEvent.ACTION_DOWN) return

        // Route to the foreground service that handles media control
        val serviceIntent = Intent(context, LyricsForegroundService::class.java)
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> serviceIntent.action = LyricsForegroundService.ACTION_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> serviceIntent.action = LyricsForegroundService.ACTION_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PAUSE -> serviceIntent.action = LyricsForegroundService.ACTION_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_NEXT -> serviceIntent.action = LyricsForegroundService.ACTION_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> serviceIntent.action = LyricsForegroundService.ACTION_PREVIOUS
            KeyEvent.KEYCODE_MEDIA_STOP -> serviceIntent.action = LyricsForegroundService.ACTION_STOP
            else -> return
        }
        serviceIntent.putExtra(LyricsForegroundService.EXTRA_FROM_MEDIA_BUTTON, true)
        context.startService(serviceIntent)
    }
}
