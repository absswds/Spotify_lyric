package com.example.spotifylyricsproxy.lyrics.lrclib

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface LrclibApi {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String
    ): List<LrclibSearchResult>

    @GET("api/get/{id}")
    suspend fun getById(
        @Path("id") id: Long
    ): LrclibGetResult?

    @GET("api/get")
    suspend fun getByTrack(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationMs: Long? = null
    ): LrclibGetResult?
}

data class LrclibSearchResult(
    val id: Long,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val duration: Long = 0
)

data class LrclibGetResult(
    val id: Long,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val duration: Long = 0,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)
