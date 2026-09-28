package com.example.spotifylyricsproxy.lyrics.qqmusic

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val MAX_SONGS = 3

class QQMusicLyricsSource : LyricsSource {

    override val name: String = "qqmusic"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> =
        withContext(Dispatchers.IO) {
            val candidates = mutableListOf<LyricCandidate>()
            android.util.Log.i("QQMusic", "Searching: ${request.trackName} ${request.artistName}")

            try {
                // Step 1: search for songs (limit=1, we only need the best match)
                val keyword = "${request.trackName} ${request.artistName}"
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=1&n=3&cr=1&aggr=1"
                val searchBody = client.newCall(
                    Request.Builder().url(searchUrl)
                        .header("Referer", "https://y.qq.com/")
                        .build()
                ).execute().body?.string()
                    ?: return@withContext candidates.also { android.util.Log.w("QQMusic", "Search empty response") }

                val searchJson = JSONObject(if (searchBody.startsWith("(")) searchBody.substring(1, searchBody.length - 1) else searchBody)
                val songList = searchJson.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                    ?: return@withContext candidates.also { android.util.Log.w("QQMusic", "No song list") }

                android.util.Log.i("QQMusic", "Found ${songList.length()} songs")
                if (songList.length() == 0) return@withContext candidates

                // Step 2: lyrics for the top few songs, in parallel, so the correction
                // screen has more than one version to choose from.
                val found = kotlinx.coroutines.coroutineScope {
                (0 until minOf(MAX_SONGS, songList.length())).map { k -> async {
                val song = songList.getJSONObject(k)
                val songid = song.optLong("songid", 0L)
                val songmid = song.optString("songmid", "")
                val songName = song.optString("songname", "")
                val singer = song.optJSONArray("singer")?.optJSONObject(0)?.optString("name", "") ?: ""
                val albumName = song.optJSONObject("album")?.optString("name", "") ?: ""
                val duration = song.optInt("interval", 0) * 1000L

                // The lyric API only works with the numeric musicid; passing the
                // alphabetic songmid returns retcode 1101 ("login required").
                if (songid > 0) {
                    val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?musicid=$songid&g_tk=5381&format=json"
                    val response = client.newCall(
                        Request.Builder().url(lyricUrl)
                            .header("Referer", "https://y.qq.com/")
                            .build()
                    ).execute()
                    if (response.isSuccessful) {
                        val lyricBody = response.body?.string()
                        if (lyricBody != null) {
                            try {
                                val lyricJson = JSONObject(if (lyricBody.startsWith("(")) lyricBody.substring(1, lyricBody.length - 1) else lyricBody)
                                if (lyricJson.optInt("retcode", -1) == 0) {
                                    val lyricEncoded = lyricJson.optString("lyric", "")
                                    if (lyricEncoded.isNotEmpty()) {
                                        val synced = try {
                                            Base64.getDecoder().decode(lyricEncoded).decodeToString()
                                        } catch (e: Exception) {
                                            android.util.Log.w("QQMusic", "Base64 decode failed: ${e.message}")
                                            null
                                        }
                                        val lyrics = fetchQrcAsYrc(songid) ?: synced
                                        if (lyrics != null) {
                                            return@async LyricCandidate(
                                                id = songmid.hashCode().toLong(),
                                                trackName = songName,
                                                artistName = singer,
                                                albumName = albumName,
                                                durationMs = duration,
                                                syncedLyrics = lyrics,
                                                plainLyrics = null,
                                                source = name,
                                                score = 0
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("QQMusic", "Lyric JSON error: ${e.message}")
                            }
                        }
                    }
                }
                null
                } }.awaitAll().filterNotNull()
                }
                candidates.addAll(found)
            } catch (e: Exception) {
                android.util.Log.w("QQMusic", "Search failed: ${e.message}")
            }
            candidates
        }

    /**
     * Word-timed QRC via lyric_download.fcg, converted to YRC; null when absent or on any failure.
     * Request parameters from Lyricify Lyrics Helper (Apache-2.0), `Providers/Web/QQMusic/Api.cs`.
     */
    private fun fetchQrcAsYrc(songid: Long): String? = try {
        val body = FormBody.Builder()
            .add("version", "15")
            .add("miniversion", "82")
            .add("lrctype", "4")
            .add("musicid", songid.toString())
            .build()
        val response = client.newCall(
            Request.Builder().url("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg")
                .header("Referer", "https://y.qq.com/")
                .post(body)
                .build()
        ).execute().use { it.body?.string() }
        response?.let(QrcConverter::extractEncrypted)
            ?.let { QrcConverter.decryptedToYrc(QrcDecrypter.decrypt(it)) }
    } catch (e: Exception) {
        android.util.Log.w("QQMusic", "QRC fetch failed: ${e.message}")
        null
    }
}
