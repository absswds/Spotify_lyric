package com.example.spotifylyricsproxy.ui.cache

import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheSummaryTest {

    @Test
    fun summarizeCacheEntries_countsAllStatuses() {
        val entries = listOf(
            cache("1", "success"),
            cache("2", "plain_only"),
            cache("3", "not_found"),
            cache("4", "failed"),
            cache("5", "success")
        )

        val summary = summarizeCacheEntries(entries)

        assertEquals(5, summary.total)
        assertEquals(2, summary.success)
        assertEquals(1, summary.plainOnly)
        assertEquals(1, summary.notFound)
        assertEquals(1, summary.failed)
    }

    private fun cache(id: String, status: String) =
        LyricCacheEntity(
            spotifyTrackId = id,
            title = "Song $id",
            artist = "Artist",
            fetchStatus = status
        )
}
