package com.example.spotifylyricsproxy.ui.playback

import androidx.compose.foundation.basicMarquee
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.core.AppSettings
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.PlaybackOptions
import com.example.spotifylyricsproxy.spotify.remote.RepeatMode
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo

/**
 * Everything the three player layouts need, gathered once from the ViewModel.
 * Positions are lambdas: only the progress bar and the lyric sweep read them, so the
 * 300 ms clock tick no longer recomposes the whole screen.
 */
@Immutable
private data class PlayerUiState(
    val connectionState: SpotifyConnectionState,
    val trackInfo: SpotifyTrackInfo,
    val albumArt: Bitmap?,
    val palette: AlbumPalette,
    /** Playback position (300 ms ticks) for the progress bar. */
    val positionMs: () -> Long,
    /** Live position with per-song + global offsets removed, for the per-frame lyric sweep. */
    val lyricPositionMs: () -> Long,
    val currentLine: LrcLine?,
    val lines: List<LrcLine>,
    val lyricStatus: LyricStatus,
    val translatedLine: String?,
    val isTranslationEnabled: Boolean,
    val targetTranslationLang: String,
    /** Language detected in the current lyrics (null until known). */
    val detectedLyricsLang: String?,
    val playbackOptions: PlaybackOptions,
    val isSpotifyInstalled: Boolean,
    /** "Title · Artist" of the queued next track, or null. */
    val nextUp: String? = null
) {
    val isPlaying get() = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty()
    val hasTrack get() = connectionState is SpotifyConnectionState.Connected || trackInfo.trackId.isNotEmpty()
}

private class PlayerActions(
    val onSeek: (Long) -> Unit,
    val onPlayPause: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    /** null turns translation off; a language tag turns it on with that target. */
    val onSelectTranslation: (String?) -> Unit,
    val onOpenMenu: () -> Unit,
    val onSearchManually: () -> Unit,
    val onConnect: () -> Unit,
    val onOpenSpotify: () -> Unit,
    val onAllowMobileData: () -> Unit,
    val onDenyMobileData: () -> Unit
)

