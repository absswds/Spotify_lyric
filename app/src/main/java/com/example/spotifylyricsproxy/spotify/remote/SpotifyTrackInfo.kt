package com.example.spotifylyricsproxy.spotify.remote

data class SpotifyTrackInfo(
    val trackId: String = "",
    val trackUri: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val playbackPositionMs: Long = 0,
    val isPaused: Boolean = true,
    val imageUri: String = ""
)
