package com.example.spotifylyricsproxy.ui.playback

import android.graphics.Bitmap
import android.icu.text.Transliterator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.key
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Rect
import com.example.spotifylyricsproxy.core.model.LyricWord
import kotlin.math.abs
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.cos
import kotlin.math.sin

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

// ---------------------------------------------------------------------------
// Background: mesh gradient sampled from the cover (no blurred image)
// ---------------------------------------------------------------------------

private const val MESH_GRID = 5
private const val MESH_SRC = 24
private const val MESH_W = 64
private const val MESH_H = 40

private class MeshPoint(val u: Float, val v: Float, val phase: Float, val speed: Float, val radius: Float)

/** Cover shrunk to MESH_SRC², read once per bitmap. Hardware bitmaps are copied first. */
private fun meshSourcePixels(bitmap: Bitmap): IntArray? = try {
    val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val small = Bitmap.createScaledBitmap(soft, MESH_SRC, MESH_SRC, true)
    IntArray(MESH_SRC * MESH_SRC).also { small.getPixels(it, 0, MESH_SRC, 0, 0, MESH_SRC, MESH_SRC) }
} catch (_: Exception) {
    null
}

/** Bilinear read from the shrunk cover, then boosted saturation and darkened so white lyrics stay legible. */
private fun meshSample(src: IntArray, u: Float, v: Float, out: FloatArray) {
    val x = (u * (MESH_SRC - 1)).coerceIn(0f, MESH_SRC - 1.001f)
    val y = (v * (MESH_SRC - 1)).coerceIn(0f, MESH_SRC - 1.001f)
    val x0 = x.toInt(); val y0 = y.toInt(); val fx = x - x0; val fy = y - y0
    val a = src[y0 * MESH_SRC + x0]; val b = src[y0 * MESH_SRC + x0 + 1]
    val c = src[(y0 + 1) * MESH_SRC + x0]; val d = src[(y0 + 1) * MESH_SRC + x0 + 1]
    for (ch in 0..2) {
        val shift = 16 - ch * 8
        val va = (a shr shift and 0xFF).toFloat(); val vb = (b shr shift and 0xFF).toFloat()
        val vc = (c shr shift and 0xFF).toFloat(); val vd = (d shr shift and 0xFF).toFloat()
        out[ch] = (va * (1 - fx) + vb * fx) * (1 - fy) + (vc * (1 - fx) + vd * fx) * fy
    }
    val mean = (out[0] + out[1] + out[2]) / 3f
    for (ch in 0..2) out[ch] = ((mean + (out[ch] - mean) * 1.35f) * 0.72f).coerceIn(0f, 255f)
}

private fun renderMesh(src: IntArray, points: List<MeshPoint>, time: Float, grid: Array<FloatArray>, pixels: IntArray) {
    points.forEachIndexed { i, p ->
        meshSample(
            src,
            0.5f + (p.u - 0.5f) * 0.8f + sin(time * p.speed + p.phase) * p.radius,
            0.5f + (p.v - 0.5f) * 0.8f + cos(time * p.speed * 0.9f + p.phase * 1.3f) * p.radius,
            grid[i]
        )
    }
    fun smooth(t: Float) = t * t * (3 - 2 * t)
    for (y in 0 until MESH_H) {
        val gy = y.toFloat() / (MESH_H - 1) * (MESH_GRID - 1)
        val j = gy.toInt().coerceAtMost(MESH_GRID - 2); val ty = smooth(gy - j)
        for (x in 0 until MESH_W) {
            val gx = x.toFloat() / (MESH_W - 1) * (MESH_GRID - 1)
            val i = gx.toInt().coerceAtMost(MESH_GRID - 2); val tx = smooth(gx - i)
            val a = grid[j * MESH_GRID + i]; val b = grid[j * MESH_GRID + i + 1]
            val c = grid[(j + 1) * MESH_GRID + i]; val d = grid[(j + 1) * MESH_GRID + i + 1]
            var argb = 0xFF shl 24
            for (ch in 0..2) {
                val v = (a[ch] * (1 - tx) + b[ch] * tx) * (1 - ty) + (c[ch] * (1 - tx) + d[ch] * tx) * ty
                argb = argb or (v.toInt().coerceIn(0, 255) shl (16 - ch * 8))
            }
            pixels[y * MESH_W + x] = argb
        }
    }
}