@Composable
fun PlaybackScreen(
    viewModel: PlaybackViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenLyricsCorrection: () -> Unit = {},
    showLyricSettingsFromDrawer: Boolean = false,
    onLyricSettingsShown: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val playbackOptions by viewModel.playbackOptions.collectAsState()
    val nextTrack by viewModel.nextTrack.collectAsState()
    val trackInfo by viewModel.currentTrack.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val positionState = viewModel.estimatedPositionMs.collectAsState()
    val currentLyricLine by viewModel.currentLyricLine.collectAsState()
    val parsedLyrics by viewModel.parsedLyrics.collectAsState()
    val lyricStatus by viewModel.lyricStatus.collectAsState()
    val translatedLine by viewModel.translatedLine.collectAsState()
    val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState()
    val targetTranslationLang by viewModel.targetTranslationLang.collectAsState()
    val detectedLyricsLang by viewModel.detectedLyricsLang.collectAsState()
    val isPreparingTranslation by viewModel.isPreparingTranslation.collectAsState()
    val preparingText = stringResource(R.string.translation_preparing)
    val songOffsetMs by viewModel.currentOffsetMs.collectAsState()
    val showMobileDataDialog by viewModel.showMobileDataDialog.collectAsState()
    val palette = remember(albumArt) { albumPalette(albumArt) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    var showLyricDisplaySettings by remember { mutableStateOf(false) }
    LaunchedEffect(showLyricSettingsFromDrawer) {
        if (showLyricSettingsFromDrawer) {
            showLyricDisplaySettings = true
            onLyricSettingsShown()
        }
    }
    val isSpotifyInstalled = rememberIsSpotifyInstalled()
    val activity = LocalContext.current as? android.app.Activity

    // Phones in landscape go full-bleed; tablets keep their system bars.
    val hideSystemBars = isLandscape && !isTablet
    DisposableEffect(activity, hideSystemBars) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideSystemBars) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val allowMobileData = {
        LyricDisplayPreferences.setTodayMobileDataChoice("allow")
        viewModel.confirmMobileDataFetch()
    }
    val denyMobileData = {
        LyricDisplayPreferences.setTodayMobileDataChoice("deny")
        viewModel.dismissMobileDataDialog()
    }

    // Stable lambdas (remembered once) that read the latest values when invoked.
    val currentSongOffset = rememberUpdatedState(songOffsetMs)
    val positionLambda = remember(positionState) { { positionState.value } }
    val lyricPositionLambda = remember(viewModel) {
        { viewModel.livePositionMs() - currentSongOffset.value - AppSettings.globalOffsetMs.longValue }
    }

    val state = PlayerUiState(
        connectionState = connectionState,
        trackInfo = trackInfo,
        albumArt = albumArt,
        palette = palette,
        positionMs = positionLambda,
        lyricPositionMs = lyricPositionLambda,
        currentLine = currentLyricLine,
        lines = parsedLyrics,
        lyricStatus = lyricStatus,
        // Say something while a language pack downloads instead of staying silent.
        translatedLine = translatedLine
            ?: preparingText.takeIf { isTranslationEnabled && isPreparingTranslation },
        isTranslationEnabled = isTranslationEnabled,
        targetTranslationLang = targetTranslationLang,
        detectedLyricsLang = detectedLyricsLang,
        playbackOptions = playbackOptions,
        isSpotifyInstalled = isSpotifyInstalled,
        nextUp = nextTrack?.let { t -> listOf(t.name, t.artists.firstOrNull()?.name.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ") }
    )
    val actions = PlayerActions(
        onSeek = viewModel::seekTo,
        onPlayPause = viewModel::togglePlayPause,
        onSkipNext = viewModel::skipNext,
        onSkipPrevious = viewModel::skipPrevious,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeat,
        onSelectTranslation = { lang ->
            if (lang == null) {
                viewModel.setTranslationEnabled(false)
            } else {
                if (lang != targetTranslationLang) viewModel.setTranslationTargetLang(lang)
                if (!isTranslationEnabled) viewModel.setTranslationEnabled(true)
            }
        },
        onOpenMenu = onOpenDrawer,
        onSearchManually = onOpenLyricsCorrection,
        onConnect = viewModel::connect,
        onOpenSpotify = viewModel::openSpotifyAndConnect,
        onAllowMobileData = allowMobileData,
        onDenyMobileData = denyMobileData
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(palette.deep)
        ) {
            when {
                isLandscape -> SplitPlayerLayout(state, actions, scale = if (isTablet) 1.25f else 1f)
                isTablet -> TabletPortraitLayout(state, actions)
                else -> PhonePortraitLayout(state, actions)
            }
        }

        if (showLyricDisplaySettings) {
            LyricDisplaySettingsDialog(
                onDismiss = { showLyricDisplaySettings = false }
            )
        }

        if (showMobileDataDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMobileDataDialog() },
                title = { Text(stringResource(R.string.mobile_data_dialog_title)) },
                text = { Text(stringResource(R.string.mobile_data_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = allowMobileData) { Text(stringResource(R.string.mobile_data_allow)) }
                },
                dismissButton = {
                    TextButton(onClick = denyMobileData) { Text(stringResource(R.string.mobile_data_deny)) }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Layouts
// ---------------------------------------------------------------------------

/** Phone portrait: clear cover stage on top, header + scrubber, lyrics fill the rest. */
@Composable
private fun PhonePortraitLayout(state: PlayerUiState, actions: PlayerActions) {
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientAlbumBackdrop(albumArt = state.albumArt, palette = state.palette, animate = state.isPlaying)
        ImmersiveAlbumBackground(albumArt = state.albumArt, palette = state.palette)

        PlayerChrome(
            state = state,
            actions = actions,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 6.dp, end = 12.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(LocalConfiguration.current.screenHeightDp.dp * 0.36f))
            ImmersiveTrackHeader(
                trackInfo = state.trackInfo,
                connectionState = state.connectionState,
                isPlaying = state.isPlaying,
                isConnected = state.hasTrack,
                onPlayPause = actions.onPlayPause,
                onSkipNext = actions.onSkipNext,
                onSkipPrevious = actions.onSkipPrevious,
                modifier = Modifier.padding(top = 28.dp)
            )
            ReadPosition(state.positionMs) { position ->
                ImmersiveSeekControl(
                    estimatedPositionMs = position,
                    durationMs = state.trackInfo.durationMs,
                    onSeek = actions.onSeek,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 14.dp)
                )
            }
            if (state.hasTrack) {
                PlayerLyrics(
                    state = state,
                    actions = actions,
                    config = LyricDisplayPreferences.resolvedConfig(),
                    textScale = 1f,
                    anchorFraction = 0.12f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                        .fadingEdges(top = 0.06f, bottom = 0.18f)
                )
            } else {
                ConnectActionPanel(
                    state = state.connectionState,
                    accent = state.palette.accent,
                    isSpotifyInstalled = state.isSpotifyInstalled,
                    onConnect = actions.onConnect,
                    onOpenSpotify = actions.onOpenSpotify,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                )
            }
        }
    }
}

/**
 * Landscape (phone and tablet): Apple-Music-style split. Left, one centred block whose
 * every row shares the cover's width; right, left-aligned lyrics with faded edges.
 */
@Composable
private fun SplitPlayerLayout(state: PlayerUiState, actions: PlayerActions, scale: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        PlayerBackground(state)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Phones are short in landscape: a smaller cover leaves room for the rest,
            // and the whole block is centred on the cover's axis.
            val compact = scale <= 1f
            // Compact: the cover takes whatever height the title, progress and
            // transport rows (~180dp) leave, capped by the left pane's width.
            val coverSize = if (compact) min(maxHeight - 180.dp, maxWidth * 0.45f * 0.82f)
                else min(maxHeight * 0.58f, maxWidth * 0.34f)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(start = 24.dp * scale, top = 20.dp * scale, bottom = 12.dp * scale),
                    contentAlignment = if (compact) Alignment.Center else Alignment.TopCenter
                ) {
                    NowPlayingBlock(
                        state = state,
                        actions = actions,
                        coverSize = coverSize,
                        scale = scale,
                        compact = compact
                    )
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                ) {
                    if (state.hasTrack) {
                        PlayerLyrics(
                            state = state,
                            actions = actions,
                            config = LyricDisplayPreferences.resolvedConfig(),
                            textScale = scale * 1.08f,
                            // Current line in the upper third, like Lyricify / Apple Music.
                            anchorFraction = 0.24f,
                            contentPadding = PaddingValues(top = maxHeight * 0.24f, bottom = maxHeight * 0.65f),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp * scale)
                                .fadingEdges(top = 0.16f, bottom = 0.30f)
                        )
                    } else {
                        ConnectActionPanel(
                            state = state.connectionState,
                            accent = state.palette.accent,
                            isSpotifyInstalled = state.isSpotifyInstalled,
                            onConnect = actions.onConnect,
                            onOpenSpotify = actions.onOpenSpotify,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                                .wrapContentSize(Alignment.Center)
                        )
                    }
                }
            }

            // Phone landscape carries these beside the title instead.
            if (!compact) {
                PlayerChrome(
                    state = state,
                    actions = actions,
                    scale = scale,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 14.dp)
                )
            }
        }
    }
}

