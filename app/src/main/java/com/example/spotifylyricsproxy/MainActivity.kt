package com.example.spotifylyricsproxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.spotifylyricsproxy.ui.navigation.AppNavigation
import com.example.spotifylyricsproxy.ui.theme.SpotifyLyricProxyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotifyLyricProxyTheme {
                AppNavigation()
            }
        }
    }
}
