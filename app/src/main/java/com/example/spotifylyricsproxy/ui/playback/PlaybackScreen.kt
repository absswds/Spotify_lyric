package com.example.spotifylyricsproxy.ui.playback

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo

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
    val palette = remember(albumArt) { albumPalette(albumArt) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.deep,
                            palette.mid,
                            Color(0xFF0A0D14)
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlaybackTopBar(
                    state = connectionState,
                    onConnect = { viewModel.authorize(activity) },
                    onDisconnect = viewModel::disconnect
                )

                Spacer(modifier = Modifier.height(22.dp))

                AlbumArtHero(albumArt = albumArt, accent = palette.accent)

                Spacer(modifier = Modifier.height(22.dp))

                TrackTitleBlock(trackInfo = trackInfo, connectionState = connectionState)

                Spacer(modifier = Modifier.height(22.dp))

                LyricsBlock(
                    currentLine = currentLyricLine,
                    allLines = parsedLyrics,
                    status = lyricStatus
                )

                Spacer(modifier = Modifier.height(22.dp))

                ProgressBlock(
                    estimatedPositionMs = estimatedPositionMs,
                    durationMs = trackInfo.durationMs,
                    accent = palette.accent
                )

                Spacer(modifier = Modifier.height(18.dp))

                PlaybackControls(
                    isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                    isConnected = connectionState is SpotifyConnectionState.Connected,
                    accent = palette.accent,
                    onPlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious
                )

                Spacer(modifier = Modifier.height(18.dp))

                OffsetControls()
            }
        }
    }
}

@Composable
private fun PlaybackTopBar(
    state: SpotifyConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stateLabel(state),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state !is SpotifyConnectionState.Connected) {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                ) {
                    Text("连接")
                }
            } else {
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("断开")
                }
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun AlbumArtHero(albumArt: Bitmap?, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.78f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.2f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            if (albumArt != null) {
                Image(
                    bitmap = albumArt.asImageBitmap(),
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "封面占位",
                    modifier = Modifier.size(86.dp),
                    tint = Color.White.copy(alpha = 0.84f)
                )
            }
        }
    }
}

@Composable
private fun TrackTitleBlock(
    trackInfo: SpotifyTrackInfo,
    connectionState: SpotifyConnectionState
) {
    Text(
        text = trackInfo.title.ifEmpty { "等待播放" },
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = if (trackInfo.artist.isNotEmpty()) {
            listOf(trackInfo.artist, trackInfo.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        } else {
            if (connectionState is SpotifyConnectionState.Connected) "正在等待 Spotify 播放" else "连接 Spotify 后同步播放状态"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.66f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun LyricsBlock(
    currentLine: LrcLine?,
    allLines: List<LrcLine>,
    status: LyricStatus
) {
    val currentIndex = if (currentLine != null) allLines.indexOf(currentLine) else -1
    val previous = allLines.getOrNull(currentIndex - 1)?.text
    val next = allLines.getOrNull(currentIndex + 1)?.text

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.11f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (status) {
                is LyricStatus.Idle -> LyricMessage("等待播放...")
                is LyricStatus.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LyricMessage("正在查找歌词")
                }
                is LyricStatus.Synced -> {
                    previous?.let { ContextLyric(it) }
                    Text(
                        text = currentLine?.text ?: "...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                    next?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        ContextLyric(it)
                    }
                }
                is LyricStatus.PlainOnly -> LyricMessage("当前歌曲暂无同步歌词")
                is LyricStatus.NotFound -> LyricMessage("暂未找到歌词")
                is LyricStatus.LowConfidence -> LyricMessage("找到疑似歌词，需要手动确认")
                is LyricStatus.ParseError -> LyricMessage("歌词解析失败")
                is LyricStatus.Error -> LyricMessage("歌词加载失败")
            }
        }
    }
}

@Composable
private fun LyricMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.78f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ContextLyric(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.48f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ProgressBlock(
    estimatedPositionMs: Long,
    durationMs: Long,
    accent: Color
) {
    if (durationMs <= 0) return

    val progress = (estimatedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = Color.White.copy(alpha = 0.18f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMs(estimatedPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.56f)
            )
            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.56f)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isConnected: Boolean,
    accent: Color,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSkipPrevious, enabled = isConnected) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "上一首",
                modifier = Modifier.size(34.dp),
                tint = Color.White.copy(alpha = if (isConnected) 0.86f else 0.28f)
            )
        }
        Spacer(modifier = Modifier.width(28.dp))
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = accent,
            contentColor = Color.White,
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = onPlayPause, enabled = isConnected) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(42.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(28.dp))
        IconButton(onClick = onSkipNext, enabled = isConnected) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "下一首",
                modifier = Modifier.size(34.dp),
                tint = Color.White.copy(alpha = if (isConnected) 0.86f else 0.28f)
            )
        }
    }
}

@Composable
private fun OffsetControls() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OffsetButton("-0.2s")
        Text(
            text = "歌词偏移",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.66f)
        )
        OffsetButton("+0.2s")
    }
}

@Composable
private fun OffsetButton(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private data class AlbumPalette(
    val deep: Color,
    val mid: Color,
    val accent: Color
)

private fun albumPalette(bitmap: Bitmap?): AlbumPalette {
    if (bitmap == null) {
        return AlbumPalette(
            deep = Color(0xFF111827),
            mid = Color(0xFF243B55),
            accent = Color(0xFF6D7DFF)
        )
    }

    val stepX = (bitmap.width / 18).coerceAtLeast(1)
    val stepY = (bitmap.height / 18).coerceAtLeast(1)
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0L

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(pixel)
            g += android.graphics.Color.green(pixel)
            b += android.graphics.Color.blue(pixel)
            count++
            x += stepX
        }
        y += stepY
    }

    val avgR = (r / count).toInt()
    val avgG = (g / count).toInt()
    val avgB = (b / count).toInt()
    return AlbumPalette(
        deep = Color(avgR / 255f * 0.28f, avgG / 255f * 0.28f, avgB / 255f * 0.28f),
        mid = Color(avgR / 255f * 0.46f, avgG / 255f * 0.46f, avgB / 255f * 0.46f),
        accent = Color(
            red = (avgR + 64).coerceAtMost(255) / 255f,
            green = (avgG + 64).coerceAtMost(255) / 255f,
            blue = (avgB + 64).coerceAtMost(255) / 255f
        )
    )
}

private fun stateLabel(state: SpotifyConnectionState): String = when (state) {
    is SpotifyConnectionState.Disconnected -> "未连接 Spotify"
    is SpotifyConnectionState.Connecting -> "正在连接..."
    is SpotifyConnectionState.Connected -> "跟随封面"
    is SpotifyConnectionState.Error -> "连接失败"
    SpotifyConnectionState.SpotifyNotInstalled -> "未安装 Spotify"
    SpotifyConnectionState.SpotifyNotLoggedIn -> "Spotify 未登录"
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