/** Tablet portrait: cover and controls side by side on top, full-width lyrics below. */
@Composable
private fun TabletPortraitLayout(state: PlayerUiState, actions: PlayerActions) {
    val scale = 1.3f
    Box(modifier = Modifier.fillMaxSize()) {
        PlayerBackground(state)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val coverSize = maxWidth * 0.30f
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 40.dp, top = 64.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    AlbumCover(state.albumArt, state.isPlaying, coverSize)
                    Column(modifier = Modifier.weight(1f)) {
                        TrackMeta(state.trackInfo, scale)
                        Spacer(modifier = Modifier.height(12.dp))
                        LiveProgressBar(state, actions, scale)
                        TransportRow(state, actions, scale)
                    }
                }
                if (state.hasTrack) {
                    PlayerLyrics(
                        state = state,
                        actions = actions,
                        config = LyricDisplayPreferences.resolvedConfig(),
                        textScale = scale,
                        anchorFraction = 0.2f,
                        contentPadding = PaddingValues(top = 48.dp, bottom = 320.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 36.dp)
                            .fadingEdges(top = 0.10f, bottom = 0.25f)
                    )
                } else {
                    ConnectActionPanel(
                        state = state.connectionState,
                        accent = state.palette.accent,
                        isSpotifyInstalled = state.isSpotifyInstalled,
                        onConnect = actions.onConnect,
                        onOpenSpotify = actions.onOpenSpotify,
                        modifier = Modifier
                            .weight(1f)
                            .padding(40.dp)
                            .wrapContentSize(Alignment.Center)
                    )
                }
            }

            PlayerChrome(
                state = state,
                actions = actions,
                scale = scale,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun PlayerBackground(state: PlayerUiState) {
    MeshGradientBackground(albumArt = state.albumArt, palette = state.palette, animate = state.isPlaying)
    // A light left→right veil keeps the lyric column calm without flattening the colours.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.24f))))
    )
}

