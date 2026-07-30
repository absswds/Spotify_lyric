package com.example.spotifylyricsproxy.lyrics.qqmusic

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

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

                // Step 2: fetch lyrics for the first song only (best match)
                val song = songList.getJSONObject(0)
                val songmid = song.optString("mid", "")
                val songName = song.optString("name", "")
                val singer = song.optJSONArray("singer")?.optJSONObject(0)?.optString("name", "") ?: ""
                val albumName = song.optJSONObject("album")?.optString("name", "") ?: ""
                val duration = song.optInt("interval", 0) * 1000L

                if (songmid.isNotEmpty()) {
                    val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&g_tk=5381&format=json"
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
                                        if (synced != null) {
                                            candidates.add(LyricCandidate(
                                                id = songmid.hashCode().toLong(),
                                                trackName = songName,
                                                artistName = singer,
                                                albumName = albumName,
                                                durationMs = duration,
                                                syncedLyrics = synced,
                                                plainLyrics = null,
                                                source = name,
                                                score = 0
                                            ))
                                            android.util.Log.i("QQMusic", "Added best candidate: $songName - $singer")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("QQMusic", "Lyric JSON error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("QQMusic", "Search failed: ${e.message}")
            }
            candidates
        }
}