/**
 * Apple-Music-style flowing background: a 5×5 grid of colour points wanders across the cover,
 * each picking up the colour beneath it; the grid is interpolated smoothly and scaled up.
 * Runs at ~30 fps while [animate] is true and freezes in place otherwise.
 */
@Composable
internal fun MeshGradientBackground(
    albumArt: Bitmap?,
    palette: AlbumPalette,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val source = remember(albumArt) { albumArt?.let(::meshSourcePixels) }
    val target = remember { Bitmap.createBitmap(MESH_W, MESH_H, Bitmap.Config.ARGB_8888) }
    val image = remember(target) { target.asImageBitmap() }
    val pixels = remember { IntArray(MESH_W * MESH_H) }
    val grid = remember { Array(MESH_GRID * MESH_GRID) { FloatArray(3) } }
    val points = remember {
        val rnd = java.util.Random(7)
        List(MESH_GRID * MESH_GRID) { k ->
            MeshPoint(
                u = (k % MESH_GRID) / (MESH_GRID - 1f),
                v = (k / MESH_GRID) / (MESH_GRID - 1f),
                phase = rnd.nextFloat() * 6.28f,
                speed = 0.06f + rnd.nextFloat() * 0.08f,
                radius = 0.18f + rnd.nextFloat() * 0.14f
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, source) {
        if (!animate || source == null) return@LaunchedEffect
        var last = 0L
        var pending = 0f
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) pending += (now - last) / 1_000_000_000f
                last = now
            }
            if (pending >= 1f / 30f) {
                time += pending
                pending = 0f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val src = source
        if (src == null) {
            drawRect(Brush.verticalGradient(listOf(palette.mid, palette.deep)))
            return@Canvas
        }
        renderMesh(src, points, time, grid, pixels)
        target.setPixels(pixels, 0, MESH_W, 0, 0, MESH_W, MESH_H)
        drawImage(
            image = image,
            srcSize = IntSize(MESH_W, MESH_H),
            dstSize = IntSize(size.width.toInt() + 1, size.height.toInt() + 1),
            filterQuality = FilterQuality.High
        )
    }
}

/**
 * Full-screen ambient layer behind the portrait art stage: the flowing mesh plus a
 * veil that deepens toward the bottom so lyrics always sit on a calm field.
 */
@Composable
internal fun AmbientAlbumBackdrop(
    albumArt: Bitmap?,
    palette: AlbumPalette,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        MeshGradientBackground(albumArt = albumArt, palette = palette, animate = animate)
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.40f to Color.Black.copy(alpha = 0.06f),
                        0.75f to Color.Black.copy(alpha = 0.22f),
                        1.00f to Color.Black.copy(alpha = 0.36f)
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
                // Preserve the complete album artwork; the mesh fills the remaining stage.
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High
            )
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

// ---------------------------------------------------------------------------
// Buttons: bare icons, no circle; press feedback is scale + fade
// ---------------------------------------------------------------------------

@Composable
internal fun BareIconButton(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "bareIconScale"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = when {
                    !enabled -> 0.3f
                    pressed -> 0.7f
                    else -> 1f
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun PlayPauseGlyph(isPlaying: Boolean, size: Dp, color: Color = Color.White) {
    if (isPlaying) {
        PauseGlyph(color = color, size = size)
    } else {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.playback_cd_play),
            modifier = Modifier.size(size * 1.2f),
            tint = color
        )
    }
}

/**
 * Track header with title/artist on left and compact transport controls on right.
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackInfo.title.ifEmpty { stringResource(R.string.playback_title_waiting) },
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
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
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BareIconButton(onClick = onSkipPrevious, size = 44.dp, enabled = isConnected) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.playback_cd_previous),
                    modifier = Modifier.size(30.dp),
                    tint = Color.White
                )
            }
            BareIconButton(onClick = onPlayPause, size = 48.dp, enabled = isConnected) {
                PlayPauseGlyph(isPlaying = isPlaying, size = 28.dp)
            }
            BareIconButton(onClick = onSkipNext, size = 44.dp, enabled = isConnected) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.playback_cd_next),
                    modifier = Modifier.size(30.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
internal fun PauseGlyph(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val barWidth = size.toPx() * 0.28f
        val barHeight = size.toPx() * 0.72f
        val gap = size.toPx() * 0.2f
        val startX = (size.toPx() - barWidth * 2 - gap) / 2
        val startY = (size.toPx() - barHeight) / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(startX, startY),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth * 0.3f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(startX + barWidth + gap, startY),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth * 0.3f)
        )
    }
}

// ---------------------------------------------------------------------------
// Lyrics
// ---------------------------------------------------------------------------

private const val USER_SCROLL_HOLD_MS = 3000L

/**
 * Position for the sweep, sampled from the continuous playback clock once per frame.
 * Re-anchoring on the 300 ms ticks made the sweep hitch backwards a few frames every
 * tick; reading the clock directly keeps it strictly linear.
 */
@Composable
private fun rememberSmoothPosition(isPlaying: Boolean, clock: () -> Long): () -> Long {
    val live = rememberUpdatedState(clock)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) frame = withFrameMillis { it }
    }
    return remember {
        {
            frame // read so every frame invalidates the draw that calls this
            live.value()
        }
    }
}