@Composable
private fun PlayerLyrics(
    state: PlayerUiState,
    actions: PlayerActions,
    config: LyricDisplayConfig,
    textScale: Float,
    anchorFraction: Float,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 120.dp)
) {
    ImmersiveLyricsBlock(
        currentLine = state.currentLine,
        allLines = state.lines,
        status = state.lyricStatus,
        translatedLine = state.translatedLine,
        isTranslationEnabled = state.isTranslationEnabled,
        isPlaying = state.isPlaying,
        positionMs = state.lyricPositionMs,
        config = config,
        onSeek = actions.onSeek,
        onAllowMobileData = actions.onAllowMobileData,
        onDenyMobileData = actions.onDenyMobileData,
        onSearchManually = actions.onSearchManually,
        textScale = textScale,
        anchorFraction = anchorFraction,
        contentPadding = contentPadding,
        modifier = modifier.coachTarget("lyrics")
    )
}

/**
 * Cover pinned near the top at full height budget; title, progress and transport share
 * the space below it evenly so the lower half of the screen is used, not left empty.
 */
@Composable
private fun NowPlayingBlock(state: PlayerUiState, actions: PlayerActions, coverSize: Dp, scale: Float, compact: Boolean = false) {
    if (compact) {
        // Phone landscape, after Lyricify: every row shares the cover's width and edges;
        // only a very small cover lets the five transport buttons run a little wider.
        Column(
            modifier = Modifier.width(maxOf(coverSize, 250.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumCover(state.albumArt, state.isPlaying, coverSize)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) { TrackMeta(state.trackInfo, scale) }
                Spacer(modifier = Modifier.width(8.dp))
                // The circles sit 4dp inside their touch boxes: nudge right so the last
                // circle's edge meets the progress bar's end.
                PlayerChrome(
                    state = state,
                    actions = actions,
                    scale = scale,
                    inline = true,
                    modifier = Modifier.offset(x = 4.dp * scale)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LiveProgressBar(state, actions, scale)
            TransportRow(state, actions, scale)
        }
        return
    }
    Column(modifier = Modifier.width(coverSize).fillMaxHeight()) {
        AlbumCover(state.albumArt, state.isPlaying, coverSize)
        Spacer(modifier = Modifier.weight(1f))
        TrackMeta(state.trackInfo, scale)
        Spacer(modifier = Modifier.weight(0.8f))
        LiveProgressBar(state, actions, scale)
        Spacer(modifier = Modifier.weight(0.8f))
        TransportRow(state, actions, scale)
        Spacer(modifier = Modifier.weight(0.6f))
    }
}

/** Own recomposition scope: reading the position here keeps the tick from reaching the parent. */
@Composable
private fun ReadPosition(position: () -> Long, content: @Composable (Long) -> Unit) {
    content(position())
}

@Composable
private fun LiveProgressBar(state: PlayerUiState, actions: PlayerActions, scale: Float) {
    Box(Modifier.coachTarget("progress")) {
    ReadPosition(state.positionMs) { position ->
        AppleProgressBar(
            positionMs = position,
            durationMs = state.trackInfo.durationMs,
            onSeek = actions.onSeek,
            scale = scale
        )
    }
    }
}

/** Cover that settles back when paused and springs forward when playback resumes. */
@Composable
private fun AlbumCover(albumArt: Bitmap?, isPlaying: Boolean, size: Dp) {
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 220f),
        label = "coverScale"
    )
    val shape = RoundedCornerShape(size * 0.035f)
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
            .shadow(elevation = 24.dp * coverScale, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = albumArt, animationSpec = tween(400), label = "coverCrossfade") { art ->
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = stringResource(R.string.album_art_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.playback_cd_album_art_placeholder),
                    modifier = Modifier.fillMaxSize(0.3f),
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrackMeta(trackInfo: SpotifyTrackInfo, scale: Float, centered: Boolean = false) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    // Long titles scroll instead of being cut off.
    // Marquee lays its content out from the left; centre the box first when centred.
    val rowModifier = (if (centered) Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally) else Modifier)
        .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
    Text(
        text = trackInfo.title.ifEmpty { stringResource(R.string.playback_title_waiting) },
        fontSize = 16.sp * scale,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        textAlign = align,
        modifier = rowModifier
    )
    val subtitle = listOf(trackInfo.artist, trackInfo.album).filter { it.isNotBlank() }.joinToString(" — ")
    if (subtitle.isNotEmpty()) {
        Text(
            text = subtitle,
            fontSize = 14.sp * scale,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 1,
                textAlign = align,
            modifier = rowModifier
        )
    }
}

