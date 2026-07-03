package com.example.spotifylyricsproxy.playback.clock

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PlaybackClock {
    private var basePositionMs: Long = 0
    private var baseElapsedRealtime: Long = 0
    private var isPaused: Boolean = true
    private var durationMs: Long = 0

    fun update(positionMs: Long, paused: Boolean, duration: Long) {
        basePositionMs = positionMs
        baseElapsedRealtime = SystemClock.elapsedRealtime()
        isPaused = paused
        durationMs = duration
    }

    fun estimatedPositionMs(): Long {
        if (isPaused) return basePositionMs
        val elapsed = SystemClock.elapsedRealtime() - baseElapsedRealtime
        val estimated = basePositionMs + elapsed
        return estimated.coerceIn(0, durationMs)
    }

    fun tick(intervalMs: Long = 300): Flow<Long> = flow {
        while (true) {
            if (!isPaused) {
                emit(estimatedPositionMs())
                delay(intervalMs)
            } else {
                delay(if (intervalMs < 2000) 2000 else intervalMs)
                emit(basePositionMs)
            }
        }
    }
}
