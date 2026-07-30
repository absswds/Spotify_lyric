package com.example.spotifylyricsproxy.ui.playback

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.spotifylyricsproxy.ui.playback.ImmersiveAlbumBackground
import com.example.spotifylyricsproxy.ui.playback.ImmersiveLyricsBlock
import com.example.spotifylyricsproxy.ui.playback.ImmersiveSeekControl
import com.example.spotifylyricsproxy.ui.playback.ImmersiveTrackHeader
import com.example.spotifylyricsproxy.ui.playback.AlbumPalette
import com.example.spotifylyricsproxy.ui.theme.GlassSurface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.lyrics.LyricStatus
import com.example.spotifylyricsproxy.spotify.remote.PlaybackOptions
import com.example.spotifylyricsproxy.spotify.remote.RepeatMode
import com.example.spotifylyricsproxy.spotify.remote.SpotifyConnectionState
import com.example.spotifylyricsproxy.spotify.remote.SpotifyTrackInfo
import kotlinx.coroutines.launch

@Composable
fun PlaybackScreen(
    viewModel: PlaybackViewModel,
    onOpenDrawer: () -> Unit = {},
    onOpenPlaylist: () -> Unit = {},
    onOpenLyricsCorrection: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    onOpenPrecache: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    showLyricSettingsFromDrawer: Boolean = false,
    onLyricSettingsShown: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val playbackOptions by viewModel.playbackOptions.collectAsState()
    val trackInfo by viewModel.currentTrack.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val estimatedPositionMs by viewModel.estimatedPositionMs.collectAsState()
    val currentLyricLine by viewModel.currentLyricLine.collectAsState()
    val parsedLyrics by viewModel.parsedLyrics.collectAsState()
    val lyricStatus by viewModel.lyricStatus.collectAsState()
    val translatedLine by viewModel.translatedLine.collectAsState()
    val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState()
    val targetTranslationLang by viewModel.targetTranslationLang.collectAsState()
    val lyricSource by viewModel.lyricSource.collectAsState()
    val palette = remember(albumArt) { albumPalette(albumArt) }
    val showCandidatePicker by viewModel.showCandidatePicker.collectAsState()
    val showMobileDataDialog by viewModel.showMobileDataDialog.collectAsState()
    var lyricsExpanded by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showLyricDisplaySettings by remember { mutableStateOf(false) }
    LaunchedEffect(showLyricSettingsFromDrawer) {
        if (showLyricSettingsFromDrawer) {
            showLyricDisplaySettings = true
            onLyricSettingsShown()
        }
    }
    val isSpotifyInstalled = rememberIsSpotifyInstalled()
    val activity = LocalContext.current as? android.app.Activity

    DisposableEffect(activity, isLandscape, lyricsExpanded) {
        val window = activity?.window
        val decorView = window?.decorView
        val controller = if (window != null && decorView != null) {
            WindowInsetsControllerCompat(window, decorView)
        } else {
            null
        }

        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isLandscape || lyricsExpanded) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        // Root box draws behind the status bar. The gradient is computed from the
        // album palette and fills the entire screen so the top never shows black.
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
        ) {
        // Content is allowed to draw under the status bar; only bottom nav inset
        // (when present on non-playback routes) is respected via padding.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (lyricsExpanded && parsedLyrics.isNotEmpty()) {
                ExpandedLyricsView(
                    lines = parsedLyrics,
                    currentLine = currentLyricLine,
                    estimatedPositionMs = estimatedPositionMs,
                    durationMs = trackInfo.durationMs,
                    isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                    accent = palette.accent,
                    playbackOptions = playbackOptions,
                    translatedLine = translatedLine,
                    isTranslationEnabled = isTranslationEnabled,
                    onSeek = viewModel::seekTo,
                    onPlayPause = viewModel::togglePlayPause,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
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
                    translatedLine = translatedLine,
                    isTranslationEnabled = isTranslationEnabled,
                    playbackOptions = playbackOptions,
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
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                    onLyricDisplaySettings = { showLyricDisplaySettings = true }
                )
            } else {
                // Immersive portrait layout
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Full-screen blurred cover + palette wash; this prevents the lower page
                    // from falling into a detached black field.
                    AmbientAlbumBackdrop(
                        albumArt = albumArt,
                        palette = palette
                    )

                    // Clear cover art stage at the top.
                    ImmersiveAlbumBackground(
                        albumArt = albumArt,
                        palette = palette
                    )

                    // Interactive control respects the status inset; the album art does not.
                    CompactTopBar(
                        state = connectionState,
                        onOpenDrawer = onOpenDrawer,
                        showStatus = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 8.dp, end = 16.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        // The art stage owns the status-bar area; metadata starts only after it.
                        Spacer(
                            modifier = Modifier.height(
                                with(LocalConfiguration.current) {
                                    screenHeightDp.dp * 0.36f
                                }
                            )
                        )

                        // Track header with compact controls
                        ImmersiveTrackHeader(
                            trackInfo = trackInfo,
                            connectionState = connectionState,
                            isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                            isConnected = connectionState is SpotifyConnectionState.Connected,
                            onPlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                            onSkipPrevious = viewModel::skipPrevious,
                            modifier = Modifier.padding(top = 28.dp)
                        )

                        // Progress lives directly below the title, before lyrics: never hidden by
                        // the gesture/navigation area or squeezed below a weighted list.
                        ImmersiveSeekControl(
                            estimatedPositionMs = estimatedPositionMs,
                            durationMs = trackInfo.durationMs,
                            onSeek = viewModel::seekTo,
                            modifier = Modifier
                                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 14.dp)
                        )

                        // Lyrics (fixed anchor, uniform blur)
                        if (connectionState is SpotifyConnectionState.Connected) {
                            ImmersiveLyricsBlock(
                                currentLine = currentLyricLine,
                                allLines = parsedLyrics,
                                status = lyricStatus,
                                translatedLine = translatedLine,
                                isTranslationEnabled = isTranslationEnabled,
                                isPlaying = !trackInfo.isPaused && trackInfo.trackId.isNotEmpty(),
                                positionMs = estimatedPositionMs,
                                config = LyricDisplayPreferences.resolvedConfig(),
                                onSeek = viewModel::seekTo,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 24.dp)
                            )
                        } else {
                            ConnectActionPanel(
                                state = connectionState,
                                accent = palette.accent,
                                isSpotifyInstalled = isSpotifyInstalled,
                                onConnect = viewModel::connect,
                                onOpenSpotify = viewModel::openSpotifyAndConnect,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        } // close background box (full-screen gradient)

        if (showLyricDisplaySettings) {
            LyricDisplaySettingsDialog(
                isTranslationEnabled = isTranslationEnabled,
                onSetTranslationEnabled = viewModel::setTranslationEnabled,
                targetTranslationLang = targetTranslationLang,
                onSetTargetTranslationLang = viewModel::setTranslationTargetLang,
                currentSource = lyricSource,
                onDismiss = { showLyricDisplaySettings = false }
            )
        }

        if (showMobileDataDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMobileDataDialog() },
                title = { Text(stringResource(R.string.mobile_data_dialog_title)) },
                text = { Text(stringResource(R.string.mobile_data_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        LyricDisplayPreferences.setTodayMobileDataChoice("allow")
                        viewModel.confirmMobileDataFetch()
                    }) {
                        Text(stringResource(R.string.mobile_data_allow))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        LyricDisplayPreferences.setTodayMobileDataChoice("deny")
                        viewModel.dismissMobileDataDialog()
                    }) {
                        Text(stringResource(R.string.mobile_data_deny))
                    }
                }
            )
        }
    }
}