/** Shuffle · previous · play/pause · next · repeat, bare icons spread across the row. */
@Composable
private fun TransportRow(state: PlayerUiState, actions: PlayerActions, scale: Float) {
    TransportButtons(state, actions, scale)
    val nextUp = state.nextUp
    if (!nextUp.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.playback_next_up, nextUp),
            fontSize = 12.sp * scale,
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp * scale)
        )
    }
}

@Composable
private fun TransportButtons(state: PlayerUiState, actions: PlayerActions, scale: Float) {
    val enabled = state.hasTrack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp * scale),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The toggles' touch boxes are wider than their icons: shift them outward so the
        // icons themselves line up with the cover and progress bar edges.
        Box(Modifier.offset(x = -8.dp * scale).coachTarget("shuffle")) {
            ModeToggle(
                icon = Icons.Filled.Shuffle,
                active = state.playbackOptions.isShuffling,
                contentDescription = stringResource(R.string.playback_cd_shuffle),
                scale = scale,
                onClick = actions.onToggleShuffle
            )
        }
        BareIconButton(onClick = actions.onSkipPrevious, size = 48.dp * scale, enabled = enabled) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.playback_cd_previous), Modifier.size(36.dp * scale), tint = Color.White)
        }
        Box(Modifier.coachTarget("play")) {
            BareIconButton(onClick = actions.onPlayPause, size = 56.dp * scale, enabled = enabled) {
                PlayPauseGlyph(isPlaying = state.isPlaying, size = 34.dp * scale)
            }
        }
        BareIconButton(onClick = actions.onSkipNext, size = 48.dp * scale, enabled = enabled) {
            Icon(Icons.Filled.SkipNext, stringResource(R.string.playback_cd_next), Modifier.size(36.dp * scale), tint = Color.White)
        }
        Box(Modifier.offset(x = 8.dp * scale).coachTarget("repeat")) {
            ModeToggle(
                icon = if (state.playbackOptions.repeatMode == RepeatMode.TRACK) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                active = state.playbackOptions.repeatMode != RepeatMode.OFF,
                contentDescription = stringResource(R.string.playback_cd_repeat),
                scale = scale,
                onClick = actions.onCycleRepeat
            )
        }
    }
}

