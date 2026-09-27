package com.example.spotifylyricsproxy.lyrics.amll

import android.util.Log
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AMLL TTML DB (CC0): community word-timed lyrics with duet agents, indexed by Spotify
 * track id, so a hit is an exact match rather than a fuzzy title search.
 *
 * https://github.com/Steve-xmh/amll-ttml-db — tried against several mirrors in order,
 * since raw.githubusercontent.com is unreliable on some networks.
 */
class AmllTtmlLyricsSource : LyricsSource {

    override val name: String = SOURCE

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    companion object {
        const val SOURCE = "amll"
        private const val TAG = "AmllTtml"
        private val SPOTIFY_ID = Regex("^[A-Za-z0-9]{22}$")
        private val MIRRORS = listOf(
            "https://amll-ttml-db.stevexmh.net/spotify/%s",
            "https://raw.githubusercontent.com/Steve-xmh/amll-ttml-db/main/spotify-lyrics/%s.ttml",
            "https://cdn.jsdelivr.net/gh/Steve-xmh/amll-ttml-db@main/spotify-lyrics/%s.ttml"
        )
    }

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> {
        val id = request.trackId
        if (!SPOTIFY_ID.matches(id)) return emptyList()
        for (template in MIRRORS) {
            val url = template.format(id)
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    // 404 means "not in the DB" — authoritative, no need to try other mirrors.
                    if (response.code == 404) return emptyList()
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null && body.trimStart().startsWith("<tt")) {
                        Log.i(TAG, "Hit for $id via ${url.substringAfter("//").substringBefore('/')}")
                        return listOf(
                            LyricCandidate(
                                trackName = request.trackName,
                                artistName = request.artistName,
                                albumName = request.albumName,
                                durationMs = request.durationMs,
                                syncedLyrics = body,
                                source = SOURCE
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror failed ($url): ${e.message}")
            }
        }
        return emptyList()
    }
}
