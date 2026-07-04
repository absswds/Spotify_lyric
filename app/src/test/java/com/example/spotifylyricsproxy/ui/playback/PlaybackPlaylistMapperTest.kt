package com.example.spotifylyricsproxy.ui.playback

import com.example.spotifylyricsproxy.spotify.webapi.SpotifyArtist
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistTrackItem
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPlaylistMapperTest {

    @Test
    fun spotifyPlaylistItemsMapToPlayableTracks() {
        val items = listOf(
            SpotifyPlaylistTrackItem(
                item = SpotifyTrack(
                    id = "abc",
                    uri = "spotify:track:abc",
                    name = "Song A",
                    artists = listOf(SpotifyArtist("Artist A")),
                    durationMs = 180_000L
                )
            )
        )

        val tracks = items.toPlaybackPlaylistTracks()

        assertEquals(1, tracks.size)
        assertEquals("abc", tracks[0].id)
        assertEquals("spotify:track:abc", tracks[0].uri)
        assertEquals("Song A", tracks[0].title)
        assertEquals("Artist A", tracks[0].artist)
        assertEquals(180_000L, tracks[0].durationMs)
    }
}
