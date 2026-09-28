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

    @GET("v1/playlists/{id}/items")
    suspend fun getPlaylistTracks(
        @Header("Authorization") auth: String,
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("market") market: String = "from_token"
    ): SpotifyPlaylistTracksResponse

    // Playback control on the active Connect device (scope user-modify-playback-state).
    @retrofit2.http.PUT("v1/me/player/play")
    suspend fun resume(@Header("Authorization") auth: String): retrofit2.Response<Unit>

    @retrofit2.http.PUT("v1/me/player/pause")
    suspend fun pause(@Header("Authorization") auth: String): retrofit2.Response<Unit>

    @retrofit2.http.POST("v1/me/player/next")
    suspend fun next(@Header("Authorization") auth: String): retrofit2.Response<Unit>

    @retrofit2.http.POST("v1/me/player/previous")
    suspend fun previous(@Header("Authorization") auth: String): retrofit2.Response<Unit>

    @retrofit2.http.PUT("v1/me/player/seek")
    suspend fun seek(@Header("Authorization") auth: String, @Query("position_ms") positionMs: Long): retrofit2.Response<Unit>

    @retrofit2.http.PUT("v1/me/player/shuffle")
    suspend fun shuffle(@Header("Authorization") auth: String, @Query("state") state: Boolean): retrofit2.Response<Unit>

    /** [state]: "track", "context" or "off". */
    @retrofit2.http.PUT("v1/me/player/repeat")
    suspend fun repeat(@Header("Authorization") auth: String, @Query("state") state: String): retrofit2.Response<Unit>

    /** Upcoming tracks (scope user-read-playback-state). */
    @GET("v1/me/player/queue")
    suspend fun getQueue(@Header("Authorization") auth: String): retrofit2.Response<SpotifyQueue>

    /** 204 (no body) when nothing is playing on any device. */
    @GET("v1/me/player")
    suspend fun getPlayer(
        @Header("Authorization") auth: String
    ): retrofit2.Response<SpotifyPlayerState>

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
    @SerializedName("item")
    private val item: SpotifyTrack? = null,
    @SerializedName("track")
    private val legacyTrack: SpotifyTrack? = null
) {
    val track: SpotifyTrack?
        get() = item ?: legacyTrack
}

data class SpotifyTrack(
    val id: String = "",
    val uri: String = "",
    val name: String = "",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    @SerializedName("duration_ms")
    val durationMs: Long = 0
)

data class SpotifyPlayerState(
    @SerializedName("is_playing")
    val isPlaying: Boolean = false,
    @SerializedName("progress_ms")
    val progressMs: Long? = null,
    val item: SpotifyTrack? = null,
    val device: SpotifyDevice? = null,
    @SerializedName("shuffle_state")
    val shuffleState: Boolean = false,
    /** "off", "track" or "context". */
    @SerializedName("repeat_state")
    val repeatState: String = "off"
)

data class SpotifyQueue(
    val queue: List<SpotifyTrack> = emptyList()
)

data class SpotifyDevice(
    val name: String = "",
    /** "Smartphone", "Tablet", "Computer", "Speaker", ... */
    val type: String = ""
)

data class SpotifyArtist(
    val name: String = ""
)

data class SpotifyAlbum(
    val name: String = "",
    val images: List<SpotifyImage> = emptyList()
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