@Composable
private fun CompactTopBar(
    state: SpotifyConnectionState,
    onOpenDrawer: () -> Unit,
    showStatus: Boolean = true,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showStatus) {
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
        } else {
            // Minimal spacer in landscape to keep menu right-aligned
            Spacer(modifier = Modifier.width(1.dp))
        }

        GlassSurface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            glassAlpha = 0.12f,
            borderAlpha = 0.20f
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.playback_cd_more),
                    tint = Color.White.copy(alpha = 0.9f)
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
                contentDescription = stringResource(R.string.nav_playlist),
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.playback_playlist_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.playback_playlist_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.56f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.playback_cd_playlist),
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
private fun AlbumArtHero(albumArt: Bitmap?, accent: Color, widthFraction: Float = 0.66f) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
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
            Crossfade(
                targetState = albumArt,
                animationSpec = tween(durationMillis = 300)
            ) { art ->
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
                        modifier = Modifier.size(86.dp),
                        tint = Color.White.copy(alpha = 0.84f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackTitleBlock(
    trackInfo: SpotifyTrackInfo,
    connectionState: SpotifyConnectionState,
    textScale: Float = 1f
) {
    Crossfade(
        targetState = trackInfo.title,
        animationSpec = tween(durationMillis = 300)
    ) { title ->
        Text(
            text = title.ifEmpty { stringResource(R.string.playback_title_waiting) },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = MaterialTheme.typography.headlineSmall.fontSize * textScale
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(modifier = Modifier.height((6 * textScale).dp))
    Crossfade(
        targetState = trackInfo.artist,
        animationSpec = tween(durationMillis = 300)
    ) { artist ->
        Text(
            text = if (artist.isNotEmpty()) {
                listOf(artist, trackInfo.album)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            } else {
                if (connectionState is SpotifyConnectionState.Connected) stringResource(R.string.playback_subtitle_waiting) else stringResource(R.string.playback_subtitle_connect_prompt)
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
            ),
            color = Color.White.copy(alpha = 0.66f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactLyricsBlock(
    currentLine: LrcLine?,
    allLines: List<LrcLine>,
    status: LyricStatus,
    translatedLine: String?,
    isTranslationEnabled: Boolean,
    onExpand: () -> Unit,
    onImportLyrics: (String) -> Unit = {}
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
                is LyricStatus.Idle -> LyricMessage(stringResource(R.string.playback_lyrics_idle))
                is LyricStatus.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LyricMessage(stringResource(R.string.playback_lyrics_searching))
                }
                is LyricStatus.Synced -> {
                    Text(
                        text = currentLine?.text ?: stringResource(R.string.playback_lyrics_synced_placeholder),
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = config.currentLineSp,
                        color = Color.White,
                        textAlign = config.textAlign,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isTranslationEnabled && !translatedLine.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = translatedLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = config.textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    next?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        ContextLyric(it, config)
                    }
                }
                is LyricStatus.PlainOnly -> LyricMessage(stringResource(R.string.playback_lyrics_plain_only))
                is LyricStatus.NotFound -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LyricMessage(stringResource(R.string.playback_lyrics_not_found))
                        Spacer(modifier = Modifier.height(12.dp))
                        val context = LocalContext.current
                        val launcher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            uri?.let {
                                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
                                if (!text.isNullOrBlank()) {
                                    onImportLyrics(text)
                                }
                            }
                        }
                        OutlinedButton(onClick = { launcher.launch(arrayOf("application/octet-stream", "text/plain")) }) {
                            Text(stringResource(R.string.import_lyrics_button))
                        }
                    }
                }
                is LyricStatus.LowConfidence -> LyricMessage(stringResource(R.string.playback_lyrics_low_confidence))
                is LyricStatus.ParseError -> LyricMessage(stringResource(R.string.playback_lyrics_parse_error))
                is LyricStatus.Error -> LyricMessage(stringResource(R.string.playback_lyrics_load_error))
                is LyricStatus.MobileDataRestricted -> LyricMessage(stringResource(R.string.mobile_data_restricted_short))
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
private fun ContextLyric(
    text: String,
    config: LyricDisplayConfig = LyricDisplayPreferences.resolvedConfig()
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontSize = config.otherLineSp,
        color = Color.White.copy(alpha = 0.48f),
        textAlign = config.textAlign,
        maxLines = 2,
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
    playbackOptions: PlaybackOptions,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
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
            playbackOptions = playbackOptions,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat
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
    playbackOptions: PlaybackOptions,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        ModeToggleButton(
            isActive = playbackOptions.isShuffling,
            accent = accent,
            icon = Icons.Filled.Shuffle,
            contentDescription = stringResource(R.string.playback_cd_shuffle),
            iconSize = 26.dp,
            onClick = onToggleShuffle
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Previous
        IconButton(onClick = onSkipPrevious, enabled = isConnected) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.playback_cd_previous),
                modifier = Modifier.size(34.dp),
                tint = Color.White.copy(alpha = if (isConnected) 0.86f else 0.28f)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))

        // Play/Pause
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
                Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(durationMillis = 250)
                    ) { playing ->
                        if (playing) PauseGlyph(color = buttonContent)
                        else Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.playback_cd_play),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Next
        IconButton(onClick = onSkipNext, enabled = isConnected) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.playback_cd_next),
                modifier = Modifier.size(34.dp),
                tint = Color.White.copy(alpha = if (isConnected) 0.86f else 0.28f)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Repeat
        ModeToggleButton(
            isActive = playbackOptions.repeatMode != RepeatMode.OFF,
            accent = accent,
            icon = if (playbackOptions.repeatMode == RepeatMode.TRACK)
                Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = stringResource(R.string.playback_cd_repeat),
            iconSize = 26.dp,
            onClick = onCycleRepeat
        )
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

/** A compact mode-toggle icon with a visible background circle when active. */
@Composable
private fun ModeToggleButton(
    isActive: Boolean,
    accent: Color,
    icon: ImageVector,
    contentDescription: String,
    iconSize: Dp = 26.dp,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(iconSize + 14.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (isActive) accent else Color.White.copy(alpha = 0.25f)
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
private fun ExpandedLyricsView(
    lines: List<LrcLine>,
    currentLine: LrcLine?,
    estimatedPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    accent: Color,
    playbackOptions: PlaybackOptions,
    translatedLine: String?,
    isTranslationEnabled: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCollapse: () -> Unit
) {
    val currentIndex = currentLine?.let { lines.indexOf(it) } ?: -1
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val config = LyricDisplayPreferences.resolvedLandscapeConfig()

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.startMs) }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = line.text,
                        color = when {
                            isCurrent -> Color.White
                            isPast -> Color.White.copy(alpha = config.pastLineAlpha)
                            else -> Color.White.copy(alpha = config.futureLineAlpha)
                        },
                        fontSize = if (isCurrent) config.currentLineSp else config.otherLineSp,
                        fontWeight = if (isCurrent) config.currentLineWeight else FontWeight.Normal,
                        textAlign = config.textAlign
                    )
                    if (isCurrent && isTranslationEnabled && !translatedLine.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = translatedLine,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = config.otherLineSp,
                            textAlign = config.textAlign
                        )
                    }
                }
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
                    contentDescription = stringResource(R.string.playback_cd_collapse),
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.playback_fullscreen_lyrics),
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
                        contentDescription = stringResource(R.string.playback_cd_jump_to_current),
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
                                contentDescription = stringResource(R.string.playback_cd_play),
                                tint = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Shuffle + Repeat — smaller buttons in expanded view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeToggleButton(
                        isActive = playbackOptions.isShuffling,
                        accent = accent,
                        icon = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.playback_cd_shuffle),
                        iconSize = 22.dp,
                        onClick = onToggleShuffle
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    ModeToggleButton(
                        isActive = playbackOptions.repeatMode != RepeatMode.OFF,
                        accent = accent,
                        icon = if (playbackOptions.repeatMode == RepeatMode.TRACK)
                            Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.playback_cd_repeat),
                        iconSize = 22.dp,
                        onClick = onCycleRepeat
                    )
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

