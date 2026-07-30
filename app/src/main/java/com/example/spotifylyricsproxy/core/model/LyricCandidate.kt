package com.example.spotifylyricsproxy.core.model

data class LyricCandidate(
    val id: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val durationMs: Long = 0,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val translation: String? = null,
    val source: String = "lrclib",
    val score: Int = 0
)
