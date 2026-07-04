package com.example.spotifylyricsproxy.ui.playback

fun fractionToSeekPosition(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0) return 0L
    return (fraction.coerceIn(0f, 1f) * durationMs).toLong()
}
