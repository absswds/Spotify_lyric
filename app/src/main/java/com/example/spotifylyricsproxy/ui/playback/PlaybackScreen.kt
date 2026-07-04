package com.example.spotifylyricsproxy.ui.playback

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import kotlinx.coroutines.launch

@Composable
fun PlaybackScreen(
    viewModel: PlaybackViewModel,
    onOpenPlaylist: () -> Unit = {},
    onOpenLyricsCorrection: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val trackInfo by viewModel.currentTrack.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val estimatedPositionMs by viewModel.estimatedPositionMs.collectAsState()
    val currentLyricLine by viewModel.currentLyricLine.collectAsState()
    val parsedLyrics by viewModel.parsedLyrics.collectAsState()
    val lyricStatus by viewModel.lyricStatus.collectAsState()
    val palette = remember(albumArt) { albumPalette(albumArt) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showLyricDisplaySettings by remember { mutableStateOf(false) }
    val isSpotifyInstalled = rememberIsSpotifyInstalled()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
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
            if (lyricsExpanded && lyricStatus is LyricStatus.Synced) {
                ExpandedLyricsView(
                    lines = parsedLyrics,
                    currentLine = currentLyricLine,
                    estimatedPositionMs = estimatedPositionMs,
                    durationMs = trackInfo.durationMs,
                    isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                    accent = palette.accent,
                    onSeek = viewModel::seekTo,
                    onPlayPause = viewModel::togglePlayPause,
                    onCollapse = { lyricsExpanded = false }
                )
            } else if (isLandscape) {
                LandscapePlaybackLayout(
                    albumArt = albumArt,
                    trackInfo = trackInfo,
                    connectionState = connectionState,
                    palette = palette,
                    estimatedPositionMs = estimatedPositionMs,
                    durationMs = trackInfo.durationMs,
                    isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                    isConnected = connectionState is SpotifyConnectionState.Connected,
                    parsedLyrics = parsedLyrics,
                    currentLyricLine = currentLyricLine,
                    lyricStatus = lyricStatus,
                    isSpotifyInstalled = isSpotifyInstalled,
                    onSeek = viewModel::seekTo,
                    onPlayPause = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onConnect = viewModel::connect,
                    onOpenSpotify = viewModel::openSpotifyAndConnect,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenLyricsCorrection = onOpenLyricsCorrection,
                    onDisconnect = viewModel::disconnect,
                    onLyricDisplaySettings = { showLyricDisplaySettings = true }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CompactTopBar(
                        state = connectionState,
                        onOpenPlaylist = onOpenPlaylist,
                        onCorrection = onOpenLyricsCorrection,
                        onLyricDisplaySettings = { showLyricDisplaySettings = true },
                        onDisconnect = viewModel::disconnect
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AlbumArtHero(albumArt = albumArt, accent = palette.accent)

                        Spacer(modifier = Modifier.height(20.dp))

                        TrackTitleBlock(trackInfo = trackInfo, connectionState = connectionState)

                        Spacer(modifier = Modifier.height(18.dp))

                        if (connectionState is SpotifyConnectionState.Connected) {
                            CompactLyricsBlock(
                                currentLine = currentLyricLine,
                                allLines = parsedLyrics,
                                status = lyricStatus,
                                onExpand = { lyricsExpanded = true }
                            )
                        } else {
                            ConnectActionPanel(
                                state = connectionState,
                                accent = palette.accent,
                                isSpotifyInstalled = isSpotifyInstalled,
                                onConnect = viewModel::connect,
                                onOpenSpotify = viewModel::openSpotifyAndConnect
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }

                    BottomPlaybackPane(
                        estimatedPositionMs = estimatedPositionMs,
                        durationMs = trackInfo.durationMs,
                        accent = palette.accent,
                        isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                        isConnected = connectionState is SpotifyConnectionState.Connected,
                        onSeek = viewModel::seekTo,
                        onPlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipNext,
                        onSkipPrevious = viewModel::skipPrevious
                    )
                }
            }
        }

        if (showLyricDisplaySettings) {
            LyricDisplaySettingsDialog(onDismiss = { showLyricDisplaySettings = false })
        }
    }
}

@Composable
private fun CompactTopBar(
    state: SpotifyConnectionState,
    onOpenPlaylist: () -> Unit,
    onCorrection: () -> Unit,
    onLyricDisplaySettings: () -> Unit,
    onDisconnect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Connection indicator — compact
        Row(verticalAlignment = Alignment.CenterVertically) {
            val connected = state is SpotifyConnectionState.Connected
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connected) Color(0xFF34C759) else Color(0xFFFF3B30).copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stateLabel(state),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f)
            )
        }

        // Menu with options
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White.copy(alpha = 0.78f)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Spotify 姝屽崟") },
                    onClick = {
                        showMenu = false
                        onOpenPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("歌词修正") },
                    onClick = {
                        showMenu = false
                        onCorrection()
                    }
                )
                DropdownMenuItem(
                    text = { Text("歌词显示") },
                    onClick = {
                        showMenu = false
                        onLyricDisplaySettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("断开连接") },
                    onClick = {
                        showMenu = false
                        onDisconnect()
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistEntryButton(
    accent: Color,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "歌单",
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Spotify 歌单",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "浏览歌单 · 点歌播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.56f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "打开",
                tint = Color.White.copy(alpha = 0.48f)
            )
        }
    }
}

