package com.example.spotifylyricsproxy.ui.playback

import android.graphics.Bitmap
import android.icu.text.Transliterator
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.spotifylyricsproxy.ui.theme.GlassSurface
import com.example.spotifylyricsproxy.util.MeteredState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val cnTransliterator: Transliterator by lazy {
    Transliterator.getInstance("Simplified-Traditional")
}
private val cnToSimplified: Transliterator by lazy {
    Transliterator.getInstance("Traditional-Simplified")
}
private val cnLock = Any()

/**
 * Convert Chinese text between simplified and traditional forms based on [targetForm].
 * - "traditional": converts simplified→traditional
 * - "simplified": converts traditional→simplified
 * - anything else: no-op
 */
internal fun convertChineseForm(text: String, targetForm: String): String {
    synchronized(cnLock) {
        return try {
            when (targetForm) {
                "traditional" -> cnTransliterator.transliterate(text)
                "simplified" -> cnToSimplified.transliterate(text)
                else -> text  // "original" — leave as-is from the source
            }
        } catch (_: Exception) { text }
    }
}

fun Int.dpToPx() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

/**
 * Full-screen ambient layer behind the art stage.
 * This is the first layer from the HTML prototype: a scaled, blurred, darkened
 * copy of the cover keeps the lower page in the album's colour world rather than black.
 */
@Composable
internal fun AmbientAlbumBackdrop(
    albumArt: Bitmap?,
    palette: AlbumPalette,
    modifier: Modifier = Modifier
) {
    // ONE continuous blur layer for the entire screen: strongest visually at the
    // bottom because the palette veil grows denser downward; it fades into nothing
    // by the lower ~10% of the clear cover. No second reflection/bleed layer.
    Box(modifier = modifier.fillMaxSize().background(palette.mid)) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(1.14f).blur(28.dp),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                alpha = 0.72f
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.30f to palette.mid.copy(alpha = 0.06f),
                        0.42f to palette.mid.copy(alpha = 0.18f),
                        0.60f to palette.deep.copy(alpha = 0.42f),
                        0.80f to palette.deep.copy(alpha = 0.68f),
                        1.00f to palette.deep.copy(alpha = 0.86f)
                    )
                )
            )
        )
    }
}


/**
 * Clear immersive album-art stage. The artwork dissolves into the extracted palette.
 */
