package com.example.spotifylyricsproxy.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spotifylyricsproxy.ui.cache.CacheScreen
import com.example.spotifylyricsproxy.ui.playback.PlaybackScreen
import com.example.spotifylyricsproxy.ui.precache.PrecacheScreen
import com.example.spotifylyricsproxy.ui.settings.SettingsScreen

sealed class NavRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Playback : NavRoute("playback", "播放", Icons.Filled.Home)
    data object Cache : NavRoute("cache", "缓存", Icons.Filled.Storage)
    data object Precache : NavRoute("precache", "预缓存", Icons.Filled.Download)
    data object Settings : NavRoute("settings", "设置", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    NavRoute.Playback,
    NavRoute.Cache,
    NavRoute.Precache,
    NavRoute.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Playback.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Playback.route) {
                PlaybackScreen()
            }
            composable(NavRoute.Cache.route) {
                CacheScreen()
            }
            composable(NavRoute.Precache.route) {
                PrecacheScreen()
            }
            composable(NavRoute.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
