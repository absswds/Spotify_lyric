package com.example.spotifylyricsproxy.notification

import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsNotificationStateTest {

    @Test
    fun updateGatePublishesOnlyWhenVisibleStateChanges() {
        val gate = LyricsNotificationUpdateGate()
        val track = SpotifyTrackInfo(
            trackId = "track-1",
            title = "今晚的风经过窗边",
            artist = "城市信号",
            isPaused = false
        )

        val first = LyricsNotificationSnapshot.from(
            track = track,
            currentLine = LrcLine(startMs = 1000, text = "今晚的风经过窗边")
        )
        val same = first.copy()
        val albumArtArrived = first.copy(hasAlbumArt = true)
        val lyricChanged = first.copy(title = "带走了几分思念")
        val paused = first.copy(isPlaying = false)

        assertTrue(gate.shouldPublish(first))
        assertFalse(gate.shouldPublish(same))
        assertTrue(gate.shouldPublish(albumArtArrived))
        assertTrue(gate.shouldPublish(lyricChanged))
        assertTrue(gate.shouldPublish(paused))
    }

    @Test
    fun snapshotUsesCurrentLyricAsTitleAndTrackAsSubtitle() {
        val snapshot = LyricsNotificationSnapshot.from(
            track = SpotifyTrackInfo(
                trackId = "track-1",
                title = "今晚的风经过窗边",
                artist = "城市信号",
                isPaused = false
            ),
            currentLine = LrcLine(startMs = 1000, text = "带走了几分思念")
        )

        assertEquals("带走了几分思念", snapshot.title)
        assertEquals("今晚的风经过窗边 - 城市信号", snapshot.subtitle)
        assertTrue(snapshot.isPlaying)
    }

    @Test
    fun snapshotFallsBackToTrackTitleWhenLyricIsMissing() {
        val snapshot = LyricsNotificationSnapshot.from(
            track = SpotifyTrackInfo(
                trackId = "track-1",
                title = "今晚的风经过窗边",
                artist = "城市信号",
                isPaused = true
            ),
            currentLine = null
        )

        assertEquals("今晚的风经过窗边", snapshot.title)
        assertEquals("城市信号", snapshot.subtitle)
        assertFalse(snapshot.isPlaying)
    }
}
