package com.example.spotifylyricsproxy.ui.precache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PrecacheCacheEntryFactoryTest {

    @Test
    fun failedTrackCreatesVisibleFailedCacheEntry() {
        val entry = failedCacheEntry(
            trackId = "spotify-track-1",
            title = "Missing Song",
            artist = "Unknown Artist",
            durationMs = 123_000L,
            now = 1_000L
        )

        assertEquals("spotify-track-1", entry.spotifyTrackId)
        assertEquals("Missing Song", entry.title)
        assertEquals("Unknown Artist", entry.artist)
        assertEquals(123_000L, entry.durationMs)
        assertEquals("failed", entry.fetchStatus)
        assertNotNull(entry.nextRetryAt)
    }
}
