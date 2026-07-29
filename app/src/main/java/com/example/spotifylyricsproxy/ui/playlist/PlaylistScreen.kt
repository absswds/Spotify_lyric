package com.example.spotifylyricsproxy.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyPlaylistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val loadingPlaylists by viewModel.loadingPlaylists.collectAsState()
    val loadingTracks by viewModel.loadingTracks.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val trackProgress by viewModel.trackProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playback_playlist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.correction_back)
                        )
                    }
                },
                actions = {
                    // Search button placeholder
                }
            )
        },
        containerColor = Color(0xFFF7F8FC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索歌单") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearSearch) {
                            Icon(Icons.Filled.Clear, "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFF0F0F5),
                    focusedContainerColor = Color(0xFFF0F0F5)
                )
            )
            Text(text = "query: $searchQuery", fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp))

            // Search results or normal playlist carousel
            if (searchQuery.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (error != null && searchResults.isEmpty()) {
                        item {
                            Text(
                                text = error!!,
                                modifier = Modifier.padding(32.dp),
                                color = Color(0xFF747B89),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (searchResults.isNotEmpty()) {
                        itemsIndexed(searchResults) { _, item ->
                            PlaylistSearchItem(
                                playlist = item,
                                onClick = {
                                    viewModel.selectPlaylist(item)
                                    viewModel.clearSearch()
                                }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "",
                                modifier = Modifier.padding(32.dp),
                                color = Color(0xFF747B89),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                // Playlist carousel
                PlaylistCarousel(
                    playlists = playlists,
                    selectedPlaylist = selectedPlaylist,
                    isLoading = loadingPlaylists,
                    error = error,
                    onSelectPlaylist = viewModel::selectPlaylist,
                    onRefresh = viewModel::loadPlaylists
                )
            }

            // Loading indicator for tracks
            if (loadingTracks && trackProgress != null && searchQuery.isEmpty()) {
                val (loaded, total) = trackProgress!!
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { (loaded.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.playlist_loading_tracks_progress, loaded, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF747B89)
                    )
                }
            } else if (loadingTracks && searchQuery.isEmpty()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.playlist_loading_tracks),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF747B89)
                    )
                }
            }

            // Track list — hidden during search
            if (selectedPlaylist != null && searchQuery.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (tracks.isEmpty() && !loadingTracks) {
                        item {
                            Text(
                                text = if (error != null) error!! else stringResource(R.string.playlist_no_songs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF747B89),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(tracks, key = { it.id }) { track ->
                        val trackIndex = tracks.indexOf(track)
                        TrackRow(
                            track = track,
                            onClick = {
                                selectedPlaylist?.let { playlist ->
                                    viewModel.playTrack(track, playlist.id, trackIndex)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCarousel(
    playlists: List<SpotifyPlaylistItem>,
    selectedPlaylist: SpotifyPlaylistItem?,
    isLoading: Boolean,
    error: String?,
    onSelectPlaylist: (SpotifyPlaylistItem) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.playlist_my_playlists),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isLoading) stringResource(R.string.playlist_refresh_loading) else stringResource(R.string.playlist_cd_refresh),
                style = MaterialTheme.typography.labelLarge,
                color = if (isLoading) Color(0xFF747B89) else Color(0xFF4F5EDC),
                modifier = Modifier
                    .clickable(enabled = !isLoading) { onRefresh() }
                    .padding(8.dp)
            )
        }

        if (error != null && playlists.isEmpty()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF3B30),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (playlists.isEmpty()) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.playlist_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF747B89)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                playlists.forEach { playlist ->
                    val selected = selectedPlaylist?.id == playlist.id
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) Color(0xFF4F5EDC) else Color(0xFFEFF1F6),
                        contentColor = if (selected) Color.White else Color(0xFF27314A),
                        modifier = Modifier.clickable { onSelectPlaylist(playlist) }
                    ) {
                        Text(
                            text = playlist.name,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: PlaylistTrack,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEFF1F6)),
                contentAlignment = Alignment.Center
            ) {
                if (track.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track.imageUrl)
                            .crossfade(true)
                            .size(120)
                            .build(),
                        contentDescription = stringResource(R.string.playback_cd_album_art_placeholder),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.playback_cd_play),
                        tint = Color(0xFF4F5EDC),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF747B89),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatMs(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF747B89)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


@Composable
private fun PlaylistSearchItem(
    playlist: SpotifyPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imgUrl = playlist.images.firstOrNull()?.url
        if (imgUrl != null) {
            AsyncImage(
                model = imgUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8EBF2)))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(playlist.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (!playlist.description.isNullOrBlank()) {
                Text(playlist.description!!.take(60), color = Color(0xFF747B89), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${playlist.tracks.total} tracks", color = Color(0xFF747B89), fontSize = 12.sp)
        }
    }
}
