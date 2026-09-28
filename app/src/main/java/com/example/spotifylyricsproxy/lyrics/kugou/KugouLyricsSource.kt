package com.example.spotifylyricsproxy.lyrics.kugou

import android.util.Log
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Kugou Music lyrics provider. Word-level KRC is converted to YRC text by [KrcDecoder].
 *
 * Flow: song search → lyric candidates per hash (krcs.kugou.com) → KRC download → decrypt.
 * Song search uses songsearch.kugou.com because mobilecdn.kugou.com is HTTP-only (cleartext is blocked).
 *
 * Reference: Lyricify-Lyrics-Helper (Apache-2.0)
 *   https://github.com/WXRIW/Lyricify-Lyrics-Helper
 */
class KugouLyricsSource : LyricsSource {

    override val name: String = NAME

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        const val NAME = "kugou"
        private const val TAG = "Kugou"
        private const val SEARCH_URL = "https://songsearch.kugou.com/song_search_v2"
        private const val CANDIDATES_URL = "https://krcs.kugou.com/search"
        private const val DOWNLOAD_URL = "https://lyrics.kugou.com/download"
        private const val MAX_SONGS = 3
        private val TAGS = Regex("<[^>]+>")
    }

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> {
        val songs = try {
            searchSongs("${request.trackName} ${request.artistName}")
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
            return emptyList()
        }
        return songs.take(MAX_SONGS).mapNotNull { song ->
            try {
                fetchCandidate(song)
            } catch (e: Exception) {
                Log.w(TAG, "Lyric fetch failed: ${e.message}")
                null
            }
        }
    }

    private fun getJson(url: String): JsonObject? {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            return JsonParser.parseString(body).asJsonObject
        }
    }

    private fun searchSongs(keyword: String): List<JsonObject> {
        val url = "$SEARCH_URL?keyword=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&page=1&pagesize=$MAX_SONGS&platform=WebFilter"
        val lists = getJson(url)?.getAsJsonObject("data")?.getAsJsonArray("lists") ?: return emptyList()
        return lists.map { it.asJsonObject }
    }

    private fun fetchCandidate(song: JsonObject): LyricCandidate? {
        val hash = song.get("FileHash")?.asString ?: return null
        val durationMs = (song.get("Duration")?.asLong ?: 0L) * 1000
        val best = getJson("$CANDIDATES_URL?ver=1&man=yes&client=mobi&hash=$hash&duration=$durationMs")
            ?.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject ?: return null
        val id = best.get("id").asString
        val accessKey = best.get("accesskey").asString
        val content = getJson("$DOWNLOAD_URL?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8")
            ?.get("content")?.asString?.takeIf { it.isNotBlank() } ?: return null
        val yrc = KrcDecoder.krcToYrc(KrcDecoder.decrypt(content)).takeIf { it.isNotBlank() } ?: return null

        fun text(key: String) = song.get(key)?.asString?.replace(TAGS, "").orEmpty()
        return LyricCandidate(
            id = id.toLongOrNull() ?: 0L,
            trackName = text("SongName"),
            artistName = text("SingerName"),
            albumName = text("AlbumName"),
            durationMs = durationMs,
            syncedLyrics = yrc,
            source = name,
            score = 0
        )
    }
}
