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
}
