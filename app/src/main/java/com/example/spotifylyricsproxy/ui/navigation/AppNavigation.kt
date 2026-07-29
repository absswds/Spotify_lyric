package com.example.spotifylyricsproxy.ui.navigation

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.spotifylyricsproxy.ui.theme.ThemePreferences
import com.example.spotifylyricsproxy.ui.playback.LyricsCorrectionScreen
import com.example.spotifylyricsproxy.ui.playback.PlaybackScreen
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import com.example.spotifylyricsproxy.ui.playlist.PlaylistScreen
import com.example.spotifylyricsproxy.ui.playlist.PlaylistViewModel
import com.example.spotifylyricsproxy.ui.precache.PrecacheScreen
import com.example.spotifylyricsproxy.ui.precache.PrecacheViewModel
import com.example.spotifylyricsproxy.ui.settings.SettingsScreen

sealed class NavRoute(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Playback : NavRoute("playback", R.string.nav_playback, Icons.Filled.Home)
    data object Cache : NavRoute("cache", R.string.nav_cache, Icons.Filled.Star)
    data object Precache : NavRoute("precache", R.string.nav_precache, Icons.Filled.Refresh)
    data object Settings : NavRoute("settings", R.string.nav_settings, Icons.Filled.Settings)
    data object Playlist : NavRoute("playlist", R.string.nav_playlist, Icons.Filled.Home)
    data object LyricsCorrection : NavRoute("lyrics_correction", R.string.nav_correction, Icons.Filled.Home)
}

val bottomNavItems = listOf(
    NavRoute.Playback,
    NavRoute.Cache,
    NavRoute.Precache,
    NavRoute.Settings
)

@Composable
fun AppNavigation(
    playbackViewModel: PlaybackViewModel,
    cacheViewModel: CacheViewModel,
    precacheViewModel: PrecacheViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showRightDrawer by remember { mutableStateOf(false) }
    val isPlayback = currentRoute == NavRoute.Playback.route
    val isDarkTheme = ThemePreferences.isDarkTheme()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Use smallestScreenWidthDp — stable regardless of rotation.
    // 600dp+ = tablet (show bottom nav in landscape); below = phone (hide it).
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val showBottomBar = currentRoute in bottomNavItems.map { it.route } && !isPlayback
    val appBackground = when {
        isPlayback -> Color(0xFF080D16)
        isDarkTheme -> Color(0xFF0B0F18)
        else -> Color(0xFFF7F8FC)
    }

    PredictiveBackHandler(enabled = showRightDrawer) { progress -> progress.collect { }; showRightDrawer = false }
    SystemBarsForRoute(isPlayback = isPlayback, isDarkTheme = isDarkTheme)

    Scaffold(
        containerColor = appBackground,
        bottomBar = {
            if (showBottomBar) {
                CompactBottomBar(
                    currentRoute = currentRoute,
                    isDarkTheme = isDarkTheme,
                    onSelect = { item ->
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Playback.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Playback.route) {
                PlaybackScreen(
                    viewModel = playbackViewModel,
                    onOpenDrawer = { showRightDrawer = true },
                    onOpenPlaylist = {
                        navController.navigate(NavRoute.Playlist.route)
                    },
                    onOpenLyricsCorrection = {
                        navController.navigate(NavRoute.LyricsCorrection.route)
                    }
                )
            }
            composable(NavRoute.Cache.route) {
                CacheScreen(viewModel = cacheViewModel)
            }
            composable(NavRoute.Precache.route) {
                PrecacheScreen(viewModel = precacheViewModel)
            }
            composable(NavRoute.Settings.route) {
                SettingsScreen()
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
        AnimatedVisibility(
            visible = showRightDrawer,
            enter = slideInHorizontally { it } + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally { it } + fadeOut(animationSpec = tween(180))
        ) {
            Box(Modifier.fillMaxSize().clickable { showRightDrawer = false }) {
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                        .background(Color(0xFF0B0F18))
                ) {
                    Column(Modifier.fillMaxSize().padding(top = 52.dp, start = 18.dp, end = 18.dp, bottom = 26.dp)) {
                        Text(text = stringResource(R.string.nav_menu_title), color = Color.White,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp))
                        listOf(
                            NavRoute.Playlist, NavRoute.Cache, NavRoute.Precache,
                            NavRoute.Settings, NavRoute.LyricsCorrection
                        ).forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        showRightDrawer = false
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(item.icon, null, Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.75f))
                                Text(stringResource(item.labelRes), color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { showRightDrawer = false }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Settings, null, Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.75f))
                            Text(stringResource(R.string.nav_lyric_display), color = Color.White.copy(alpha = 0.75f),
                                fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
            }
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
        window.navigationBarColor = navigationBarColor.toArgb()
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

@Composable
private fun CompactBottomBar(
    currentRoute: String?,
    isDarkTheme: Boolean,
    onSelect: (NavRoute) -> Unit
) {
    val isPlayback = currentRoute == NavRoute.Playback.route
    val darkChrome = isPlayback || isDarkTheme
    val outerColor = if (darkChrome) Color(0xFF080D16) else Color(0xFFF7F8FC)
    val containerColor = if (darkChrome) Color.White.copy(alpha = 0.08f) else Color(0xFFF7F8FC)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(outerColor)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            border = if (darkChrome) null else BorderStroke(1.dp, Color(0xFFE8EBF2)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { item ->
                    CompactBottomBarItem(
                        item = item,
                        selected = currentRoute == item.route,
                        dark = darkChrome,
                        onClick = { onSelect(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactBottomBarItem(
    item: NavRoute,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = if (dark) Color.White else Color(0xFF27314A)
    val idleColor = if (dark) Color.White.copy(alpha = 0.54f) else Color(0xFF747B89)
    val pillColor = if (dark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color(0xFFECE8FF)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) pillColor else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = stringResource(item.labelRes),
                modifier = Modifier.size(19.dp),
                tint = if (selected) selectedColor else idleColor
            )
        }
        Text(
            text = stringResource(item.labelRes),
            color = if (selected) selectedColor else idleColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}
