package com.example.spotifylyricsproxy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.spotifylyricsproxy.ui.navigation.AppNavigation
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import com.example.spotifylyricsproxy.ui.theme.SpotifyLyricProxyTheme

class MainActivity : ComponentActivity() {

    private val playbackViewModel: PlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotifyLyricProxyTheme {
                AppNavigation(playbackViewModel)
            }
        }
    }

    @Deprecated("Use registerForActivityResult instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        playbackViewModel.onAuthResult(requestCode, resultCode, data)
    }
}
