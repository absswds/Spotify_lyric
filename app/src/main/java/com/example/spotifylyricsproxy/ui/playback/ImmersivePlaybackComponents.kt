package com.example.spotifylyricsproxy.ui.playback

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import com.example.spotifylyricsproxy.ui.theme.SeekGlassPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Immersive album background that fills the top portion of the screen.
 * The artwork dissolves into the extracted dominant color, not black.
 */
@Composable
internal fun ImmersiveAlbumBackground(
    albumArt: Bitmap?,
    palette: AlbumPalette,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val artworkHeight = screenHeight * 0.32f // 32% of screen height

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(artworkHeight)
    ) {
        // Artwork layer
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.deep),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.playback_cd_album_art_placeholder),
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        // Bottom fade into palette color (not black)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight * 0.5f)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.deep.copy(alpha = 0.8f),
                            palette.deep
                        )
                    )
                )
        )
    }
}

/**
 * Track header with title/artist on left and compact transport controls on right.
 * No favorite/heart button.
 */
@Composable
fun ImmersiveTrackHeader(
    trackInfo: SpotifyTrackInfo,
    connectionState: SpotifyConnectionState,
    isPlaying: Boolean,
    isConnected: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title and artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = trackInfo.title.ifEmpty { stringResource(R.string.playback_title_waiting) },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (trackInfo.artist.isNotEmpty()) {
                    listOf(trackInfo.artist, trackInfo.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                } else {
                    if (connectionState is SpotifyConnectionState.Connected)
                        stringResource(R.string.playback_subtitle_waiting)
                    else
                        stringResource(R.string.playback_subtitle_connect_prompt)
                },
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Compact transport controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSkipPrevious,
                enabled = isConnected,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.playback_cd_previous),
                    modifier = Modifier.size(22.dp),
                    tint = Color.White.copy(alpha = if (isConnected) 0.85f else 0.3f)
                )
            }

            IconButton(
                onClick = onPlayPause,
                enabled = isConnected,
                modifier = Modifier.size(40.dp)
            ) {
                if (isPlaying) {
                    PauseGlyph(
                        color = Color.White.copy(alpha = if (isConnected) 0.9f else 0.3f),
                        size = 20.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.playback_cd_play),
                        modifier = Modifier.size(24.dp),
                        tint = Color.White.copy(alpha = if (isConnected) 0.9f else 0.3f)
                    )
                }
            }

            IconButton(
                onClick = onSkipNext,
                enabled = isConnected,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.playback_cd_next),
                    modifier = Modifier.size(22.dp),
                    tint = Color.White.copy(alpha = if (isConnected) 0.85f else 0.3f)
                )
            }
        }
    }
}

@Composable
private fun PauseGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val barWidth = size.toPx() * 0.28f
        val barHeight = size.toPx() * 0.7f
        val gap = size.toPx() * 0.18f
        val startX = (size.toPx() - barWidth * 2 - gap) / 2
        val startY = (size.toPx() - barHeight) / 2

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(startX, startY),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f)
        )
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(startX + barWidth + gap, startY),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.3f)
        )
    }
}

/**
 * Immersive lyrics block with fixed anchor and uniform blur for non-current lines.
 * Current line: clear white, bold, larger.
 * All other lines: SAME constant muted alpha, SAME font size, SAME blur radius.
 */
@Composable
fun ImmersiveLyricsBlock(
    currentLine: LrcLine?,
    allLines: List<LrcLine>,
    status: LyricStatus,
    translatedLine: String?,
    isTranslationEnabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val currentIndex = currentLine?.let { allLines.indexOf(it) } ?: -1

    // Auto-scroll to keep current line anchored
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            // Scroll to keep current line at position ~3 from top (fixed anchor)
            val targetIndex = (currentIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            is LyricStatus.Idle, is LyricStatus.Searching -> {
                Text(
                    text = stringResource(R.string.playback_lyrics_searching),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            is LyricStatus.Synced -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(allLines) { index, line ->
                        val isCurrent = index == currentIndex
                        LyricLine(
                            text = line.text,
                            isCurrent = isCurrent,
                            onClick = { onSeek(line.startMs) }
                        )

                        // Show translation under current line
                        if (isCurrent && isTranslationEnabled && !translatedLine.isNullOrBlank()) {
                            Text(
                                text = translatedLine,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.playback_lyrics_not_found),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun LyricLine(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    // Uniform style for all non-current lines
    val fontSize = if (isCurrent) 28.sp else 20.sp
    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
    val color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.45f)
    val blurRadius = if (isCurrent) 0.dp else 1.5.dp

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .blur(blurRadius)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

/**
 * Immersive seek control:
 * - Idle: short centered line (92dp), no labels
 * - Scrubbing: expands to full-width glass pill with elapsed/duration labels
 */
@Composable
fun ImmersiveSeekControl(
    estimatedPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0) return

    val progress = (estimatedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(progress) }
    var collapseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Update scrub progress from playback
    LaunchedEffect(progress) {
        if (!isScrubbing) {
            scrubProgress = progress
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isScrubbing) {
            // Expanded glass pill with labels
            SeekGlassPill(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Elapsed time
                    Text(
                        text = formatMs((scrubProgress * durationMs).toLong()),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    // Seek bar
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    val x = change.position.x
                                    val width = size.width.toFloat()
                                    scrubProgress = (x / width).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val width = size.width.toFloat()
                                    scrubProgress = (offset.x / width).coerceIn(0f, 1f)
                                    onSeek((scrubProgress * durationMs).toLong().coerceIn(0, durationMs))
                                    isScrubbing = false
                                }
                            }
                    ) {
                        // Track background
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.2f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                        // Progress fill
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            size = androidx.compose.ui.geometry.Size(
                                width = size.width * scrubProgress,
                                height = size.height
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }

                    // Duration
                    Text(
                        text = formatMs(durationMs),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Idle: short centered line
            Canvas(
                modifier = Modifier
                    .width(92.dp)
                    .height(3.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { isScrubbing = true },
                            onDragEnd = {
                                onSeek((scrubProgress * durationMs).toLong().coerceIn(0, durationMs))
                                // Delay collapse
                                collapseJob?.cancel()
                                collapseJob = coroutineScope.launch {
                                    delay(650)
                                    isScrubbing = false
                                }
                            },
                            onDragCancel = { isScrubbing = false },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x
                                val width = size.width.toFloat()
                                scrubProgress = (x / width).coerceIn(0f, 1f)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            isScrubbing = true
                            // Delay collapse
                            collapseJob?.cancel()
                            collapseJob = coroutineScope.launch {
                                delay(650)
                                isScrubbing = false
                            }
                        }
                    }
            ) {
                // Track background
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
                )
                // Progress fill
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width * progress,
                        height = size.height
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