/**
 * Lyrics list with Apple-Music-style behaviour:
 * - the current line springs up and a soft-edged glow sweeps across it as it is sung
 *   (timed over the line's duration by rendered width), and the list follows it
 *   with a soft spring scroll;
 * - other lines fade and blur more the further they are from the current one;
 * - dragging the list pauses following; it resumes after [USER_SCROLL_HOLD_MS]
 *   or immediately via the "back to current line" pill.
 */
@Composable
fun ImmersiveLyricsBlock(
    currentLine: LrcLine?,
    allLines: List<LrcLine>,
    status: LyricStatus,
    translatedLine: String?,
    isTranslationEnabled: Boolean,
    isPlaying: Boolean,
    /** Live lyric-time clock, read once per frame by the sweep (never in composition). */
    positionMs: () -> Long,
    config: LyricDisplayConfig,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAllowMobileData: () -> Unit = {},
    onDenyMobileData: () -> Unit = {},
    onSearchManually: (() -> Unit)? = null,
    textScale: Float = 1f,
    anchorFraction: Float = 0.28f,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp)
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            is LyricStatus.Idle, is LyricStatus.Searching -> {
                LyricsStatusMessage(stringResource(R.string.playback_lyrics_searching), textScale)
            }
            is LyricStatus.Synced, is LyricStatus.LowConfidence -> {
                if (allLines.isEmpty()) {
                    LyricsStatusMessage(stringResource(R.string.playback_lyrics_not_found), textScale, onSearchManually)
                } else {
                    Column {
                        if (status is LyricStatus.LowConfidence) {
                            Text(
                                text = stringResource(R.string.playback_lyrics_low_confidence),
                                fontSize = 13.sp * textScale,
                                color = Color(0xFFFFB340),
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .then(if (onSearchManually != null) Modifier.clickable(onClick = onSearchManually) else Modifier)
                            )
                        }
                        SyncedLyricsList(
                            lines = allLines,
                            currentLine = currentLine,
                            translatedLine = translatedLine,
                            isTranslationEnabled = isTranslationEnabled,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            config = config,
                            onSeek = onSeek,
                            textScale = textScale,
                            anchorFraction = anchorFraction,
                            contentPadding = contentPadding
                        )
                    }
                }
            }
            is LyricStatus.MobileDataRestricted -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.mobile_data_restricted),
                        fontSize = 16.sp * textScale,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onAllowMobileData) { Text(stringResource(R.string.mobile_data_allow), color = Color.White) }
                        TextButton(onClick = onDenyMobileData) { Text(stringResource(R.string.mobile_data_deny), color = Color.White.copy(alpha = 0.7f)) }
                    }
                }
            }
            is LyricStatus.PlainOnly, is LyricStatus.NotFound, is LyricStatus.ParseError -> {
                LyricsStatusMessage(stringResource(R.string.playback_lyrics_not_found), textScale, onSearchManually)
            }
            is LyricStatus.Error -> {
                LyricsStatusMessage(stringResource(R.string.playback_lyrics_load_error), textScale, onSearchManually)
            }
        }
    }
}

