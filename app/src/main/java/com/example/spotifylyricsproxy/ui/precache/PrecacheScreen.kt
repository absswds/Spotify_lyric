package com.example.spotifylyricsproxy.ui.precache

import android.app.Activity
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.database.entity.PlaylistCacheJobEntity
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrecacheScreen(viewModel: PrecacheViewModel) {
    val isAuthorized by viewModel.isAuthorized.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val cachedJobs by viewModel.cachedJobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val activity = LocalContext.current as Activity
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.precache_screen_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isAuthorized) {
                // Auth section
                AuthSection(
                    onLogin = { viewModel.authorize(activity) }
                )
            } else {
                // User is authorized - show playlists
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.precache_your_playlists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.precache_logout), style = MaterialTheme.typography.bodySmall)
                    }
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (playlists.isEmpty()) {
                        EmptyPlaylistsState(onRefresh = { viewModel.loadPlaylists() })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(playlists) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    job = cachedJobs.find { it.playlistId == playlist.id },
                                    progress = progressMap[playlist.id],
                                    canPrecache = currentUserId.isNotBlank() && viewModel.isPlaylistCacheable(playlist),
                                    onPrecache = { viewModel.precachePlaylist(playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthSection(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.precache_screen_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.precache_login_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogin) {
            Text(stringResource(R.string.precache_login_spotify))
        }
    }
}

@Composable
private fun EmptyPlaylistsState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.precache_no_playlists),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRefresh) {
            Text(stringResource(R.string.precache_retry))
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: SpotifyPlaylistItem,
    job: PlaylistCacheJobEntity?,
    progress: PrecacheProgress?,
    canPrecache: Boolean,
    onPrecache: () -> Unit
) {
    val isRunning = progress?.isRunning == true
    val hasFinishedJob = job != null && job.lastScanAt != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRunning && progress!!.totalTracks > 0)
                            stringResource(R.string.precache_cached_format, progress.cachedTracks, progress.totalTracks)
                        else
                            playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isRunning -> stringResource(R.string.precache_processing, progress!!.progressPercent)
                            !canPrecache -> stringResource(R.string.precache_owner_only, playlist.tracks.total)
                            else -> stringResource(R.string.precache_tracks_count, playlist.tracks.total)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasFinishedJob) {
                        Spacer(modifier = Modifier.height(4.dp))
                        PrecacheProgressRow(job)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onPrecache,
                    enabled = canPrecache && !isRunning
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (hasFinishedJob) stringResource(R.string.precache_retry) else stringResource(R.string.precache_cache_button))
                    }
                }
            }

            // Progress bar during caching
            if (isRunning && progress!!.totalTracks > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (progress.cachedTracks + progress.failedTracks + progress.notFoundTracks).toFloat() / progress.totalTracks },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    color = Color(0xFF34C759),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrecacheProgressRow(job: PlaylistCacheJobEntity) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF34C759),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${job.cachedTracks}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF34C759)
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (job.notFoundTracks > 0) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${job.notFoundTracks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9500)
                )
            }
            if (job.failedTracks > 0) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${job.failedTracks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF3B30)
                )
            }
        }
    }
}
