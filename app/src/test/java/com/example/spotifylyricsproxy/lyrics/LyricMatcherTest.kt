package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMatcherTest {

    @Test
    fun `perfect match scores high`() {
        val candidate = LyricCandidate(
            trackName = "Shape of You",
            artistName = "Ed Sheeran",
            albumName = "Divide",
            durationMs = 233000,
            syncedLyrics = "[00:10.00]Test"
        )
        val result = LyricMatcher.score(
            candidate, "Shape of You", "Ed Sheeran", "Divide", 233000
        )
        assertTrue(result.score >= 75) // Auto accept
    }

    @Test
    fun `cleaned title match`() {
        val candidate = LyricCandidate(
            trackName = "Shape of You",
            artistName = "Ed Sheeran",
            syncedLyrics = "[00:10.00]Test"
        )
        val result = LyricMatcher.score(
            candidate, "Shape of You (Remastered)", "Ed Sheeran"
        )
        assertTrue(result.score >= 65) // Title(35) + Artist(20) + Synced(12) = 67
    }

    @Test
    fun `plain only lyrics get penalty`() {
        val candidate = LyricCandidate(
            trackName = "Test Song",
            artistName = "Test Artist",
            plainLyrics = "Some plain lyrics"
        )
        val result = LyricMatcher.score(
            candidate, "Test Song", "Test Artist"
        )
        assertTrue(result.score < 75) // Should not auto-accept
    }

    @Test
    fun `duration match within 2 seconds`() {
        val candidate = LyricCandidate(
            trackName = "Test",
            artistName = "Artist",
            durationMs = 200000,
            syncedLyrics = "[00:10.00]Test"
        )
        val result = LyricMatcher.score(
            candidate, "Test", "Artist", expectedDurationMs = 201000
        )
        assertTrue(result.score >= 18) // Duration bonus
    }

    @Test
    fun `duration diff over 5 seconds no bonus`() {
        val candidate = LyricCandidate(
            trackName = "Test",
            artistName = "Artist",
            durationMs = 200000,
            syncedLyrics = "[00:10.00]Test"
        )
        val result = LyricMatcher.score(
            candidate, "Test", "Artist", expectedDurationMs = 210000
        )
        assertTrue(result.score < 75)
    }

    @Test
    fun `thresholds are correct`() {
        assertTrue(LyricMatcher.isAutoAccept(75))
        assertTrue(LyricMatcher.isAutoAccept(90))
        assertFalse(LyricMatcher.isAutoAccept(74))

        assertTrue(LyricMatcher.needsManualReview(60))
        assertTrue(LyricMatcher.needsManualReview(74))
        assertFalse(LyricMatcher.needsManualReview(59))
        assertFalse(LyricMatcher.needsManualReview(75))
    }
}
