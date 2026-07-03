package com.example.spotifylyricsproxy.spotify.remote

sealed class SpotifyConnectionState {
    data object Disconnected : SpotifyConnectionState()
    data object Connecting : SpotifyConnectionState()
    data object Connected : SpotifyConnectionState()
    data class Error(val message: String) : SpotifyConnectionState()
    data object SpotifyNotInstalled : SpotifyConnectionState()
    data object SpotifyNotLoggedIn : SpotifyConnectionState()
}
