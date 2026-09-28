package com.example.spotifylyricsproxy

import androidx.compose.animation.togetherWith
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyTokenStore
import com.example.spotifylyricsproxy.ui.navigation.AppNavigation
import com.example.spotifylyricsproxy.ui.onboarding.OnboardingScreen
import androidx.compose.runtime.getValue
import com.example.spotifylyricsproxy.ui.cache.CacheViewModel
import com.example.spotifylyricsproxy.ui.playback.LyricDisplayPreferences
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import com.example.spotifylyricsproxy.ui.precache.PrecacheViewModel
import com.example.spotifylyricsproxy.ui.theme.LocaleHelper
import com.example.spotifylyricsproxy.ui.theme.SpotifyLyricProxyTheme
import com.example.spotifylyricsproxy.ui.theme.ThemePreferences
import kotlinx.coroutines.launch
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val wrapped = LocaleHelper.wrap(newBase)
        super.attachBaseContext(wrapped)
    }

    private val playbackViewModel: PlaybackViewModel by viewModels()
    private val cacheViewModel: CacheViewModel by viewModels()
    private val precacheViewModel: PrecacheViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemePreferences.init(this)
        LyricDisplayPreferences.init(this)
        com.example.spotifylyricsproxy.core.AppSettings.init(this)
        // Permissions are offered (all optional) by the first-launch guide instead of
        // being forced on every start.

        // Restore persisted Web API access token so process-kill / re-launch
        // doesn't force the user to re-authorize
        SpotifyTokenStore.init(this)
        val savedToken = SpotifyTokenStore.getAccessToken()
        if (!savedToken.isNullOrBlank()) {
            android.util.Log.i("MainActivity", "Restored saved access token (len=${savedToken.length})")
            SpotifyAuthHolder.accessToken = savedToken
            // Tell ViewModels that a token is available so they can try to
            // load playlists without waiting for a fresh auth flow.
            playbackViewModel.handleRestoredToken(savedToken)
            precacheViewModel.handleRestoredToken(savedToken)
        }

        // Check if launched from OAuth redirect
        intent?.data?.let { uri ->
            handleAuthRedirect(uri.toString())
        }

        // Expose auth starter to ViewModels
        SpotifyAuthHolder.startAuth = { request ->
            AuthorizationClient.openLoginActivity(this@MainActivity, AUTH_REQ_CODE, request)
        }

        // Resume lifecycle hook for gentle auto-reconnect
        lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                playbackViewModel.onResume()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                playbackViewModel.onPause()
            }
        })

        setContent {
            SpotifyLyricProxyTheme {
                val onboardingDone by com.example.spotifylyricsproxy.core.AppSettings.onboardingDone
                // One continuous shot from the guide into the player: the guide zooms
                // past the camera while the player settles in behind it.
                androidx.compose.animation.AnimatedContent(
                    targetState = onboardingDone,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(700, delayMillis = 150)) +
                            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(900), initialScale = 0.9f)) togetherWith
                            (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)) +
                                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(600), targetScale = 1.18f))
                    },
                    label = "onboardingToPlayer"
                ) { done ->
                    if (done) {
                        AppNavigation(
                            playbackViewModel = playbackViewModel,
                            cacheViewModel = cacheViewModel,
                            precacheViewModel = precacheViewModel
                        )
                    } else {
                        OnboardingScreen(onFinish = com.example.spotifylyricsproxy.core.AppSettings::finishOnboarding)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.data?.let { uri ->
            handleAuthRedirect(uri.toString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        android.util.Log.i("MainActivity", "onActivityResult req=$requestCode res=$resultCode")
        if (requestCode == AUTH_REQ_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, data)
            android.util.Log.i("MainActivity", "Auth response type=${response.type}")
            if (response.type == AuthorizationResponse.Type.TOKEN) {
                // Save token for Web API access (shared between ViewModels)
                SpotifyAuthHolder.accessToken = response.accessToken
                SpotifyTokenStore.save(response.accessToken)
                playbackViewModel.handleAuthResponse(response)
                precacheViewModel.handleAuthResponse(response)
            }
        }
    }

    private fun handleAuthRedirect(uriString: String) {
        if (!uriString.startsWith("spotifylyricsproxy://callback")) return

        android.util.Log.i("MainActivity", "handleAuthRedirect: $uriString")
        val uri = android.net.Uri.parse(uriString)

        // PKCE authorization code flow (browser-based, bypasses Spotify App native auth)
        val authCode = uri.getQueryParameter("code")
        if (authCode != null) {
            android.util.Log.i("MainActivity", "PKCE auth code received")
            lifecycleScope.launch {
                precacheViewModel.handlePKCEAuthRedirect(uri)
            }
            return
        }

        // Fallback: standard implicit grant token response (from Spotify App native auth)
        val authResponse = AuthorizationResponse.fromUri(uri)
        android.util.Log.i("MainActivity", "AuthResponse type=${authResponse.type} error=${authResponse.error}")

        when (authResponse.type) {
            AuthorizationResponse.Type.TOKEN -> {
                // Save token for Web API access (shared between ViewModels)
                SpotifyAuthHolder.accessToken = authResponse.accessToken
                SpotifyTokenStore.save(authResponse.accessToken)
                playbackViewModel.handleAuthResponse(authResponse)
                precacheViewModel.handleAuthResponse(authResponse)
            }
            AuthorizationResponse.Type.ERROR -> {
                android.util.Log.w("MainActivity", "Auth error: ${authResponse.error}")
            }
            else -> {}
        }
    }

    companion object {
        private const val AUTH_REQ_CODE = 0x10
    }
}

object SpotifyAuthHolder {
    var startAuth: ((com.spotify.sdk.android.auth.AuthorizationRequest) -> Unit)? = null

    /** Opens Spotify's authorization with every scope the app uses. */
    fun requestAuth() {
        val request = com.spotify.sdk.android.auth.AuthorizationRequest.Builder(
            BuildConfig.SPOTIFY_CLIENT_ID,
            com.spotify.sdk.android.auth.AuthorizationResponse.Type.TOKEN,
            "spotifylyricsproxy://callback"
        )
            .setScopes(arrayOf("app-remote-control", "playlist-read-private", "playlist-read-collaborative", "user-read-private", "user-read-playback-state", "user-modify-playback-state"))
            .build()
        startAuth?.invoke(request)
    }
    /** Shared Web API token, set by MainActivity when auth succeeds */
    var accessToken: String? = null
}