/** A bare icon whose "on" state is full white plus a small dot underneath — no filled circle. */
@Composable
private fun ModeToggle(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    scale: Float,
    onClick: () -> Unit
) {
    BareIconButton(onClick = onClick, size = 36.dp * scale) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp * scale),
            tint = Color.White.copy(alpha = if (active) 1f else 0.45f)
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp * scale)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/** Top-right controls: translation language picker and the menu. */
@Composable
private fun PlayerChrome(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    /** Beside the title (phone landscape): translate becomes a frosted circle too. */
    inline: Boolean = false
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.coachTarget("translate")) {
            if (inline) {
                CircleIconButton(
                    icon = Icons.Filled.Translate,
                    contentDescription = stringResource(R.string.settings_translate_lyrics),
                    active = state.isTranslationEnabled,
                    scale = scale,
                    onClick = { pickerOpen = true }
                )
            } else {
                ModeToggle(
                    icon = Icons.Filled.Translate,
                    active = state.isTranslationEnabled,
                    contentDescription = stringResource(R.string.settings_translate_lyrics),
                    scale = scale * 1.15f,
                    onClick = { pickerOpen = true }
                )
            }
            TranslationPicker(
                expanded = pickerOpen,
                state = state,
                onSelect = {
                    pickerOpen = false
                    actions.onSelectTranslation(it)
                },
                onDismiss = { pickerOpen = false }
            )
        }
        Box(Modifier.coachTarget("menu")) {
            MenuCircleButton(onClick = actions.onOpenMenu, scale = scale)
        }
    }
}

/** Frosted-circle "more" button like Apple Music / Lyricify. */
@Composable
private fun MenuCircleButton(onClick: () -> Unit, scale: Float) {
    CircleIconButton(Icons.Filled.MoreHoriz, stringResource(R.string.playback_cd_more), true, scale, onClick)
}

/** Frosted circle icon button; an inactive toggle is dimmer. */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(Color.White.copy(alpha = if (active) 0.16f else 0.08f), label = "circleBg")
    BareIconButton(onClick = onClick, size = 42.dp * scale) {
        Box(
            modifier = Modifier
                .size(34.dp * scale)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp * scale),
                tint = Color.White.copy(alpha = if (active) 1f else 0.55f)
            )
        }
    }
}

private val TRANSLATION_TARGETS = listOf(
    "zh" to R.string.settings_translate_target_zh,
    "zh-TW" to R.string.settings_translate_target_tw,
    "en" to R.string.settings_translate_target_en,
    "ja" to R.string.settings_translate_target_ja
)

