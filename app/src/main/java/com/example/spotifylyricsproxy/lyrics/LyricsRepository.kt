package com.example.spotifylyricsproxy.lyrics

import android.util.Log
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.lrclib.LrclibLyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LyricsRepository {

    companion object {
        private const val TAG = "LyricsRepo"
    }

    private val source = LrclibLyricsSource()

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
        Log.i(TAG, "fetchLyrics: title=$title artist=$artist")
        if (title.isEmpty() || artist.isEmpty()) {
            Log.w(TAG, "Empty title/artist, skip")
            return
        }

        _lyricStatus.value = LyricStatus.Searching

        try {
            val request = LyricsSearchRequest(
                trackName = title,
                artistName = artist,
                albumName = album,
                durationMs = durationMs
            )

            val candidates = withContext(Dispatchers.IO) {
                Log.i(TAG, "Calling LRCLIB...")
                val result = source.search(request)
                Log.i(TAG, "LRCLIB returned ${result.size} candidates")
                result
            }

            Log.i(TAG, "Got ${candidates.size} candidates")

            if (candidates.isEmpty()) {
                Log.w(TAG, "No lyrics found for: $title - $artist")
                _lyricStatus.value = LyricStatus.NotFound
                return
            }

            // Score and find best match
            val scored = candidates.map {
                LyricMatcher.score(it, title, artist, album, durationMs)
            }.sortedByDescending { it.score }

            val best = scored.first()
            Log.i(TAG, "Best match: ${best.trackName} by ${best.artistName} (score: ${best.score})")

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
