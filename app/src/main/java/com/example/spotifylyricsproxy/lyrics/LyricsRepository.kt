package com.example.spotifylyricsproxy.lyrics

import android.util.Log
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import com.example.spotifylyricsproxy.database.entity.TrackPlayHistoryEntity
import com.example.spotifylyricsproxy.lyrics.lrclib.LrclibLyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LyricsRepository(private val database: AppDatabase) {

    companion object {
        private const val TAG = "LyricsRepo"
        private const val RETRY_DELAY_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    private val source = LrclibLyricsSource()
    private val cacheDao = database.lyricCacheDao()
    private val historyDao = database.trackPlayHistoryDao()

    private val _parsedLyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val parsedLyrics: StateFlow<List<LrcLine>> = _parsedLyrics.asStateFlow()

    private val _currentLine = MutableStateFlow<LrcLine?>(null)
    val currentLine: StateFlow<LrcLine?> = _currentLine.asStateFlow()

    private val _lyricStatus = MutableStateFlow<LyricStatus>(LyricStatus.Idle)
    val lyricStatus: StateFlow<LyricStatus> = _lyricStatus.asStateFlow()

    suspend fun fetchLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0
    ) {
        if (title.isEmpty() || artist.isEmpty()) return

        // Check cache first
        val cached = withContext(Dispatchers.IO) { cacheDao.getByTrackId(trackId) }
        if (cached != null) {
            Log.i(TAG, "Cache hit: $title (status=${cached.fetchStatus})")
            updatePlayHistory(cached)
            when (cached.fetchStatus) {
                "success" -> {
                    cached.syncedLyrics?.let { synced ->
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            _parsedLyrics.value = lines
                            _lyricStatus.value = LyricStatus.Synced(cached.confidenceScore)
                            return
                        }
                    }
                }
                "not_found", "failed" -> {
                    if (cached.nextRetryAt != null && System.currentTimeMillis() < cached.nextRetryAt) {
                        Log.d(TAG, "Retry not yet due for $title")
                        _lyricStatus.value = LyricStatus.NotFound
                        return
                    }
                }
                "plain_only" -> {
                    cached.syncedLyrics?.let { synced ->
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            _parsedLyrics.value = lines
                            _lyricStatus.value = LyricStatus.Synced(cached.confidenceScore)
                            return
                        }
                    }
                }
            }
        }

        // Not cached or needs refresh, fetch from network
        _lyricStatus.value = LyricStatus.Searching

        try {
            val request = LyricsSearchRequest(
                trackName = title,
                artistName = artist,
                albumName = album,
                durationMs = durationMs
            )

            val candidates = withContext(Dispatchers.IO) { source.search(request) }

            if (candidates.isEmpty()) {
                Log.w(TAG, "No lyrics found for: $title - $artist")
                cacheNotfound(trackId, title, artist, album, durationMs)
                _lyricStatus.value = LyricStatus.NotFound
                return
            }

            val scored = candidates.map {
                LyricMatcher.score(it, title, artist, album, durationMs)
            }.sortedByDescending { it.score }

            val best = scored.first()
            Log.i(TAG, "Best match: ${best.trackName} (score: ${best.score})")

            // Cache the result
            cacheResult(trackId, title, artist, album, durationMs, best)

            if (!LyricMatcher.isAutoAccept(best.score)) {
                _lyricStatus.value = LyricStatus.LowConfidence(best.score)
                return
            }

            val syncedLyrics = best.syncedLyrics
            if (syncedLyrics.isNullOrEmpty()) {
                _lyricStatus.value = LyricStatus.PlainOnly
                return
            }

            val lines = LrcParser.parse(syncedLyrics)
            if (lines.isEmpty()) {
                _lyricStatus.value = LyricStatus.ParseError
                return
            }

            _parsedLyrics.value = lines
            _lyricStatus.value = LyricStatus.Synced(best.score)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics", e)
            _lyricStatus.value = LyricStatus.Error(e.message ?: "未知错误")
        }
    }

    private suspend fun cacheResult(
        trackId: String, title: String, artist: String,
        album: String, durationMs: Long, best: LyricCandidate
    ) {
        val fetchStatus = when {
            best.syncedLyrics != null -> "success"
            best.plainLyrics != null -> "plain_only"
            else -> "not_found"
        }
        val entity = LyricCacheEntity(
            spotifyTrackId = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            syncedLyrics = best.syncedLyrics,
            plainLyrics = best.plainLyrics,
            fetchStatus = fetchStatus,
            confidenceScore = best.score,
            source = best.source
        )
        withContext(Dispatchers.IO) { cacheDao.upsert(entity) }
    }

    private suspend fun cacheNotfound(
        trackId: String, title: String, artist: String,
        album: String, durationMs: Long
    ) {
        val entity = LyricCacheEntity(
            spotifyTrackId = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            fetchStatus = "not_found",
            nextRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS
        )
        withContext(Dispatchers.IO) { cacheDao.upsert(entity) }
    }

    private suspend fun updatePlayHistory(cached: LyricCacheEntity) {
        withContext(Dispatchers.IO) {
            cacheDao.upsert(
                cached.copy(lastPlayedAt = System.currentTimeMillis())
            )
            historyDao.upsert(
                TrackPlayHistoryEntity(
                    spotifyTrackId = cached.spotifyTrackId,
                    title = cached.title,
                    artist = cached.artist,
                    album = cached.album,
                    durationMs = cached.durationMs,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updatePosition(positionMs: Long) {
        val lines = _parsedLyrics.value
        if (lines.isEmpty()) return
        val line = LyricSyncEngine.findCurrentLine(lines, positionMs)
        if (line != _currentLine.value) {
            _currentLine.value = line
        }
    }

    fun reset() {
        _parsedLyrics.value = emptyList()
        _currentLine.value = null
        _lyricStatus.value = LyricStatus.Idle
    }
}

sealed class LyricStatus {
    data object Idle : LyricStatus()
    data object Searching : LyricStatus()
    data class Synced(val score: Int) : LyricStatus()
    data object PlainOnly : LyricStatus()
    data object NotFound : LyricStatus()
    data object ParseError : LyricStatus()
    data class LowConfidence(val score: Int) : LyricStatus()
    data class Error(val message: String) : LyricStatus()
}