@Composable
private fun SyncedLyricsList(
    lines: List<LrcLine>,
    currentLine: LrcLine?,
    translatedLine: String?,
    isTranslationEnabled: Boolean,
    isPlaying: Boolean,
    positionMs: () -> Long,
    config: LyricDisplayConfig,
    onSeek: (Long) -> Unit,
    textScale: Float,
    anchorFraction: Float,
    contentPadding: PaddingValues
) {
    val listState = remember(lines) { LazyListState() }
    val scope = rememberCoroutineScope()
    val currentIndex = currentLine?.let { lines.indexOf(it) } ?: -1
    val smoothPosition = rememberSmoothPosition(isPlaying, positionMs)
    var lastUserScrollAt by remember(lines) { mutableLongStateOf(0L) }
    var browsing by remember(lines) { mutableStateOf(false) }

    // Only real finger drags count as "user browsing"; our own animated scrolls do not.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                browsing = true
                lastUserScrollAt = android.os.SystemClock.uptimeMillis()
            } else if (interaction is DragInteraction.Stop || interaction is DragInteraction.Cancel) {
                lastUserScrollAt = android.os.SystemClock.uptimeMillis()
            }
        }
    }

    suspend fun followCurrent(immediate: Boolean) {
        if (currentIndex < 0) return
        val anchorPx = (listState.layoutInfo.viewportSize.height * anchorFraction).toInt()
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        if (visible != null && !immediate) {
            listState.animateScrollBy(
                value = (visible.offset - anchorPx).toFloat(),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 110f)
            )
        } else {
            listState.scrollToItem(currentIndex, -anchorPx)
        }
    }

    LaunchedEffect(currentIndex, lastUserScrollAt, browsing) {
        if (browsing) {
            val wait = USER_SCROLL_HOLD_MS - (android.os.SystemClock.uptimeMillis() - lastUserScrollAt)
            if (wait > 0) delay(wait)
            if (listState.isScrollInProgress) return@LaunchedEffect
            browsing = false
        }
        followCurrent(immediate = listState.layoutInfo.visibleItemsInfo.isEmpty())
    }

    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp * textScale)
        ) {
            itemsIndexed(lines, key = { index, line -> "${line.startMs}_$index" }) { index, line ->
                val isCurrent = index == currentIndex
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (index == 0 && line.startMs >= INTRO_DOTS_MIN_MS) {
                        AnimatedVisibility(
                            visible = currentIndex < 0,
                            enter = fadeIn(tween(300)) + expandVertically(),
                            exit = fadeOut(tween(250)) + shrinkVertically(tween(450))
                        ) {
                            IntroCountdownDots(
                                startMs = 0L,
                                endMs = line.startMs,
                                positionMs = smoothPosition,
                                textScale = textScale,
                                alignEnd = config.textAlign == TextAlign.End || config.textAlign == TextAlign.Right,
                                center = config.textAlign == TextAlign.Center
                            )
                        }
                    }
                    // Duet: the second singer's lines sit on the opposite side.
                    val lineConfig = if (line.isSecondaryVoice && config.textAlign != TextAlign.Center) {
                        config.copy(textAlign = if (config.textAlign == TextAlign.End || config.textAlign == TextAlign.Right) TextAlign.Start else TextAlign.End)
                    } else config
                    // Word-by-word only with real word timing; line-only lyrics light up whole
                    // (an estimate from text length never lines up with the singing).
                    val timedWords = if (LyricDisplayPreferences.wordByWord.value) line.words else emptyList()
                    val wordRanges = remember(timedWords) { wordCharRanges(timedWords) }
                    LyricLine(
                        text = if (timedWords.isEmpty()) line.text else timedWords.joinToString("") { it.text },
                        isCurrent = isCurrent,
                        distance = if (currentIndex < 0) 2 else abs(index - currentIndex),
                        isPast = currentIndex >= 0 && index < currentIndex,
                        browsing = browsing,
                        charReveal = { revealedChars(timedWords, wordRanges, smoothPosition()) },
                        wordRanges = wordRanges,
                        config = lineConfig,
                        textScale = textScale,
                        onClick = {
                            browsing = false
                            onSeek(line.startMs)
                        }
                    )
                    // Like Lyricify: every line carries its translation; the current one is
                    // brighter. Other lines only have the translation shipped with the lyrics.
                    val lineTranslation = if (isCurrent) translatedLine else line.translation
                    if (isTranslationEnabled && !lineTranslation.isNullOrBlank()) {
                        val tAlpha by animateFloatAsState(
                            if (isCurrent) 0.7f else 0.28f, tween(450), label = "translationAlpha"
                        )
                        Text(
                            text = convertChineseForm(lineTranslation, lineConfig.chineseForm),
                            fontSize = 16.sp * textScale,
                            lineHeight = 23.sp * textScale,
                            color = Color.White.copy(alpha = tAlpha),
                            textAlign = lineConfig.textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                    // Interlude: the same dots between two lines far apart.
                    val next = lines.getOrNull(index + 1)
                    val gapStart = if (line.text.isBlank()) line.startMs
                        else line.endMs ?: line.words.lastOrNull()?.endMs
                    if (isCurrent && next != null && gapStart != null &&
                        next.startMs - gapStart >= INTERLUDE_DOTS_MIN_MS
                    ) {
                        var inGap by remember { mutableStateOf(false) }
                        LaunchedEffect(gapStart) {
                            while (true) {
                                inGap = smoothPosition() >= gapStart + 300
                                delay(200)
                            }
                        }
                        AnimatedVisibility(
                            visible = inGap,
                            enter = fadeIn(tween(300)) + expandVertically(),
                            exit = fadeOut(tween(250)) + shrinkVertically(tween(450))
                        ) {
                            IntroCountdownDots(
                                startMs = gapStart,
                                endMs = next.startMs,
                                positionMs = smoothPosition,
                                textScale = textScale,
                                alignEnd = lineConfig.textAlign == TextAlign.End || lineConfig.textAlign == TextAlign.Right,
                                center = lineConfig.textAlign == TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = browsing && currentIndex >= 0,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = 0.6f)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.playback_cd_jump_to_current),
                fontSize = 13.sp * textScale,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable {
                        browsing = false
                        scope.launch { followCurrent(immediate = false) }
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun LyricsStatusMessage(text: String, textScale: Float, onSearchManually: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            fontSize = 18.sp * textScale,
            color = Color.White.copy(alpha = 0.6f)
        )
        if (onSearchManually != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.correction_research),
                fontSize = 14.sp * textScale,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(onClick = onSearchManually)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

private const val INTRO_DOTS_MIN_MS = 3_000L
private const val INTERLUDE_DOTS_MIN_MS = 6_000L

/**
 * Intro countdown after Lyricify / Apple Music: three dots that light up one by one
 * as the first line approaches, breathing gently, then swell and vanish just before
 * it starts. Position is read in the draw phase only.
 */
@Composable
private fun IntroCountdownDots(
    startMs: Long,
    endMs: Long,
    positionMs: () -> Long,
    textScale: Float,
    alignEnd: Boolean,
    center: Boolean
) {
    val dot = 10.dp * textScale
    val gap = 8.dp * textScale
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 14.dp * textScale)
            .height(dot * 1.6f)
    ) {
        val now = positionMs().coerceIn(startMs, endMs)
        val progress = ((now - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
        // Last 400 ms: swell then collapse.
        val outro = ((endMs - now) / 400f).coerceIn(0f, 1f)
        val pop = if (outro < 1f) (1f + 0.25f * kotlin.math.sin(outro * Math.PI.toFloat())) * outro else 1f
        val breathe = 1f + 0.1f * kotlin.math.sin(now / 1000f * 2f * Math.PI.toFloat() / 3.2f)
        val r = dot.toPx() / 2f * breathe * pop
        val step = dot.toPx() + gap.toPx()
        val groupWidth = step * 2 + dot.toPx()
        val startX = when {
            center -> (size.width - groupWidth) / 2
            alignEnd -> size.width - groupWidth
            else -> 0f
        } + dot.toPx() / 2
        for (i in 0 until 3) {
            // Each dot fills over its own third of the wait.
            val fill = ((progress * 3f) - i).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = (0.25f + 0.75f * fill) * outro.coerceAtLeast(0.001f)),
                radius = r,
                center = Offset(startX + step * i, size.height / 2)
            )
        }
    }
}

/**
 * One lyric line. Every line is laid out at the current-line size so nothing reflows;
 * non-current lines are shrunk with a graphics-layer scale, dimmed and blurred by distance,
 * and the change of current line animates as one smooth spring.
 */
@Composable
internal fun LyricLine(
    text: String,
    isCurrent: Boolean,
    distance: Int,
    isPast: Boolean,
    browsing: Boolean,
    /** How many characters are sung (fractional), read in the draw phase only. */
    charReveal: () -> Float,
    /** Character range of each word, so sung words can lift as a unit; empty = no word timing. */
    wordRanges: List<IntRange>,
    config: LyricDisplayConfig,
    textScale: Float,
    onClick: () -> Unit
) {
    val displayText = remember(text, config.chineseForm) { convertChineseForm(text, config.chineseForm) }
    val restScale = (config.otherLineSp.value / config.currentLineSp.value).coerceIn(0.6f, 1f)
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1f else restScale,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 140f),
        label = "lyricScale"
    )
    val baseAlpha = when {
        isCurrent -> 1f
        isPast -> config.pastLineAlpha
        else -> config.futureLineAlpha
    }
    val falloff = if (isCurrent) 1f else (1f - 0.12f * (distance - 1)).coerceAtLeast(0.45f)
    val alpha by animateFloatAsState(
        targetValue = if (browsing && !isCurrent) maxOf(baseAlpha, 0.5f) else baseAlpha * falloff,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "lyricAlpha"
    )
    val blurTarget = if (!config.blurEnabled || isCurrent || browsing) 0.dp else (0.4.dp * distance).coerceAtMost(1.5.dp)
    val blur by animateDpAsState(blurTarget, tween(450), label = "lyricBlur")
    val originX = when (config.textAlign) {
        TextAlign.Start, TextAlign.Left -> 0f
        TextAlign.End, TextAlign.Right -> 1f
        else -> 0.5f
    }
    val fontSize: TextUnit = config.currentLineSp * textScale
    val style = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.3f,
        fontWeight = config.currentLineWeight,
        textAlign = config.textAlign,
        letterSpacing = (-0.2).sp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // No clip here: the current line is scaled up and its lifted descenders
            // would be cut off. Without a clip the ripple would be square, so none.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            // Blur renders into a layer clipped to its bounds: apply it outside the
            // padding so descenders (g, y) that reach past the text box aren't cut.
            .then(if (blur > 0.dp) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp * textScale)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(originX, 0.5f)
            }
            // ModulateAlpha instead of .alpha(): no offscreen layer, so nothing is
            // clipped to the line's bounds (lowered descenders stay whole).
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
    ) {
        if (isCurrent) {
            // Chinese-form conversion keeps one char per char; if it ever doesn't, the word
            // offsets no longer apply and the line is simply shown lit.
            if (wordRanges.isNotEmpty() && displayText.length == text.length) {
                SweepText(displayText, style, charReveal, wordRanges)
            } else {
                Text(text = displayText, style = style.copy(color = Color.White), modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(text = displayText, style = style.copy(color = Color.White), modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Word-by-word highlight with depth, after Apple Music: every word sits slightly low and
 * dim until it is sung, then eases upward while a soft-edged glow sweeps across it. The
 * dim and lit copies lift together, so sung and unsung words read as two layers.
 * [charReveal] is read in the draw phase only; nothing recomposes per frame.
 */
@Composable
private fun SweepText(
    text: String,
    style: TextStyle,
    charReveal: () -> Float,
    wordRanges: List<IntRange>
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fontPx = with(LocalDensity.current) { style.fontSize.toPx() }
    val softEdge = fontPx * 0.9f
    val lift = fontPx * 0.10f
    // Clip box per character: its own width, and its own row's height so a wrapped
    // line never draws the neighbouring row's glyphs (the "ghost" under line one).
    // Only the outer edges get extra room for the lift.
    val boxes = remember(layout, lift, fontPx) {
        layout?.let { l ->
            // Rows split just below each baseline, so descenders (g, y, p) stay
            // with their own row even when the line height is tight.
            fun split(row: Int) = rowSplit(l, row, fontPx)
            List(text.length) { i ->
                // Spaces have no ink, and the one at a wrap point reports a box spanning most
                // of the row: drawing it at its (unsung) word's lift repeated the whole sung
                // part of the row a few pixels lower, the doubled text on wrapped lines.
                if (text[i].isWhitespace()) return@List Rect.Zero
                val row = l.getLineForOffset(i)
                val b = l.getBoundingBox(i)
                Rect(
                    maxOf(b.left, l.getLineLeft(row)),
                    if (row == 0) l.getLineTop(0) - lift * 3f else split(row - 1),
                    minOf(b.right, l.getLineRight(row)),
                    if (row == l.lineCount - 1) l.getLineBottom(row) + lift * 3f else split(row)
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = style.copy(color = Color.White.copy(alpha = 0.45f)),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val b = boxes
                    if (b == null) drawContent()
                    else drawLiftedWords(b, wordRanges, charReveal(), lift) { drawContent() }
                }
        )
        Text(
            text = text,
            style = style.copy(color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val l = layout
                    val b = boxes
                    if (l == null || b == null) return@drawWithContent
                    val sung = charReveal()
                    drawLiftedWords(b, wordRanges, sung, lift) { drawContent() }
                    drawCharReveal(l, sung, softEdge, lift, fontPx)
                }
        )
    }
}

/**
 * Draws [glyphs] once per character, clipped to that character and shifted by its word's
 * lift: unsung words sit low, and a word eases upward as it is sung and stays up.
 */
private inline fun DrawScope.drawLiftedWords(
    boxes: List<Rect>,
    wordRanges: List<IntRange>,
    sung: Float,
    maxLift: Float,
    glyphs: () -> Unit
) {
    for (range in wordRanges) {
        val len = (range.last - range.first + 1).coerceAtLeast(1)
        val f = ((sung - range.first) / len).coerceIn(0f, 1f)
        val eased = 1f - (1f - f) * (1f - f) * (1f - f)
        val dy = maxLift - maxLift * 2f * eased
        for (i in range) {
            val box = boxes.getOrNull(i) ?: continue
            if (box.width <= 0f) continue
            clipRect(box.left, box.top, box.right, box.bottom) {
                translate(top = dy) { glyphs() }
            }
        }
    }
}

/** Character range [start, end) of each word inside the joined line text. */
private fun wordCharRanges(words: List<LyricWord>): List<IntRange> {
    var offset = 0
    return words.map { w ->
        val range = offset until offset + w.text.length
        offset += w.text.length
        range
    }
}

/** Sung characters at [positionMs]: whole words before it, plus a fraction of the active word. */
private fun revealedChars(words: List<LyricWord>, ranges: List<IntRange>, positionMs: Long): Float {
    var revealed = 0f
    for ((i, w) in words.withIndex()) {
        if (positionMs < w.startMs) break
        val r = ranges[i]
        // Count only the visible part of the word (trailing space is instant).
        val visible = w.text.trimEnd().length.coerceAtLeast(1)
        val f = if (w.endMs > w.startMs) ((positionMs - w.startMs).toFloat() / (w.endMs - w.startMs)).coerceIn(0f, 1f) else 1f
        revealed = if (f >= 1f) (r.last + 1).toFloat() else r.first + visible * f
    }
    return revealed
}

/** Erase the unsung part of each row, with the soft edge sitting at the exact sung position. */
/** Boundary between [row] and the next one: just below the baseline, past descenders. */
private fun rowSplit(l: TextLayoutResult, row: Int, fontPx: Float) = l.getLineBaseline(row) + fontPx * 0.42f

private fun DrawScope.drawCharReveal(l: TextLayoutResult, sung: Float, softEdge: Float, lift: Float, fontPx: Float) {
    for (row in 0 until l.lineCount) {
        val start = l.getLineStart(row)
        val end = l.getLineEnd(row, visibleEnd = true)
        if (sung >= end) continue
        val left = l.getLineLeft(row)
        val right = l.getLineRight(row)
        val bandTop = if (row == 0) l.getLineTop(0) - lift * 3f else rowSplit(l, row - 1, fontPx)
        val bandBottom = if (row == l.lineCount - 1) l.getLineBottom(row) + lift * 3f else rowSplit(l, row, fontPx)
        val x = if (sung <= start) {
            left - softEdge
        } else {
            val i = sung.toInt()
            val a = l.getHorizontalPosition(i, usePrimaryDirection = true)
            val b = l.getHorizontalPosition((i + 1).coerceAtMost(end), usePrimaryDirection = true)
            a + (b - a) * (sung - i)
        }
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startX = x - softEdge / 2,
                endX = x + softEdge / 2
            ),
            // Same row bands as the lift clip, so erasing an unsung row never
            // eats the descenders of the row above it.
            topLeft = Offset(left - softEdge, bandTop),
            size = Size(right - left + softEdge * 2, bandBottom - bandTop),
            blendMode = BlendMode.DstOut
        )
    }
}

// ---------------------------------------------------------------------------
// Seek controls
// ---------------------------------------------------------------------------

/**
 * Portrait scrubber: one permanent Canvas node that expands into a capsule while scrubbing.
 * The whole row accepts touches; the idle line is too thin to aim at.
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
    var scrubProgress by remember { mutableFloatStateOf(playbackProgress) }
    var dragStartProgress by remember { mutableFloatStateOf(0f) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var collapseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val expansion by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "scrubberExpand"
    )

    fun collapseAfter(ms: Long) {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(ms)
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
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            collapseJob?.cancel()
                            isScrubbing = true
                            dragStartProgress = scrubProgress
                            dragDistancePx = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragDistancePx += dragAmount
                            scrubProgress = (dragStartProgress + dragDistancePx / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((scrubProgress * durationMs).toLong())
                            collapseAfter(650)
                        },
                        onDragCancel = { collapseAfter(650) }
                    )
                }
                .pointerInput(durationMs) {
                    detectTapGestures {
                        // Tap expands only; the playhead is preserved and no seek is sent.
                        isScrubbing = true
                        collapseAfter(1200)
                    }
                }
        ) {
            val idleWidth = 92.dp.toPx()
            val fullWidth = size.width
            val shellWidth = idleWidth + (fullWidth - idleWidth) * expansion
            val shellLeft = (fullWidth - shellWidth) / 2f
            val shellHeight = 28.dp.toPx() + 16.dp.toPx() * expansion
            val shellTop = (size.height - shellHeight) / 2f

            if (expansion > 0.01f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = (0.17f * expansion).coerceIn(0f, 1f)),
                    topLeft = Offset(shellLeft, shellTop),
                    size = Size(shellWidth, shellHeight),
                    cornerRadius = CornerRadius(shellHeight / 2f)
                )
            }

            val sideSpace = 96.dp.toPx() * expansion
            val trackLeft = shellLeft + sideSpace / 2f
            val trackWidth = (shellWidth - sideSpace).coerceAtLeast(1f)
            val trackHeight = 3.dp.toPx() + 1.dp.toPx() * expansion
            val trackTop = (size.height - trackHeight) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(trackLeft, trackTop),
                size = Size(trackWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.88f),
                topLeft = Offset(trackLeft, trackTop),
                size = Size(trackWidth * scrubProgress, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
        }

        if (expansion > 0.01f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .alpha(expansion.coerceIn(0f, 1f)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = formatMs((scrubProgress * durationMs).toLong()), fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                Text(text = formatMs(durationMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
            }
        }
    }
}

/**
 * Apple-Music-style progress: a hairline that swells while held, elapsed / remaining below.
 * Tap anywhere to jump, or drag; the seek is sent on release.
 */
@Composable
internal fun AppleProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val thickness by animateDpAsState(
        targetValue = if (dragFraction != null) 9.dp * scale else 5.dp * scale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "progressThickness"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp * scale)
                .pointerInput(durationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var f = (down.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = f
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            f = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            change.consume()
                        }
                        if (durationMs > 0) onSeek((f * durationMs).toLong())
                        dragFraction = null
                    }
                }
        ) {
            val h = thickness.toPx()
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, top),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h / 2f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (dragFraction != null) 1f else 0.78f),
                topLeft = Offset(0f, top),
                size = Size(size.width * fraction, h),
                cornerRadius = CornerRadius(h / 2f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shownMs = (fraction * durationMs).toLong()
            val timeColor = Color.White.copy(alpha = if (dragFraction != null) 0.9f else 0.5f)
            BouncyTime(formatMs(shownMs), 11.sp * scale, timeColor)
            BouncyTime("-" + formatMs((durationMs - shownMs).coerceAtLeast(0L)), 11.sp * scale, timeColor)
        }
    }
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * Time label whose changing digits roll, after Lyricify: the old digit slides up and
 * out while blurring and fading, the new one rises from below and sharpens into place.
 * Tabular figures keep the width steady.
 */
@Composable
private fun BouncyTime(text: String, fontSize: TextUnit, color: Color) {
    val style = TextStyle(fontSize = fontSize, color = color, fontFeatureSettings = "tnum")
    Row {
        // Key by position from the right so "9:59" -> "10:00" keeps the seconds aligned.
        text.forEachIndexed { i, ch ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val ease = tween<IntOffset>(380, easing = FastOutSlowInEasing)
                        (slideInVertically(ease) { it / 2 } + fadeIn(tween(300)))
                            .togetherWith(slideOutVertically(ease) { -it / 2 } + fadeOut(tween(260)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "timeDigit"
                ) { c ->
                    val blur by transition.animateDp(
                        transitionSpec = { tween(380) },
                        label = "digitBlur"
                    ) { state -> if (state == EnterExitState.Visible) 0.dp else 2.5.dp }
                    Text(
                        c.toString(),
                        style = style,
                        modifier = if (blur > 0.dp) Modifier.blur(blur, BlurredEdgeTreatment.Unbounded) else Modifier
                    )
                }
            }
        }
    }
}
