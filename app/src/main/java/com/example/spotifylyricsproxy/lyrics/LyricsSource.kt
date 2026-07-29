package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest

/**
 * A pluggable lyrics provider. Implementations must be free, key-less, and tolerant of failure:
 * a thrown exception simply means "this source has nothing" and the repository moves on.
 */
interface LyricsSource {
    val name: String
    suspend fun search(request: LyricsSearchRequest): List<LyricCandidate>
}
