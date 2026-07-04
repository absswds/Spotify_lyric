package com.example.spotifylyricsproxy.spotify.webapi

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyWebApi {

    @GET("v1/me")
    suspend fun getMe(
        @Header("Authorization") auth: String
    ): SpotifyUserProfile

    @GET("v1/me/playlists")
    suspend fun getPlaylists(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SpotifyPlaylistResponse

    @GET("v1/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") auth: String,
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("market") market: String = "from_token"
    ): SpotifyPlaylistTracksResponse

    @GET("v1/tracks/{id}")
    suspend fun getTrack(
        @Header("Authorization") auth: String,
        @Path("id") trackId: String
    ): SpotifyTrack?
}

data class SpotifyPlaylistResponse(
    val items: List<SpotifyPlaylistItem> = emptyList(),
    val total: Int = 0
)

data class SpotifyPlaylistItem(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val collaborative: Boolean = false,
    val owner: SpotifyPlaylistOwner? = null,
    @SerializedName("items")
    val tracks: SpotifyPlaylistTracksInfo = SpotifyPlaylistTracksInfo(),
    val images: List<SpotifyImage> = emptyList()
)

data class SpotifyPlaylistOwner(
    val id: String = "",
    @SerializedName("display_name")
    val displayName: String? = null
)

data class SpotifyPlaylistTracksInfo(
    val total: Int = 0
)

data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrackItem> = emptyList(),
    val total: Int = 0
)

data class SpotifyPlaylistTrackItem(
    val track: SpotifyTrack? = null
)

data class SpotifyTrack(
    val id: String = "",
    val uri: String = "",
    val name: String = "",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    val durationMs: Long = 0
)

data class SpotifyArtist(
    val name: String = ""
)

data class SpotifyAlbum(
    val name: String = ""
)

data class SpotifyImage(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null
)

data class SpotifyUserProfile(
    @SerializedName("display_name")
    val displayName: String? = null,
    val id: String = "",
    val email: String? = null
)
