package com.example.spotifylyricsproxy.ui.playback

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(viewModel: PlaybackViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val trackInfo by viewModel.currentTrack.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val estimatedPositionMs by viewModel.estimatedPositionMs.collectAsState()
    val currentLyricLine by viewModel.currentLyricLine.collectAsState()
    val parsedLyrics by viewModel.parsedLyrics.collectAsState()
    val lyricStatus by viewModel.lyricStatus.collectAsState()
    val activity = LocalContext.current as Activity

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("播放") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection status section
            ConnectionStatusSection(connectionState, { viewModel.authorize(activity) }, viewModel::disconnect)

            Spacer(modifier = Modifier.height(24.dp))

            // Now playing section
            NowPlayingSection(trackInfo, albumArt, estimatedPositionMs, connectionState)

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics section
            LyricsSection(currentLyricLine, parsedLyrics, lyricStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            PlaybackControls(
                isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                isConnected = connectionState is SpotifyConnectionState.Connected,
                onPlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Debug info
            DebugInfoSection(trackInfo, connectionState)
        }
    }
}

@Composable
private fun ConnectionStatusSection(
    state: SpotifyConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is SpotifyConnectionState.Connected ->
                    MaterialTheme.colorScheme.primaryContainer
                is SpotifyConnectionState.Error, SpotifyConnectionState.SpotifyNotInstalled,
                SpotifyConnectionState.SpotifyNotLoggedIn ->
                    MaterialTheme.colorScheme.errorContainer
                is SpotifyConnectionState.Connecting ->
                    MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Spotify 状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stateLabel(state),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (state is SpotifyConnectionState.Connecting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            when (state) {
                is SpotifyConnectionState.Disconnected,
                is SpotifyConnectionState.Error,
                is SpotifyConnectionState.SpotifyNotInstalled,
                is SpotifyConnectionState.SpotifyNotLoggedIn -> {
                    Button(onClick = onConnect) {
                        Text("连接")
                    }
                }
                is SpotifyConnectionState.Connected -> {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("断开")
                    }
                }
                is SpotifyConnectionState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun NowPlayingSection(
    trackInfo: SpotifyTrackInfo,
    albumArt: Bitmap?,
    estimatedPositionMs: Long,
    connectionState: SpotifyConnectionState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Album art
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = "专辑封面",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "封面",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song title
            Text(
                text = trackInfo.title.ifEmpty { "未播放" },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Artist - Album
            Text(
                text = if (trackInfo.artist.isNotEmpty()) {
                    "${trackInfo.artist} — ${trackInfo.album}"
                } else {
                    if (connectionState is SpotifyConnectionState.Connected) "正在等待播放..." else "请先连接 Spotify"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Progress bar
            if (trackInfo.durationMs > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                val progress = (estimatedPositionMs.toFloat() / trackInfo.durationMs.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(estimatedPositionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMs(trackInfo.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isConnected: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSkipPrevious,
            enabled = isConnected
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "上一首",
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        IconButton(
            onClick = onPlayPause,
            enabled = isConnected
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        IconButton(
            onClick = onSkipNext,
            enabled = isConnected
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "下一首",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun LyricsSection(
    currentLine: LrcLine?,
    allLines: List<LrcLine>,
    status: LyricStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "歌词",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (status) {
                is LyricStatus.Idle -> {
                    Text(
                        text = "等待播放...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is LyricStatus.Searching -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在搜索歌词...")
                    }
                }
                is LyricStatus.Synced -> {
                    Text(
                        text = currentLine?.text ?: "...",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    // Show surrounding context
                    if (allLines.isNotEmpty() && currentLine != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val idx = allLines.indexOf(currentLine)
                        if (idx > 0) {
                            Text(
                                text = allLines[idx - 1].text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (idx >= 0 && idx + 1 < allLines.size) {
                            Text(
                                text = allLines[idx + 1].text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is LyricStatus.PlainOnly -> {
                    Text(
                        text = "仅有普通歌词，无同步时间轴",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is LyricStatus.NotFound -> {
                    Text(
                        text = "未找到歌词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is LyricStatus.LowConfidence -> {
                    Text(
                        text = "匹配置信度不足 (${status.score}分)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is LyricStatus.ParseError -> {
                    Text(
                        text = "歌词解析失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is LyricStatus.Error -> {
                    Text(
                        text = "错误: ${status.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugInfoSection(
    trackInfo: SpotifyTrackInfo,
    connectionState: SpotifyConnectionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "调试信息",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            DebugRow("连接状态", stateLabel(connectionState))
            DebugRow("Track ID", trackInfo.trackId.ifEmpty { "—" })
            DebugRow("Track URI", trackInfo.trackUri.ifEmpty { "—" })
            DebugRow("播放位置", "${trackInfo.playbackPositionMs}ms / ${trackInfo.durationMs}ms")
            DebugRow("播放状态", if (trackInfo.isPaused) "已暂停" else "播放中")
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun stateLabel(state: SpotifyConnectionState): String = when (state) {
    is SpotifyConnectionState.Disconnected -> "未连接"
    is SpotifyConnectionState.Connecting -> "正在连接..."
    is SpotifyConnectionState.Connected -> "已连接"
    is SpotifyConnectionState.Error -> "错误: ${state.message}"
    SpotifyConnectionState.SpotifyNotInstalled -> "未安装 Spotify"
    SpotifyConnectionState.SpotifyNotLoggedIn -> "Spotify 未登录"
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