@Composable
private fun stateLabel(state: SpotifyConnectionState): String = when (state) {
    is SpotifyConnectionState.Disconnected -> stringResource(R.string.playback_disconnected)
    is SpotifyConnectionState.Connecting -> stringResource(R.string.playback_connecting)
    is SpotifyConnectionState.Connected -> stringResource(R.string.playback_connected)
    is SpotifyConnectionState.Error -> stringResource(R.string.playback_error)
    SpotifyConnectionState.SpotifyNotInstalled -> stringResource(R.string.playback_not_installed)
    SpotifyConnectionState.SpotifyNotLoggedIn -> stringResource(R.string.playback_not_logged_in)
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
    translatedLine: String?,
    isTranslationEnabled: Boolean,
    playbackOptions: PlaybackOptions,
    isSpotifyInstalled: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onConnect: () -> Unit,
    onOpenSpotify: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenLyricsCorrection: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onLyricDisplaySettings: () -> Unit
) {
    val dominant = palette.deep
    val secondary = palette.mid
    val accent = palette.accent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(colors = listOf(dominant, secondary))
            )
    ) {
        // Top-right: settings gear
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
                .size(42.dp),
            shape = CircleShape,
            glassAlpha = 0.12f,
            borderAlpha = 0.20f
        ) {
            IconButton(onClick = onLyricDisplaySettings, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.lyric_settings_title), tint = Color.White.copy(alpha = 0.9f))
            }
        }

        // Center: scrollable full lyrics list (uses portrait's ImmersiveLyricsBlock)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 100.dp)   // leave room for the bottom bar
        ) {
            if (!isConnected) {
                ConnectActionPanel(
                    state = connectionState,
                    accent = accent,
                    isSpotifyInstalled = isSpotifyInstalled,
                    onConnect = onConnect,
                    onOpenSpotify = onOpenSpotify,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 48.dp, end = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ImmersiveLyricsBlock(
                        currentLine = currentLyricLine,
                        allLines = parsedLyrics,
                        status = lyricStatus,
                        translatedLine = translatedLine,
                        isTranslationEnabled = isTranslationEnabled,
                        isPlaying = isPlaying,
                        positionMs = estimatedPositionMs,
                        config = LyricDisplayPreferences.resolvedConfig(),
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom bar: left=cover+info, center=progress bar (compact), right=transport
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp)
                .height(64.dp)
        ) {
            // Progress bar — geometrically centered on screen
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(260.dp)
                    .height(48.dp)
            ) {
                ImmersiveSeekControl(
                    estimatedPositionMs = estimatedPositionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Left: small cover + title/artist
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onOpenPlaylist() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    albumArt?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = stringResource(R.string.album_art_description),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.widthIn(max = 300.dp)) {
                    Text(
                        text = trackInfo.title.ifEmpty { stringResource(R.string.playback_title_waiting) },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = listOf(trackInfo.artist, trackInfo.album).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right: transport controls
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    glassAlpha = 0.16f,
                    borderAlpha = 0.22f
                ) {
                    IconButton(onClick = onSkipPrevious, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.playback_cd_previous), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                GlassSurface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    glassAlpha = 0.20f,
                    borderAlpha = 0.26f
                ) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = isPlaying,
                                animationSpec = tween(durationMillis = 250)
                            ) { playing ->
                                if (playing) PauseGlyph(color = Color.White)
                                else Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.playback_cd_play), tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                    }
                }
                GlassSurface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    glassAlpha = 0.16f,
                    borderAlpha = 0.22f
                ) {
                    IconButton(onClick = onSkipNext, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.playback_cd_next), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricDisplaySettingsDialog(
    isTranslationEnabled: Boolean = false,
    onSetTranslationEnabled: (Boolean) -> Unit = {},
    targetTranslationLang: String = "zh",
    onSetTargetTranslationLang: (String) -> Unit = {},
    currentSource: String = "",
    onDismiss: () -> Unit
) {
    val currentFontSize by LyricDisplayPreferences.fontSize
    val currentBold by LyricDisplayPreferences.boldCurrentLine
    val currentDim by LyricDisplayPreferences.dimLevel
    val currentAlign by LyricDisplayPreferences.alignment

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

                // Current lyrics source
                if (currentSource.isNotEmpty()) {
                    val sourceLabel = when (currentSource) {
                                            "cache" -> stringResource(R.string.lyric_source_cache)
                                            "lrclib" -> stringResource(R.string.lyric_source_lrclib)
                                            "netease" -> stringResource(R.string.lyric_source_netease)
                                            "qqmusic" -> stringResource(R.string.lyric_source_qqmusic)
                                            "manual" -> stringResource(R.string.lyric_source_manual)
                                            else -> currentSource
                                        }
                    Text(
                        text = stringResource(R.string.lyric_source_label, sourceLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Font size sliders
                val currentSp = LyricDisplayPreferences.fontSizeCurrent.value
                val otherSp = LyricDisplayPreferences.fontSizeOther.value

                Column {
                    Text(
                        text = stringResource(R.string.lyric_settings_font_size_current, "${currentSp.toInt()}sp"),
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
                        text = stringResource(R.string.lyric_settings_font_size_other, "${otherSp.toInt()}sp"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = otherSp,
                        onValueChange = { LyricDisplayPreferences.setFontSizeOther(it) },
                        valueRange = 12f..36f,
                        steps = 22
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.lyric_settings_bold),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.lyric_settings_bold_label), fontSize = 16.sp)
                        Switch(
                            checked = currentBold,
                            onCheckedChange = { LyricDisplayPreferences.setBoldCurrentLine(it) }
                        )
                    }
                }

                // Blur toggle for inactive lines
                val blurEnabled = LyricDisplayPreferences.blurEnabled.value
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.lyric_settings_inactive_blur), fontSize = 16.sp)
                        Switch(
                            checked = blurEnabled,
                            onCheckedChange = { LyricDisplayPreferences.setBlurEnabled(it) }
                        )
                    }
                }

                // Dim level is a sub-setting of blur: only visible when blur is on.
                    if (blurEnabled) {
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

                    Column {
                        Text(
                            text = stringResource(R.string.lyric_settings_alignment),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val alignLabels = listOf(
                                "center" to stringResource(R.string.lyric_settings_align_center),
                                "start" to stringResource(R.string.lyric_settings_align_left)
                            )
                            alignLabels.forEach { (value, label) ->
                                FilterChip(
                                    selected = currentAlign == value,
                                    onClick = { LyricDisplayPreferences.setAlignment(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                // Translation toggle
                Column {
                    Text(
                        text = stringResource(R.string.settings_group_translation),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_translate_lyrics),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.settings_translate_lyrics_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isTranslationEnabled,
                            onCheckedChange = onSetTranslationEnabled
                        )
                    }
                    if (isTranslationEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.settings_translate_target),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .clickable { expanded = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            when (targetTranslationLang) {
                                                "zh" -> stringResource(R.string.settings_translate_target_zh)
                                                "zh-TW" -> stringResource(R.string.settings_translate_target_tw)
                                                "en" -> stringResource(R.string.settings_translate_target_en)
                                                "ja" -> stringResource(R.string.settings_translate_target_ja)
                                                else -> targetTranslationLang
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    listOf(
                                        "zh" to stringResource(R.string.settings_translate_target_zh),
                                        "zh-TW" to stringResource(R.string.settings_translate_target_tw),
                                        "en" to stringResource(R.string.settings_translate_target_en),
                                        "ja" to stringResource(R.string.settings_translate_target_ja)
                                    ).forEach { (code, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onSetTargetTranslationLang(code)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Mobile data — show today's choice info
                val todayChoice = LyricDisplayPreferences.getTodayMobileDataChoice()
                if (todayChoice != null) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = stringResource(
                                if (todayChoice == "allow") R.string.mobile_data_settings_today_allow
                                else R.string.mobile_data_settings_today_deny
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lyric_settings_done)) }
        }
    )
}