/** Off + one row per target language; notes when the lyrics are already in the chosen language. */
@Composable
private fun TranslationPicker(
    expanded: Boolean,
    state: PlayerUiState,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xF0202228)
    ) {
        val check: @Composable (Boolean) -> Unit = { on ->
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (on) Color.White else Color.Transparent,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.translation_off), color = Color.White) },
            leadingIcon = { check(!state.isTranslationEnabled) },
            onClick = { onSelect(null) }
        )
        TRANSLATION_TARGETS.forEach { (lang, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label), color = Color.White) },
                leadingIcon = { check(state.isTranslationEnabled && state.targetTranslationLang == lang) },
                onClick = { onSelect(lang) }
            )
        }
        val detected = state.detectedLyricsLang
        if (state.isTranslationEnabled && detected != null && state.translatedLine == null &&
            detected.substringBefore('-') == state.targetTranslationLang.substringBefore('-')
        ) {
            Text(
                text = stringResource(R.string.translation_same_language),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/** Fades content out toward the top and bottom edges (fractions of the height). */
private fun Modifier.fadingEdges(top: Float, bottom: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                top to Color.Black,
                (1f - bottom) to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

// ---------------------------------------------------------------------------
// Connection, palette and dialog (unchanged)
// ---------------------------------------------------------------------------

@Composable
private fun ConnectActionPanel(
    state: SpotifyConnectionState,
    accent: Color,
    isSpotifyInstalled: Boolean,
    onConnect: () -> Unit,
    onOpenSpotify: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        text = stringResource(R.string.playback_action_reconnect),
                        accent = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                        enabled = true,
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSpotifyInstalled) {
                        ActionPill(
                            text = stringResource(R.string.playback_action_open_spotify),
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
        modifier = modifier.coachTarget("lyrics")
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

internal data class AlbumPalette(
    val deep: Color,
    val mid: Color,
    val accent: Color
)

private val paletteCache = androidx.collection.LruCache<Int, AlbumPalette>(20)

internal fun albumPalette(bitmap: Bitmap?): AlbumPalette {
    if (bitmap == null) {
        return AlbumPalette(
            deep = Color(0xFF111827),
            mid = Color(0xFF243B55),
            accent = Color(0xFF6D7DFF)
        )
    }
    val key = bitmap.hashCode()
    paletteCache.get(key)?.let { return it }

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
    val result = AlbumPalette(
        deep = Color(avgR / 255f * 0.52f, avgG / 255f * 0.52f, avgB / 255f * 0.52f),
        mid = Color(avgR / 255f * 0.72f, avgG / 255f * 0.72f, avgB / 255f * 0.72f),
        accent = Color(
            red = (avgR + 64).coerceAtMost(255) / 255f,
            green = (avgG + 64).coerceAtMost(255) / 255f,
            blue = (avgB + 64).coerceAtMost(255) / 255f
        )
    )
    paletteCache.put(key, result)
    return result
}

@Composable
private fun rememberIsSpotifyInstalled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        context.packageManager.getLaunchIntentForPackage("com.spotify.music") != null
    }
}

@Composable
private fun connectionHint(state: SpotifyConnectionState): String = when (state) {
    is SpotifyConnectionState.Connecting -> stringResource(R.string.playback_hint_connecting)
    SpotifyConnectionState.SpotifyNotInstalled -> stringResource(R.string.playback_hint_not_installed)
    SpotifyConnectionState.SpotifyNotLoggedIn -> stringResource(R.string.playback_hint_not_logged_in)
    is SpotifyConnectionState.Error -> stringResource(R.string.playback_hint_error, state.message)
    SpotifyConnectionState.Disconnected -> stringResource(R.string.playback_hint_disconnected)
    SpotifyConnectionState.Connected -> ""
}

private fun readableOn(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.55f) Color.Black else Color.White
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricDisplaySettingsDialog(
    onDismiss: () -> Unit
) {
    // Alignment and blur live in the player menu's quick tiles; only what has no
    // other home is here.
    val currentDim by LyricDisplayPreferences.dimLevel

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyric_settings_title)) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.lyric_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val currentSp = LyricDisplayPreferences.fontSizeCurrent.value
                Column {
                    Text(
                        text = stringResource(R.string.lyric_settings_font_size, "${currentSp.toInt()}sp"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = currentSp,
                        onValueChange = { LyricDisplayPreferences.setFontSizeCurrent(it) },
                        valueRange = 12f..36f,
                        steps = 22
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.lyric_settings_dim_level),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dimLabels = listOf(
                            "low" to stringResource(R.string.lyric_settings_dim_low),
                            "medium" to stringResource(R.string.lyric_settings_dim_medium),
                            "high" to stringResource(R.string.lyric_settings_dim_high)
                        )
                        dimLabels.forEach { (value, label) ->
                            FilterChip(
                                selected = currentDim == value,
                                onClick = { LyricDisplayPreferences.setDimLevel(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lyric_settings_done)) }
        }
    )
}
