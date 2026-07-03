package com.example.spotifylyricsproxy.lyrics.lrclib

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LrclibLyricsSource {

    private val api: LrclibApi

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(LrclibApi::class.java)
    }

    suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> {
        return try {
            // Try precise lookup first
            val result = api.getByTrack(
                trackName = request.trackName,
                artistName = request.artistName,
                albumName = request.albumName.ifEmpty { null },
                durationMs = if (request.durationMs > 0) request.durationMs else null
            )
            if (result != null) {
                listOf(result.toCandidate())
            } else {
                // Fallback to search
                val query = "${request.trackName} ${request.artistName}"
                api.search(query).map { it.toCandidate() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun LrclibGetResult.toCandidate() = LyricCandidate(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        durationMs = duration,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        source = "lrclib"
    )

    private fun LrclibSearchResult.toCandidate() = LyricCandidate(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        durationMs = duration,
        source = "lrclib"
    )
}

data class LyricsSearchRequest(
    val trackName: String,
    val artistName: String,
    val albumName: String = "",
    val durationMs: Long = 0
)
