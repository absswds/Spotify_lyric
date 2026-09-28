package com.example.spotifylyricsproxy.ui.navigation

import android.app.Activity
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.ui.cache.CacheScreen
import com.example.spotifylyricsproxy.ui.cache.CacheViewModel
import com.example.spotifylyricsproxy.ui.playback.LyricsCorrectionScreen
import com.example.spotifylyricsproxy.ui.playback.PlaybackScreen
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import com.example.spotifylyricsproxy.ui.playlist.PlaylistScreen
import com.example.spotifylyricsproxy.ui.playlist.PlaylistViewModel
import com.example.spotifylyricsproxy.ui.precache.PrecacheScreen
import com.example.spotifylyricsproxy.ui.precache.PrecacheViewModel
import com.example.spotifylyricsproxy.ui.settings.SettingsScreen
import com.example.spotifylyricsproxy.ui.theme.ThemePreferences

sealed class NavRoute(val route: String, @StringRes val labelRes: Int) {
    data object Playback : NavRoute("playback", R.string.nav_playback)
    data object Cache : NavRoute("cache", R.string.nav_cache)
    data object Precache : NavRoute("precache", R.string.nav_precache)
    data object Settings : NavRoute("settings", R.string.nav_settings)
    data object Playlist : NavRoute("playlist", R.string.nav_playlist)
    data object LyricsCorrection : NavRoute("lyrics_correction", R.string.nav_correction)
}

@Composable
fun AppNavigation(
    playbackViewModel: PlaybackViewModel,
    cacheViewModel: CacheViewModel,
    precacheViewModel: PrecacheViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showMenu by remember { mutableStateOf(false) }
    var requestLyricSettings by remember { mutableStateOf(false) }
    val isPlayback = currentRoute == NavRoute.Playback.route
    val isDarkTheme = ThemePreferences.isDarkTheme()
    val appBackground = when {
        isPlayback -> Color(0xFF080D16)
        isDarkTheme -> Color(0xFF0B0F18)
        else -> Color(0xFFF7F8FC)
    }

    PredictiveBackHandler(enabled = showMenu) { progress -> progress.collect { }; showMenu = false }
    SystemBarsForRoute(isPlayback = isPlayback, isDarkTheme = isDarkTheme)

    // Secondary pages are pushed on top of the player so Back always returns to it.
    fun openPage(route: NavRoute) {
        showMenu = false
        navController.navigate(route.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(containerColor = appBackground) { _ ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Playback.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(NavRoute.Playback.route) {
                PlaybackScreen(
                    viewModel = playbackViewModel,
                    onOpenDrawer = { showMenu = true },
                    onOpenLyricsCorrection = { navController.navigate(NavRoute.LyricsCorrection.route) },
                    showLyricSettingsFromDrawer = requestLyricSettings,
                    onLyricSettingsShown = { requestLyricSettings = false }
                )
            }
            composable(NavRoute.Cache.route) {
                CacheScreen(viewModel = cacheViewModel, onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Precache.route) {
                PrecacheScreen(viewModel = precacheViewModel, onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.Playlist.route) {
                val playlistViewModel: PlaylistViewModel = viewModel()
                PlaylistScreen(
                    viewModel = playlistViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoute.LyricsCorrection.route) {
                LyricsCorrectionScreen(
                    viewModel = playbackViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    PlayerMenuSheet(
        visible = showMenu,
        viewModel = playbackViewModel,
        onDismiss = { showMenu = false },
        onOpenPage = ::openPage,
        onOpenLyricDisplay = {
            showMenu = false
            requestLyricSettings = true
        }
    )

    // One-time button tour, above the menu so it can walk through it too.
    val tourDone by com.example.spotifylyricsproxy.core.AppSettings.tourDone
    var tourReady by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(tourDone) {
        // Let the player's controls lay out (and report their bounds) first.
        if (!tourDone) {
            // After the guide's zoom into the player has finished.
            kotlinx.coroutines.delay(1200)
            tourReady = true
        }
    }
    if (tourReady && !tourDone) {
        com.example.spotifylyricsproxy.ui.playback.CoachTour(
            menuOpen = showMenu,
            onOpenMenu = { showMenu = true },
            onDone = {
                showMenu = false
                com.example.spotifylyricsproxy.core.AppSettings.finishTour()
            }
        )
    }
}

@Composable
@Suppress("DEPRECATION")
private fun SystemBarsForRoute(isPlayback: Boolean, isDarkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        val navigationBarColor = when {
            isPlayback -> Color(0xFF080D16)
            isDarkTheme -> Color(0xFF0B0F18)
            else -> Color(0xFFF7F8FC)
        }
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = if (isPlayback) Color.Transparent.toArgb() else navigationBarColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isPlayback && !isDarkTheme
            isAppearanceLightNavigationBars = !isPlayback && !isDarkTheme
        }
    }
}
