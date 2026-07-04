package com.example.spotifylyricsproxy.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSeekTest {

    @Test
    fun fractionToSeekPositionClampsToTrackDuration() {
        assertEquals(0L, fractionToSeekPosition(-0.2f, 200_000L))
        assertEquals(100_000L, fractionToSeekPosition(0.5f, 200_000L))
        assertEquals(200_000L, fractionToSeekPosition(1.2f, 200_000L))
    }

    @Test
    fun fractionToSeekPositionReturnsZeroWhenDurationIsMissing() {
        assertEquals(0L, fractionToSeekPosition(0.5f, 0L))
    }
}
