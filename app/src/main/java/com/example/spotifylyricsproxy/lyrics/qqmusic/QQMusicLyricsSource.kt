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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> =
        withContext(Dispatchers.IO) {
            val candidates = mutableListOf<LyricCandidate>()

            // Step 1: search for songs
            val keyword = "${request.trackName} ${request.artistName}"
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=1&n=5&cr=1&aggr=1"
            val searchBody = client.newCall(Request.Builder().url(searchUrl).build()).execute().body?.string()
                ?: return@withContext candidates

            val searchJson = JSONObject(if (searchBody.startsWith("(")) searchBody.substring(1, searchBody.length - 1) else searchBody)
            val songList = searchJson.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return@withContext candidates

            for (i in 0 until songList.length()) {
                val song = songList.getJSONObject(i)
                val songmid = song.optString("mid", "")
                val songName = song.optString("name", "")
                val singer = song.optJSONArray("singer")?.optJSONObject(0)?.optString("name", "") ?: ""
                val albumName = song.optJSONObject("album")?.optString("name", "") ?: ""
                val duration = song.optInt("interval", 0) * 1000L  // seconds → ms

                if (songmid.isEmpty()) continue

                // Step 2: fetch lyrics
                val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&g_tk=5381&format=json"
                val lyricBody = client.newCall(Request.Builder().url(lyricUrl).build()).execute().body?.string()
                    ?: continue

                val lyricJson = JSONObject(if (lyricBody.startsWith("(")) lyricBody.substring(1, lyricBody.length - 1) else lyricBody)
                if (lyricJson.optInt("retcode", -1) != 0) continue

                val lyricEncoded = lyricJson.optString("lyric", "")
                if (lyricEncoded.isEmpty()) continue

                val synced = try {
                    Base64.getDecoder().decode(lyricEncoded).decodeToString()
                } catch (e: Exception) { continue }

                val transEncoded = lyricJson.optString("trans", "")
                val trans = if (transEncoded.isNotEmpty()) {
                    try { Base64.getDecoder().decode(transEncoded).decodeToString() } catch (_: Exception) { null }
                } else null

                candidates.add(LyricCandidate(
                    id = songmid,
                    trackName = songName,
                    artistName = singer,
                    albumName = albumName,
                    durationMs = duration,
                    syncedLyrics = synced,
                    plainLyrics = null,
                    source = name,
                    score = 0
                ))
            }
            candidates
        }
}
