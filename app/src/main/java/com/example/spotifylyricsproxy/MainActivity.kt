package com.example.spotifylyricsproxy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.spotifylyricsproxy.ui.navigation.AppNavigation
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import com.example.spotifylyricsproxy.ui.precache.PrecacheViewModel
import com.example.spotifylyricsproxy.ui.theme.SpotifyLyricProxyTheme

class MainActivity : ComponentActivity() {

    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val precacheViewModel: PrecacheViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotifyLyricProxyTheme {
                AppNavigation(
                    playbackViewModel = playbackViewModel,
                    precacheViewModel = precacheViewModel
                )
            }
        }
    }

    @Deprecated("Use registerForActivityResult instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Route to the correct auth handler
        if (!playbackViewModel.onAuthResult(requestCode, resultCode, data)) {
            precacheViewModel.onAuthResult(resultCode, data)
        }
    }
}
