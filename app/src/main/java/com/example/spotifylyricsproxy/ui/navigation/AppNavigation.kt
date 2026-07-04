package com.example.spotifylyricsproxy.ui.navigation

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spotifylyricsproxy.ui.cache.CacheScreen
import com.example.spotifylyricsproxy.ui.cache.CacheViewModel
import com.example.spotifylyricsproxy.ui.playback.PlaybackScreen
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import com.example.spotifylyricsproxy.ui.precache.PrecacheScreen
import com.example.spotifylyricsproxy.ui.precache.PrecacheViewModel
import com.example.spotifylyricsproxy.ui.settings.SettingsScreen

sealed class NavRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Playback : NavRoute("playback", "播放", Icons.Filled.Home)
    data object Cache : NavRoute("cache", "缓存", Icons.Filled.Star)
    data object Precache : NavRoute("precache", "预缓存", Icons.Filled.Refresh)
    data object Settings : NavRoute("settings", "设置", Icons.Filled.Settings)
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
    val isPlayback = currentRoute == NavRoute.Playback.route

    SystemBarsForRoute(isPlayback = isPlayback)

    Scaffold(
        containerColor = if (isPlayback) Color(0xFF080D16) else Color(0xFFF7F8FC),
        bottomBar = {
            CompactBottomBar(
                currentRoute = currentRoute,
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Playback.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Playback.route) {
                PlaybackScreen(viewModel = playbackViewModel)
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
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun SystemBarsForRoute(isPlayback: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = (if (isPlayback) Color(0xFF080D16) else Color(0xFFF7F8FC)).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isPlayback
            isAppearanceLightNavigationBars = !isPlayback
        }
    }
}

@Composable
private fun CompactBottomBar(
    currentRoute: String?,
    onSelect: (NavRoute) -> Unit
) {
    val isPlayback = currentRoute == NavRoute.Playback.route
    val outerColor = if (isPlayback) Color(0xFF080D16) else Color(0xFFF7F8FC)
    val containerColor = if (isPlayback) Color.White.copy(alpha = 0.08f) else Color(0xFFF7F8FC)

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
            border = if (isPlayback) null else BorderStroke(1.dp, Color(0xFFE8EBF2)),
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
                        dark = isPlayback,
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
                contentDescription = item.label,
                modifier = Modifier.size(19.dp),
                tint = if (selected) selectedColor else idleColor
            )
        }
        Text(
            text = item.label,
            color = if (selected) selectedColor else idleColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}
