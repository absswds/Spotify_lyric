package com.example.spotifylyricsproxy.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.ui.playback.coachTarget
import com.example.spotifylyricsproxy.ui.playback.LyricDisplayPreferences
import com.example.spotifylyricsproxy.ui.playback.PlaybackViewModel
import java.util.Locale

private val SheetInk = Color(0xFFF7F5FA)
private val SheetInkDim = SheetInk.copy(alpha = 0.55f)

/**
 * Glass side sheet opened from the player's ··· button: now-playing summary, quick lyric
 * toggles, per-song timing, and entry points to every secondary page.
 */
@Composable
fun PlayerMenuSheet(
    visible: Boolean,
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
    onOpenPage: (NavRoute) -> Unit,
    onOpenLyricDisplay: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    val sheetWidth = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 400.dp else 330.dp
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 380f)) { it } + fadeIn(tween(160)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sheetWidth)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xF2221C2A), Color(0xF5141119))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(26.dp))
                    // Swallow taps on empty sheet space so they never reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuContent(viewModel, onOpenPage, onOpenLyricDisplay)
            }
        }
    }
}

@Composable
private fun ColumnScope.MenuContent(
    viewModel: PlaybackViewModel,
    onOpenPage: (NavRoute) -> Unit,
    onOpenLyricDisplay: () -> Unit
) {
    val track by viewModel.currentTrack.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val source by viewModel.lyricSource.collectAsState()
    val offsetMs by viewModel.currentOffsetMs.collectAsState()
    val alignment by LyricDisplayPreferences.alignment
    val blurEnabled by LyricDisplayPreferences.blurEnabled
    val wordByWord by LyricDisplayPreferences.wordByWord
    val lyricLines by viewModel.parsedLyrics.collectAsState()
    val hasWordTiming = remember(lyricLines) { lyricLines.any { it.words.isNotEmpty() } }

    // Now playing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            albumArt?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title.ifEmpty { stringResource(R.string.playback_title_waiting) },
                color = SheetInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (track.artist.isNotBlank()) {
                Text(track.artist, color = SheetInkDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val otherDevice by viewModel.otherDevicePlaying.collectAsState()
            if (otherDevice) {
                Text(stringResource(R.string.menu_other_device_sync), color = Color(0xFF8FE3B0), fontSize = 11.sp, maxLines = 1)
            }
        }
        sourceLabel(source)?.let { label ->
            Text(
                text = label,
                color = Color(0xFF8FE3B0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF8FE3B0).copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    // Quick toggles
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.coachTarget("menu_quick")) {
        // Shows the current alignment; each tap switches to the other one.
        val alignStart = alignment == "start"
        QuickTile(
            if (alignStart) Icons.AutoMirrored.Filled.FormatAlignLeft else Icons.Filled.FormatAlignCenter,
            stringResource(if (alignStart) R.string.lyric_settings_align_left else R.string.lyric_settings_align_center),
            active = true
        ) {
            LyricDisplayPreferences.setAlignment(if (alignStart) "center" else "start")
        }
        QuickTile(Icons.Filled.BlurOn, stringResource(R.string.menu_quick_blur), blurEnabled) {
            LyricDisplayPreferences.setBlurEnabled(!blurEnabled)
        }
        // Only offered when these lyrics really have word timing.
        if (hasWordTiming) {
            QuickTile(Icons.Filled.TextFields, stringResource(R.string.menu_quick_word_by_word), wordByWord) {
                LyricDisplayPreferences.setWordByWord(!wordByWord)
            }
        }
    }

    GroupTitle(stringResource(R.string.menu_group_lyrics))
    MenuGroup {
        Box(Modifier.coachTarget("menu_research")) {
            MenuRow(Icons.Filled.Search, Color(0xFFFF9F0A), stringResource(R.string.correction_research), stringResource(R.string.menu_research_desc)) {
                onOpenPage(NavRoute.LyricsCorrection)
            }
        }
        Box(Modifier.coachTarget("menu_offset")) {
            MenuRow(Icons.Filled.Timer, Color(0xFF5E5CE6), stringResource(R.string.menu_offset_title), stringResource(R.string.menu_offset_desc),
                trailing = { OffsetStepper(offsetMs, onChange = { viewModel.adjustOffset(it) }) },
                onClick = null
            )
        }
        Box(Modifier.coachTarget("menu_display")) {
            MenuRow(Icons.Filled.TextFields, Color(0xFF64D2FF), stringResource(R.string.nav_lyric_display), stringResource(R.string.menu_display_desc), onClick = onOpenLyricDisplay)
        }
    }

    GroupTitle(stringResource(R.string.menu_group_library))
    Box(Modifier.coachTarget("menu_library")) { MenuGroup {
        MenuRow(Icons.AutoMirrored.Filled.QueueMusic, Color(0xFF30D158), stringResource(R.string.nav_playlist), stringResource(R.string.menu_playlists_desc)) {
            onOpenPage(NavRoute.Playlist)
        }
        MenuRow(Icons.Filled.CloudDownload, Color(0xFF0A84FF), stringResource(R.string.nav_precache), stringResource(R.string.menu_precache_desc)) {
            onOpenPage(NavRoute.Precache)
        }
        MenuRow(Icons.Filled.Storage, Color(0xFF8E8E93), stringResource(R.string.nav_cache), stringResource(R.string.menu_cache_desc)) {
            onOpenPage(NavRoute.Cache)
        }
    } }

    Spacer(modifier = Modifier.height(2.dp))
    Box(Modifier.coachTarget("menu_settings")) { MenuGroup {
        MenuRow(Icons.Filled.Settings, Color(0xFF636366), stringResource(R.string.nav_settings), stringResource(R.string.menu_settings_desc)) {
            onOpenPage(NavRoute.Settings)
        }
    } }
}

@Composable
private fun sourceLabel(source: String): String? = when (source) {
    "" -> null
    "cache" -> stringResource(R.string.lyric_source_cache)
    "lrclib" -> stringResource(R.string.lyric_source_lrclib)
    "netease" -> stringResource(R.string.lyric_source_netease)
    "qqmusic" -> stringResource(R.string.lyric_source_qqmusic)
    "manual" -> stringResource(R.string.lyric_source_manual)
    else -> source
}

@Composable
private fun RowScope.QuickTile(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f), label = "tileScale")
    Column(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (active) 0.16f else 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (active) SheetInk else SheetInkDim)
        Text(label, color = if (active) SheetInk else SheetInkDim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        color = SheetInk.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp)
    )
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        content = content
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, color = SheetInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SheetInk.copy(alpha = 0.42f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SheetInk.copy(alpha = 0.3f))
        }
    }
}

/** −/+ in 0.1 s steps; positive = lyrics later. */
@Composable
internal fun OffsetStepper(offsetMs: Long, onChange: (Long) -> Unit, ink: Color = SheetInk) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", ink) { onChange(-100) }
        Text(
            text = String.format(Locale.US, "%+.1f s", offsetMs / 1000f).replace("+0.0", "0.0"),
            color = ink,
            fontSize = 13.sp,
            modifier = Modifier
                .width(58.dp)
                .padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepButton("+", ink) { onChange(100) }
    }
}

@Composable
private fun StepButton(label: String, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = ink, fontSize = 16.sp)
    }
}