@Composable
internal fun ImmersiveAlbumBackground(
    albumArt: Bitmap?,
    palette: AlbumPalette,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val stageHeight = configuration.screenHeightDp.dp * 0.36f

    // Mirrors the prototype's art-stage: clear 36% cover, then a separately
    // controlled lower bleed into the sampled album palette.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stageHeight)
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // Preserve the complete album artwork. Remaining stage space keeps
                // the sampled album backdrop instead of cutting artwork edges.
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High
            )

        // Keep the clear cover intact. The transition below is created by the
        // ambient backdrop plus palette gradient, not a visible blurred reflection.
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(palette.deep),
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
                fontSize = 23.sp,
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
            GlassSurface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                glassAlpha = 0.12f,
                borderAlpha = 0.18f
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    enabled = isConnected,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.playback_cd_previous),
                        modifier = Modifier.size(25.dp),
                        tint = Color.White.copy(alpha = if (isConnected) 0.9f else 0.3f)
                    )
                }
            }

            GlassSurface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                glassAlpha = 0.16f,
                borderAlpha = 0.24f
            ) {
                IconButton(
                    onClick = onPlayPause,
                    enabled = isConnected,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isPlaying) {
                        PauseGlyph(
                            color = Color.White.copy(alpha = if (isConnected) 0.95f else 0.3f),
                            size = 23.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.playback_cd_play),
                            modifier = Modifier.size(28.dp),
                            tint = Color.White.copy(alpha = if (isConnected) 0.95f else 0.3f)
                        )
                    }
                }
            }

            GlassSurface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                glassAlpha = 0.12f,
                borderAlpha = 0.18f
            ) {
                IconButton(
                    onClick = onSkipNext,
                    enabled = isConnected,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.playback_cd_next),
                        modifier = Modifier.size(25.dp),
                        tint = Color.White.copy(alpha = if (isConnected) 0.9f else 0.3f)
                    )
                }
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
    isPlaying: Boolean,
    positionMs: Long = 0L,
    config: com.example.spotifylyricsproxy.ui.playback.LyricDisplayConfig,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = remember(allLines) { LazyListState() }
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    itemsIndexed(allLines, key = { index, line -> "${line.startMs}_$index" }) { index, line ->
                        val isCurrent = index == currentIndex
                        val lineEndMs = allLines.getOrNull(index + 1)?.startMs ?: (line.startMs + 4000L)
                        LyricLine(
                            text = line.text,
                            isCurrent = isCurrent,
                            shouldAnimate = isCurrent && isPlaying,
                            animationKey = if (isCurrent) line.startMs else null,
                            lineStartMs = line.startMs,
                            lineEndMs = lineEndMs,
                            positionMs = positionMs,
                            config = config,
                            onClick = { onSeek(line.startMs) }
                        )

                        // Translation belongs to the same lyric block and gets breathing room.
                        if (isCurrent && isTranslationEnabled && !translatedLine.isNullOrBlank()) {
                            Text(
                                text = translatedLine,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                textAlign = config.textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 8.dp)
                            )
                        }
                    }
                }
            }
            is LyricStatus.MobileDataRestricted -> {
                Text(
                    text = stringResource(R.string.mobile_data_restricted),
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            is LyricStatus.LowConfidence -> {
                // Show lyrics even for low-confidence matches, with a status banner
                if (allLines.isNotEmpty()) {
                    Column {
                        Text(
                            text = stringResource(R.string.playback_lyrics_low_confidence),
                            fontSize = 13.sp,
                            color = Color(0xFFFF9500),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            itemsIndexed(allLines, key = { index, line -> "${line.startMs}_$index" }) { index, line ->
                                val isCurrent = index == currentIndex
                                val lineEndMs = allLines.getOrNull(index + 1)?.startMs ?: (line.startMs + 4000L)
                                LyricLine(
                                    text = line.text,
                                    isCurrent = isCurrent,
                                    shouldAnimate = isCurrent && isPlaying,
                                    animationKey = if (isCurrent) line.startMs else null,
                                    lineStartMs = line.startMs,
                                    lineEndMs = lineEndMs,
                                    positionMs = positionMs,
                                    config = config,
                                    onClick = { onSeek(line.startMs) }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.playback_lyrics_not_found),
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            is LyricStatus.PlainOnly, is LyricStatus.NotFound, is LyricStatus.ParseError -> {
                Text(
                    text = stringResource(R.string.playback_lyrics_not_found),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            is LyricStatus.Error -> {}
        }
    }
}

@Composable
internal fun LyricLine(
    text: String,
    isCurrent: Boolean,
    shouldAnimate: Boolean,
    animationKey: Long?,
    lineStartMs: Long = 0L,
    lineEndMs: Long = 0L,
    positionMs: Long = 0L,
    config: com.example.spotifylyricsproxy.ui.playback.LyricDisplayConfig,
    onClick: () -> Unit
) {
    // Convert chinese form (simplified/traditional) based on setting
    val displayText = convertChineseForm(text, config.chineseForm)
    // Current line is one coherent phrase: a restrained single pop, never per-character.
    if (isCurrent && shouldAnimate && animationKey != null) {
        WholeLinePopLyric(
            text = displayText,
            animationKey = animationKey,
            fontSize = config.currentLineSp,
            lineHeight = config.currentLineSp * 1.38f,
            fontWeight = config.currentLineWeight,
            textAlign = config.textAlign,
            onClick = onClick
        )
    } else if (isCurrent) {
        Text(
            text = displayText,
            fontSize = config.currentLineSp,
            lineHeight = config.currentLineSp * 1.38f,
            fontWeight = config.currentLineWeight,
            color = Color.White,
            textAlign = config.textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 3.dp)
        )
    } else {
        Text(
            text = displayText,
            fontSize = config.otherLineSp,
            lineHeight = config.otherLineSp * 1.38f,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = config.pastLineAlpha),
            textAlign = config.textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (config.blurEnabled) Modifier.blur(2.dp) else Modifier)
                .clickable(onClick = onClick)
                .padding(vertical = 3.dp)
        )
    }
}

@Composable
internal fun WholeLinePopLyric(
    text: String,
    animationKey: Long,
    fontSize: androidx.compose.ui.unit.TextUnit = 31.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 42.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center,
    onClick: () -> Unit
) {
    val alpha = remember(animationKey) { Animatable(0f) }
    val scale = remember(animationKey) { Animatable(0.96f) }
    val lift = remember(animationKey) { Animatable(6f) }
    LaunchedEffect(animationKey) {
        launch { alpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
        launch { lift.animateTo(-1.5f, tween(220, easing = FastOutSlowInEasing)) }
        scale.animateTo(1.018f, tween(220, easing = FastOutSlowInEasing))
        launch { scale.animateTo(1f, tween(120, easing = FastOutSlowInEasing)) }
        launch { lift.animateTo(0f, tween(120, easing = FastOutSlowInEasing)) }
    }
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        color = Color.White,
        textAlign = textAlign,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
            .alpha(alpha.value)
            .scale(scale.value)
            .offset(y = lift.value.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CharacterPopLyric(
    text: String,
    animationKey: Long,
    lineStartMs: Long,
    lineEndMs: Long,
    positionMs: Long,
    onClick: () -> Unit
) {
    // How far we are through THIS line, used to reveal characters progressively
    // in sync with playback (one character at a time, not all at once).
    val span = (lineEndMs - lineStartMs).coerceAtLeast(1L)
    val elapsedInLine = (positionMs - lineStartMs).coerceIn(0L, span)
    val charCount = text.length.coerceAtLeast(1)
    // ceil ensures the final glyph is revealed before the playback clock crosses
    // the next line boundary; floor permanently hid it in normal clock jumps.
    val revealedCount = kotlin.math.ceil(
        (elapsedInLine.toDouble() / span.toDouble()) * charCount
    ).toInt().coerceIn(0, charCount)

    // Preserve whitespace as a real layout cell while each visible character enters separately.
    val characters = remember(text) { text.map { it.toString() } }
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        characters.forEachIndexed { index, character ->
            CharacterPop(
                character = character,
                animationKey = animationKey,
                // Characters already passed in the song are shown immediately;
                // the next one pops in, the rest stay hidden until their turn.
                revealed = index < revealedCount,
                isNext = index == revealedCount
            )
        }
    }
}

@Composable
private fun CharacterPop(
    character: String,
    animationKey: Long,
    revealed: Boolean,
    isNext: Boolean
) {
    // Playback ticks recompose this line frequently. Keep the reveal lightweight:
    // no per-character coroutine, scale or bounce; a single alpha state avoids jank.
    Text(
        text = character,
        fontSize = 31.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = if (revealed) 1f else 0f),
        modifier = Modifier
            .alpha(if (revealed) 1f else 0f)
    )
}

/**
 * Reference-web scrubber, implemented as one permanent Canvas node.
 * That is deliberate: expanding must never replace the pointer target mid-drag.
 */
@Composable
fun ImmersiveSeekControl(
    estimatedPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0) return

    val playbackProgress = (estimatedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(playbackProgress) }
    var dragStartProgress by remember { mutableStateOf(0f) }
    var dragDistancePx by remember { mutableStateOf(0f) }
    var acceptedGesture by remember { mutableStateOf(false) }
    var collapseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val expansion by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scrubberExpand"
    )

    fun commitSeek() {
        onSeek((scrubProgress.coerceIn(0f, 1f) * durationMs).toLong())
    }
    fun collapseAfterRelease() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(650)
            isScrubbing = false
        }
    }
    fun collapseAfterTap() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            // An expand-only tap should be inspectable, but must not leave the capsule stuck.
            delay(1200)
            isScrubbing = false
        }
    }
    LaunchedEffect(playbackProgress) {
        if (!isScrubbing) scrubProgress = playbackProgress
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // One stable pointer node for BOTH idle and expanded states. The visual only is animated.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            val idleWidth = 92.dp.toPx()
                            val centerStart = (size.width - idleWidth) / 2f
                            // In idle mode, only the visible 92dp line is touchable.
                            // Once open, any point across the expanded capsule can be dragged.
                            acceptedGesture = isScrubbing || start.x in centerStart..(centerStart + idleWidth)
                            if (acceptedGesture) {
                                collapseJob?.cancel()
                                isScrubbing = true
                                dragStartProgress = scrubProgress
                                dragDistancePx = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!acceptedGesture) return@detectHorizontalDragGestures
                            change.consume()
                            dragDistancePx += dragAmount
                            scrubProgress = (dragStartProgress + dragDistancePx / size.width)
                                .coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (acceptedGesture) {
                                commitSeek()
                                collapseAfterRelease()
                            }
                            acceptedGesture = false
                        },
                        onDragCancel = {
                            // Do not collapse immediately: a framework cancellation must not flash it away.
                            if (acceptedGesture) collapseAfterRelease()
                            acceptedGesture = false
                        }
                    )
                }
                .pointerInput(durationMs) {
                    detectTapGestures { tap ->
                        val idleWidth = 92.dp.toPx()
                        val centerStart = (size.width - idleWidth) / 2f
                        if (isScrubbing || tap.x in centerStart..(centerStart + idleWidth)) {
                            // Tap expands ONLY. Preserve current playhead; no seek is submitted.
                            collapseJob?.cancel()
                            isScrubbing = true
                            collapseAfterTap()
                        }
                    }
                }
        ) {
            val idleWidth = 92.dp.toPx()
            val fullWidth = size.width
            val shellWidth = idleWidth + (fullWidth - idleWidth) * expansion
            val shellLeft = (fullWidth - shellWidth) / 2f
            val shellHeight = 28.dp.toPx() + 16.dp.toPx() * expansion
            val shellTop = (size.height - shellHeight) / 2f
            val radius = shellHeight / 2f

            // Web prototype: bare line at rest; glass appears progressively with expansion.
            if (expansion > 0.01f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.17f * expansion),
                    topLeft = androidx.compose.ui.geometry.Offset(shellLeft, shellTop),
                    size = androidx.compose.ui.geometry.Size(shellWidth, shellHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                )
            }

            val sideSpace = 96.dp.toPx() * expansion
            val trackLeft = shellLeft + sideSpace / 2f
            val trackWidth = (shellWidth - sideSpace).coerceAtLeast(1f)
            val trackHeight = 3.dp.toPx() + 1.dp.toPx() * expansion
            val trackTop = (size.height - trackHeight) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, trackTop),
                size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.88f),
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, trackTop),
                size = androidx.compose.ui.geometry.Size(trackWidth * scrubProgress, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
            )
        }

        // Labels are only a visual overlay; the permanent Canvas above owns every gesture.
        if (expansion > 0.01f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .alpha(expansion),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs((scrubProgress * durationMs).toLong()),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    text = formatMs(durationMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f)
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