@Composable
private fun ConnectActionPanel(
    state: SpotifyConnectionState,
    accent: Color,
    isSpotifyInstalled: Boolean,
    onConnect: () -> Unit,
    onOpenSpotify: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = connectionHint(state),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (state is SpotifyConnectionState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionPill(
                        text = "重新连接",
                        accent = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                        enabled = true,
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSpotifyInstalled) {
                        ActionPill(
                            text = "打开 Spotify 后连接",
                            accent = accent,
                            contentColor = readableOn(accent),
                            enabled = true,
                            onClick = onOpenSpotify,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    accent: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) accent else Color.White.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) contentColor else Color.White.copy(alpha = 0.36f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AlbumArtHero(albumArt: Bitmap?, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.66f)
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
                            accent.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.18f)
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
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
private fun CompactLyricsBlock(
    currentLine: LrcLine?,
    allLines: List<LrcLine>,
    status: LyricStatus,
    onExpand: () -> Unit
) {
    val currentIndex = if (currentLine != null) allLines.indexOf(currentLine) else -1
    val previous = allLines.getOrNull(currentIndex - 1)?.text
    val next = allLines.getOrNull(currentIndex + 1)?.text
    val config = LyricDisplayPreferences.resolvedConfig()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (status is LyricStatus.Synced && allLines.isNotEmpty()) {
                    onExpand()
                }
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
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
                        textAlign = config.textAlign,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    next?.let {
                        Spacer(modifier = Modifier.height(10.dp))
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
    val config = LyricDisplayPreferences.resolvedConfig()
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.48f),
        textAlign = config.textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun BottomPlaybackPane(
    estimatedPositionMs: Long,
    durationMs: Long,
    accent: Color,
    isPlaying: Boolean,
    isConnected: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProgressBlock(
            estimatedPositionMs = estimatedPositionMs,
            durationMs = durationMs,
            accent = accent,
            onSeek = onSeek
        )

        Spacer(modifier = Modifier.height(10.dp))

        PlaybackControls(
            isPlaying = isPlaying,
            isConnected = isConnected,
            accent = accent,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious
        )
    }
}

@Composable
private fun ProgressBlock(
    estimatedPositionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit
) {
    if (durationMs <= 0) return

    val progress = (estimatedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var draggingProgress by remember { mutableStateOf<Float?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = draggingProgress ?: progress,
            onValueChange = { fraction ->
                draggingProgress = fraction
            },
            onValueChangeFinished = {
                draggingProgress?.let {
                    onSeek((it * durationMs).toLong().coerceIn(0, durationMs))
                }
                draggingProgress = null
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = draggingProgress?.let { (it * durationMs).toLong() } ?: estimatedPositionMs
            Text(
                text = formatMs(displayMs),
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
        val buttonBg = if (isConnected) accent else Color.White.copy(alpha = 0.14f)
        val buttonContent = if (buttonBg.let { c ->
                0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
            } > 0.55f
        ) Color.Black else Color.White
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = buttonBg,
            contentColor = buttonContent,
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = onPlayPause, enabled = isConnected) {
                if (isPlaying) {
                    PauseGlyph(color = buttonContent)
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(42.dp)
                    )
                }
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
private fun PauseGlyph(color: Color = Color.White) {
    Row(
        modifier = Modifier.size(42.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PauseBar(color = color)
        Spacer(modifier = Modifier.width(7.dp))
        PauseBar(color = color)
    }
}

@Composable
private fun PauseBar(color: Color) {
    Box(
        modifier = Modifier
            .width(7.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
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

@Composable
private fun ExpandedLyricsView(
    lines: List<LrcLine>,
    currentLine: LrcLine?,
    estimatedPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    accent: Color,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onCollapse: () -> Unit
) {
    val currentIndex = currentLine?.let { lines.indexOf(it) } ?: -1
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val config = LyricDisplayPreferences.resolvedConfig()

    // Detect if the current line has scrolled out of the viewport
    val isScrolledAway by remember(currentIndex) {
        derivedStateOf {
            if (currentIndex < 0) false
            else listState.layoutInfo.visibleItemsInfo.none { it.index == currentIndex }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Lyrics list — leave bottom room for the control bar
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 56.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == currentIndex
                val isPast = index < currentIndex
                Text(
                    text = line.text,
                    color = when {
                        isCurrent -> Color.White
                        isPast -> Color.White.copy(alpha = config.pastLineAlpha)
                        else -> Color.White.copy(alpha = config.futureLineAlpha)
                    },
                    fontSize = if (isCurrent) config.currentLineSp else config.otherLineSp,
                    fontWeight = if (isCurrent) config.currentLineWeight else FontWeight.Normal,
                    textAlign = config.textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.startMs) }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }
        }

        // Top bar: collapse button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 8.dp, end = 4.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "收起歌词",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "全屏歌词",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.48f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Jump-to-current-line button — shows only when user scrolls away
        if (isScrolledAway && currentIndex >= 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem((currentIndex - 3).coerceAtLeast(0))
                        }
                    },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "回到当前行",
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Bottom control bar: progress + play/pause
        if (durationMs > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact progress slider
                val progress = (estimatedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                var draggingProgress by remember { mutableStateOf<Float?>(null) }
                val displayMs = draggingProgress?.let { (it * durationMs).toLong() } ?: estimatedPositionMs

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMs(displayMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.width(40.dp)
                    )

                    Slider(
                        value = draggingProgress ?: progress,
                        onValueChange = { draggingProgress = it },
                        onValueChangeFinished = {
                            draggingProgress?.let {
                                onSeek((it * durationMs).toLong().coerceIn(0, durationMs))
                            }
                            draggingProgress = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                        )
                    )

                    Text(
                        text = formatMs(durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Play / pause — same style as the compact controls
                    IconButton(onClick = onPlayPause) {
                        if (isPlaying) {
                            PauseGlyph(color = Color.White.copy(alpha = 0.78f))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "播放",
                                tint = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberIsSpotifyInstalled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        context.packageManager.getLaunchIntentForPackage("com.spotify.music") != null
    }
}

private fun stateLabel(state: SpotifyConnectionState): String = when (state) {
    is SpotifyConnectionState.Disconnected -> "未连接"
    is SpotifyConnectionState.Connecting -> "连接中..."
    is SpotifyConnectionState.Connected -> "已连接"
    is SpotifyConnectionState.Error -> "连接失败"
    SpotifyConnectionState.SpotifyNotInstalled -> "未安装 Spotify"
    SpotifyConnectionState.SpotifyNotLoggedIn -> "未登录"
}

private fun connectionHint(state: SpotifyConnectionState): String = when (state) {
    is SpotifyConnectionState.Connecting -> "正在连接 Spotify..."
    SpotifyConnectionState.SpotifyNotInstalled -> "没有检测到 Spotify，请先安装后再连接"
    SpotifyConnectionState.SpotifyNotLoggedIn -> "请先打开 Spotify 登录账号，再回到这里连接"
    is SpotifyConnectionState.Error -> "Spotify 没有连上：${state.message}"
    SpotifyConnectionState.Disconnected -> "连接 Spotify 后同步播放状态和歌词"
    SpotifyConnectionState.Connected -> ""
}

private fun readableOn(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.55f) Color.Black else Color.White
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun LandscapePlaybackLayout(
    albumArt: Bitmap?,
    trackInfo: SpotifyTrackInfo,
    connectionState: SpotifyConnectionState,
    palette: AlbumPalette,
    estimatedPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isConnected: Boolean,
    parsedLyrics: List<LrcLine>,
    currentLyricLine: LrcLine?,
    lyricStatus: LyricStatus,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    isSpotifyInstalled: Boolean,
    onConnect: () -> Unit,
    onOpenSpotify: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenLyricsCorrection: () -> Unit,
    onLyricDisplaySettings: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Left panel: album art, track info, controls (38%)
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompactTopBar(
                state = connectionState,
                onOpenPlaylist = onOpenPlaylist,
                onCorrection = onOpenLyricsCorrection,
                onLyricDisplaySettings = onLyricDisplaySettings,
                onDisconnect = onDisconnect
            )

            Spacer(modifier = Modifier.height(10.dp))

            AlbumArtHero(albumArt = albumArt, accent = palette.accent)

            Spacer(modifier = Modifier.height(12.dp))

            TrackTitleBlock(trackInfo = trackInfo, connectionState = connectionState)

            Spacer(modifier = Modifier.height(8.dp))

            ProgressBlock(
                estimatedPositionMs = estimatedPositionMs,
                durationMs = durationMs,
                accent = palette.accent,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(4.dp))

            PlaybackControls(
                isPlaying = isPlaying,
                isConnected = isConnected,
                accent = palette.accent,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious
            )
        }

        // Right panel: lyrics or connect action (62%)
        Column(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isConnected) {
                ConnectActionPanel(
                    state = connectionState,
                    accent = palette.accent,
                    isSpotifyInstalled = isSpotifyInstalled,
                    onConnect = onConnect,
                    onOpenSpotify = onOpenSpotify
                )
            } else {
                LandscapeLyricsContent(
                    lines = parsedLyrics,
                    currentLine = currentLyricLine,
                    lyricStatus = lyricStatus,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
private fun LandscapeLyricsContent(
    lines: List<LrcLine>,
    currentLine: LrcLine?,
    lyricStatus: LyricStatus,
    onSeek: (Long) -> Unit
) {
    when (lyricStatus) {
        is LyricStatus.Idle -> LyricMessage("等待播放...")
        is LyricStatus.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            LyricMessage("正在查找歌词")
        }
        is LyricStatus.Synced -> {
            if (lines.isNotEmpty()) {
                LandscapeLyricsList(lines, currentLine, onSeek)
            } else {
                LyricMessage("暂无歌词内容")
            }
        }
        is LyricStatus.PlainOnly -> LyricMessage("当前歌曲暂无同步歌词")
        is LyricStatus.NotFound -> LyricMessage("暂未找到歌词")
        is LyricStatus.LowConfidence -> LyricMessage("找到疑似歌词，需要手动确认")
        is LyricStatus.ParseError -> LyricMessage("歌词解析失败")
        is LyricStatus.Error -> LyricMessage("歌词加载失败")
    }
}

@Composable
private fun LandscapeLyricsList(
    lines: List<LrcLine>,
    currentLine: LrcLine?,
    onSeek: (Long) -> Unit
) {
    val currentIndex = currentLine?.let { lines.indexOf(it) } ?: -1
    val listState = rememberLazyListState()
    val config = LyricDisplayPreferences.resolvedConfig()

    // Auto-scroll to keep the current line centered
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            val isPast = index < currentIndex
            Text(
                text = line.text,
                color = when {
                    isCurrent -> Color.White
                    isPast -> Color.White.copy(alpha = config.pastLineAlpha)
                    else -> Color.White.copy(alpha = config.futureLineAlpha)
                },
                fontSize = if (isCurrent) config.currentLineSp else config.otherLineSp,
                fontWeight = if (isCurrent) config.currentLineWeight else FontWeight.Normal,
                textAlign = config.textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (line.startMs >= 0) onSeek(line.startMs)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun LyricDisplaySettingsDialog(onDismiss: () -> Unit) {
    val currentFontSize by LyricDisplayPreferences.fontSize
    val currentBold by LyricDisplayPreferences.boldCurrentLine
    val currentDim by LyricDisplayPreferences.dimLevel
    val currentAlign by LyricDisplayPreferences.alignment

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("歌词显示设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Font size
                Column {
                    Text(
                        text = "字体大小",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "small" to "小",
                            "default" to "默认",
                            "large" to "大",
                            "xlarge" to "超大"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = currentFontSize == value,
                                onClick = { LyricDisplayPreferences.setFontSize(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Bold toggle
                Column {
                    Text(
                        text = "当前行加粗",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("加粗显示", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = currentBold,
                            onCheckedChange = { LyricDisplayPreferences.setBoldCurrentLine(it) }
                        )
                    }
                }

                // Dim level
                Column {
                    Text(
                        text = "非当前行透明度",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("low" to "低", "medium" to "中", "high" to "高").forEach { (value, label) ->
                            FilterChip(
                                selected = currentDim == value,
                                onClick = { LyricDisplayPreferences.setDimLevel(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Alignment
                Column {
                    Text(
                        text = "对齐方式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("center" to "居中", "start" to "左对齐").forEach { (value, label) ->
                            FilterChip(
                                selected = currentAlign == value,
                                onClick = { LyricDisplayPreferences.setAlignment(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
