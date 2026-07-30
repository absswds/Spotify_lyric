package com.example.spotifylyricsproxy.lyrics.netease

import android.util.Log
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Netease Cloud Music lyrics provider.
 *
 * Uses the undocumented but public music.163.com search and lyric endpoints.
 * No API key, no encryption needed for basic LRC+translation.
 * Translation (tlyric) is returned alongside the main lyric when available.
 *
 * Reference: Lyricify-Lyrics-Helper (Apache-2.0)
 *   https://github.com/WXRIW/Lyricify-Lyrics-Helper
 */
class NeteaseLyricsSource : LyricsSource {

    override val name: String = "netease"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "Netease"
        private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> {
        // Step 1: search for tracks matching title + artist
        val songs = try {
            searchSongs("${request.trackName} ${request.artistName}")
        } catch (e: Exception) {
            Log.w(TAG, "Search failed: ${e.message}")
            return emptyList()
        }

        if (songs.isEmpty()) {
            Log.i(TAG, "No search results for '${request.trackName}'")
            return emptyList()
        }

        // Step 2: fetch LRC for the best match (first result)
        val bestSong = songs.first()
        val lrcText = try {
            fetchLrc(bestSong.id)
        } catch (e: Exception) {
            Log.w(TAG, "Lyric fetch failed for ${bestSong.id}: ${e.message}")
            null
        }

        // Step 3: build candidate
        val candidate = LyricCandidate(
            id = bestSong.id,
            trackName = bestSong.name,
            artistName = bestSong.resolvedArtists.joinToString(", ") { it.name },
            albumName = bestSong.resolvedAlbum?.name ?: "",
            durationMs = bestSong.resolvedDuration,
            syncedLyrics = lrcText?.lyric,
            plainLyrics = null,
            translation = lrcText?.translation,
            source = name,
            score = 0
        )

        // If we also have a translation, append it as a metadata hint
        val translation = lrcText?.translation
        val result = mutableListOf(candidate)
        if (translation != null && translation.isNotEmpty()) {
            // Store translation inline — the renderer appends it below the current line
            // when isTranslationEnabled is active.
            Log.i(TAG, "Found translation (${translation.length} chars) for track ${bestSong.id}")
        }
        return result
    }

    /**
     * Search Netease for tracks matching the keyword.
     * Returns parsed song list, or empty on failure.
     */
    private fun searchSongs(keyword: String): List<NeteaseSong> {
        val url = "$SEARCH_URL?csrf_token=&hlpretag=&hlposttag=&s=${
            java.net.URLEncoder.encode(keyword, "UTF-8")
        }&type=1&offset=0&total=true&limit=10"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Search HTTP ${response.code}")
            return emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        val parsed = try {
            gson.fromJson(body, NeteaseSearchResponse::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Search JSON parse error: ${e.message}")
            return emptyList()
        }

        if (parsed.code != 200L) {
            Log.w(TAG, "Netease search code=${parsed.code}")
            return emptyList()
        }

        return parsed.result?.songs ?: emptyList()
    }

    /**
     * Fetch LRC lyrics for a given song id.
     * Returns the LRC text plus optional translation, or null.
     */
    private fun fetchLrc(songId: Long): LrcResult? {
        val url = "$LYRIC_URL?id=$songId&lv=-1&kv=-1&tv=-1&rv=-1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Lyric HTTP ${response.code} for id=$songId")
            return null
        }

        val body = response.body?.string() ?: return null
        val parsed = try {
            gson.fromJson(body, NeteaseLyricResponse::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Lyric JSON parse error: ${e.message}")
            return null
        }

        if (parsed.code != 200L) return null

        val lrc = parsed.lrc?.lyric
        val translation = parsed.tlyric?.lyric
        if (lrc.isNullOrBlank()) return null

        return LrcResult(
            lyric = lrc,
            translation = if (translation.isNullOrBlank()) null else translation
        )
    }
}

/** Internal container for lyric + optional translation */
data class LrcResult(
    val lyric: String,
    val translation: String? = null
)
