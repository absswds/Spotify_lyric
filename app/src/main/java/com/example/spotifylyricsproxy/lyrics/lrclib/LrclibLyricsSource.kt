package com.example.spotifylyricsproxy.lyrics.lrclib

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LrclibLyricsSource : LyricsSource {

    override val name: String = "lrclib"

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

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> {
        // Try precise lookup first (best effort, don't crash on failure)
        val preciseResult: LrclibGetResult? = try {
            val durationSec = if (request.durationMs > 0) request.durationMs / 1000.0 else null
            api.getByTrack(
                trackName = request.trackName,
                artistName = request.artistName,
                albumName = request.albumName.ifEmpty { null },
                durationSec = durationSec
            )
        } catch (e: Exception) {
            android.util.Log.w("Lrclib", "Precise lookup failed: ${e.message}")
            null
        }

        // If precise lookup succeeded with synced lyrics, return it
        if (preciseResult != null && preciseResult.syncedLyrics != null) {
            return listOf(preciseResult.toCandidate())
        }

        // Fallback to search
        return try {
            val query = "${request.trackName} ${request.artistName}"
            android.util.Log.i("Lrclib", "Searching with query: $query")
            val rawResults = api.search(query)
            android.util.Log.i("Lrclib", "Search returned ${rawResults.size} raw results")
            // Enrich each search result with the full record (getById) so syncedLyrics
            // and plainLyrics are always populated — the search endpoint may omit them
            // for some entries depending on the API's internal state.
            val searchResults = rawResults.mapNotNull { result ->
                try {
                    val full = api.getById(result.id)
                    full?.toCandidate() ?: result.toCandidate()
                } catch (_: Exception) {
                    result.toCandidate()
                }
            }
            android.util.Log.i("Lrclib", "Enriched ${searchResults.size} candidates")
            if (searchResults.isNotEmpty()) searchResults
            else if (preciseResult != null) listOf(preciseResult.toCandidate())
            else emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Lrclib", "Search failed: ${e.message}", e)
            if (preciseResult != null) listOf(preciseResult.toCandidate())
            else emptyList()
        }
    }

    private fun LrclibGetResult.toCandidate() = LyricCandidate(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        durationMs = (duration * 1000).toLong(),
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        source = name
    )

    private fun LrclibSearchResult.toCandidate() = LyricCandidate(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        durationMs = (duration * 1000).toLong(),
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        source = name
    )
}

data class LyricsSearchRequest(
    val trackName: String,
    val artistName: String,
    val albumName: String = "",
    val durationMs: Long = 0
)
