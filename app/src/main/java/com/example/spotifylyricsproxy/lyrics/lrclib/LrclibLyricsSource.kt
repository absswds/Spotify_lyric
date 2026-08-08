package com.example.spotifylyricsproxy.lyrics.lrclib

import android.icu.text.Transliterator
import android.util.Log
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LrclibLyricsSource : LyricsSource {

    override val name: String = "lrclib"

    private val api: LrclibApi
    private val enrichClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // lrclib.net's Cloudflare rejects the default "okhttp/4.x" UA with HTTP 520.
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", BROWSER_UA)
                .build())
        }
        .build()

    init {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .build())
            }
            .build()
        api = Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrclibApi::class.java)
    }

    companion object {
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        private val cnSimplifier: Transliterator by lazy {
            Transliterator.getInstance("Traditional-Simplified")
        }
        private val cnLock = Any()
        private fun toSimplified(s: String) = synchronized(cnLock) {
            try { cnSimplifier.transliterate(s) } catch (_: Exception) { s }
        }
    }

    private fun enrichSynced(id: Long): LrclibSearchResult? {
        val body = try {
            val req = Request.Builder().url("https://lrclib.net/api/get/$id").build()
            val resp = enrichClient.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.string() else null
        } catch (e: Exception) {
            null
        }
        if (body == null) return null
        val json = try { JSONObject(body) } catch (_: Exception) { return null }
        val txt = json.optString("syncedLyrics", "")
        // optString on a JSON null value may return "null" (4 chars) instead of ""
        // on some Android versions. Only accept valid LRC content.
        if (txt.isEmpty() || !txt.startsWith("[")) return null
        return LrclibSearchResult(
            id = id,
            trackName = json.optString("trackName", ""),
            artistName = json.optString("artistName", ""),
            albumName = json.optString("albumName", ""),
            duration = json.optDouble("duration", 0.0),
            syncedLyrics = txt,
            plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotEmpty() }
        )
    }

    override suspend fun search(request: LyricsSearchRequest): List<LyricCandidate> {
        // Precise lookup
        val preciseResult: LrclibGetResult? = try {
            api.getByTrack(
                trackName = request.trackName,
                artistName = request.artistName,
                albumName = request.albumName.ifEmpty { null },
                durationSec = if (request.durationMs > 0) request.durationMs / 1000.0 else null
            )
        } catch (e: Exception) { Log.w("Lrclib", "Precise: ${e.message}"); null }
        if (preciseResult?.syncedLyrics != null) return listOf(preciseResult.toCandidate())

        // Search attempts: Traditional title+artist → Simplified title+artist → title only
        val simplifiedTitle = toSimplified(request.trackName)
        val simplifiedArtist = toSimplified(request.artistName)
        var seen = emptySet<Long>()
        val queries = listOf(
            "${request.trackName} ${request.artistName}",
            "$simplifiedTitle $simplifiedArtist",
            request.trackName,
            simplifiedTitle
        )
        for (attempt in queries) {
            val raw = try { api.search(attempt) } catch (e: Exception) { Log.w("Lrclib", "Search $attempt: ${e.message}"); continue }
            Log.i("Lrclib", "Search '$attempt': ${raw.size} results")
            for (r in raw) {
                if (r.id in seen) continue
                seen = seen + r.id
                val enriched = enrichSynced(r.id) ?: continue
                return listOf(enriched.toCandidate())
            }
        }
        return if (preciseResult != null) listOf(preciseResult.toCandidate()) else emptyList()
    }

    private fun LrclibGetResult.toCandidate() = LyricCandidate(
        id = id, trackName = trackName, artistName = artistName,
        albumName = albumName, durationMs = (duration * 1000).toLong(),
        syncedLyrics = syncedLyrics, plainLyrics = plainLyrics, source = name
    )

    private fun LrclibSearchResult.toCandidate() = LyricCandidate(
        id = id, trackName = trackName, artistName = artistName,
        albumName = albumName, durationMs = (duration * 1000).toLong(),
        syncedLyrics = syncedLyrics, plainLyrics = plainLyrics, source = name
    )
}

data class LyricsSearchRequest(
    val trackName: String,
    val artistName: String,
    val albumName: String = "",
    val durationMs: Long = 0
)
