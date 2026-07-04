package com.example.spotifylyricsproxy.spotify.webapi

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaylistModelTest {

    @Test
    fun playlistItem_mapsItemsOwnerAndCollaborativeFields() {
        val json = """
            {
              "id": "playlist-1",
              "name": "Daily",
              "collaborative": true,
              "owner": {
                "id": "user-1",
                "display_name": "Binbi"
              },
              "items": {
                "total": 183
              },
              "tracks": {
                "total": 0
              }
            }
        """.trimIndent()

        val playlist = Gson().fromJson(json, SpotifyPlaylistItem::class.java)

        assertEquals("playlist-1", playlist.id)
        assertEquals("Daily", playlist.name)
        assertTrue(playlist.collaborative)
        assertEquals("user-1", playlist.owner?.id)
        assertEquals("Binbi", playlist.owner?.displayName)
        assertEquals(183, playlist.tracks.total)
    }

    @Test
    fun playlistTrackItem_mapsCurrentItemField() {
        val json = """
            {
              "item": {
                "id": "track-1",
                "name": "Song",
                "duration_ms": 181000,
                "artists": [
                  { "name": "Artist" }
                ]
              }
            }
        """.trimIndent()

        val item = Gson().fromJson(json, SpotifyPlaylistTrackItem::class.java)

        assertEquals("track-1", item.track?.id)
        assertEquals("Song", item.track?.name)
        assertEquals(181000L, item.track?.durationMs)
        assertEquals("Artist", item.track?.artists?.single()?.name)
    }

    @Test
    fun playlistTrackItem_stillMapsDeprecatedTrackField() {
        val json = """
            {
              "track": {
                "id": "track-2",
                "name": "Old Song",
                "duration_ms": 182000,
                "artists": [
                  { "name": "Old Artist" }
                ]
              }
            }
        """.trimIndent()

        val item = Gson().fromJson(json, SpotifyPlaylistTrackItem::class.java)

        assertEquals("track-2", item.track?.id)
        assertEquals("Old Song", item.track?.name)
        assertEquals(182000L, item.track?.durationMs)
        assertEquals("Old Artist", item.track?.artists?.single()?.name)
    }
}
