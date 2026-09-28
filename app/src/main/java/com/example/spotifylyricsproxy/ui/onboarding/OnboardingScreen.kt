package com.example.spotifylyricsproxy.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalConfiguration
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.ui.playback.LyricDisplayPreferences
import com.example.spotifylyricsproxy.ui.settings.ChineseModePreview
import com.example.spotifylyricsproxy.notification.NotificationPermissionPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import com.example.spotifylyricsproxy.core.AppSettings

const val PROJECT_URL = "https://github.com/absswds/Spotify_lyric"
private const val SPOTIFY_PACKAGE = "com.spotify.music"

private val Ink = Color.White
private val InkDim = Color.White.copy(alpha = 0.62f)
private val Accent = Color(0xFF1ED760)

/**
 * First-launch guide: what the app does, where the source lives, and the optional
 * permissions that make it work better. Everything can be skipped.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    // Chinese UIs get an extra page for how Chinese lyrics meet a Chinese target.
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val pages = buildList {
        add("welcome")
        if (chinese) add("chinese")
        add("cache")
        add("permissions")
    }
    val pageCount = pages.size
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val aurora = rememberInfiniteTransition(label = "aurora")
    val auroraPhase = aurora.animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "auroraPhase"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2735), Color(0xFF0B0F18))))
            .drawBehind { drawAuroraBlobs(auroraPhase.value) }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = InkDim,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onFinish)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Neighbouring pages recede and fade while swiping.
                            val offset = kotlin.math.abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                            alpha = 1f - offset * 0.6f
                            val scale = 1f - offset * 0.08f
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp)
                    ) {
                        val visible = pager.settledPage == page
                        when (pages[page]) {
                            "welcome" -> WelcomePage(visible)
                            "chinese" -> ChineseModePage()
                            "cache" -> CacheConsentPage()
                            else -> PermissionsPage(visible)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PagerDots(count = pageCount, current = pager.currentPage)
                Spacer(modifier = Modifier.weight(1f))
                val last = pager.currentPage == pageCount - 1
                Text(
                    text = stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .clickable {
                            if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                        .padding(horizontal = 26.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ChineseModePage() {
    val mode by LyricDisplayPreferences.chineseConvertMode
    Spacer(modifier = Modifier.height(24.dp))
    Text(stringResource(R.string.settings_chinese_mode), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.settings_chinese_mode_desc), color = InkDim, fontSize = 15.sp, lineHeight = 21.sp)
    Spacer(modifier = Modifier.height(12.dp))
    ChineseModePreview(mode, lineColor = Ink, background = Color.White.copy(alpha = 0.07f))
    Spacer(modifier = Modifier.height(12.dp))
    listOf(
        "replace" to R.string.settings_chinese_replace,
        "below" to R.string.settings_chinese_below
    ).forEach { (value, label) ->
        val selected = mode == value
        Text(
            text = stringResource(label),
            color = if (selected) Color.Black else Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) Accent else Color.White.copy(alpha = 0.07f))
                .clickable { LyricDisplayPreferences.setChineseConvertMode(value) }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

/** Slowly drifting colour glows behind the guide, like the player's cover-sampled background. */
private fun DrawScope.drawAuroraBlobs(t: Float) {
    val glows = listOf(
        Triple(Color(0xFF1ED760), 0.25f, 0f),
        Triple(Color(0xFF5B6CFF), 0.75f, 2.1f),
        Triple(Color(0xFFE0569B), 0.5f, 4.2f)
    )
    glows.forEach { (color, x, phase) ->
        val center = Offset(
            size.width * (x + 0.18f * kotlin.math.sin(t + phase)),
            size.height * (0.35f + 0.22f * kotlin.math.cos(t * 0.8f + phase))
        )
        val radius = size.minDimension * 0.75f
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center, radius),
            radius = radius,
            center = center
        )
    }
}

/** Fades and lifts a block in, [index] steps after the page shows. */
@Composable
private fun Modifier.staggerIn(visible: Boolean, index: Int): Modifier {
    // Starts at 0 even when the page is visible from the first frame (the welcome page).
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, tween(560, delayMillis = 90 * index, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(0f)
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/**
 * A mock media-card capsule cycling through demo lines, showing the app's core idea
 * before any explanation: lyrics follow the song outside the app.
 */
@Composable
private fun LiveCapsuleDemo() {
    val lines = stringArrayResource(R.array.onboarding_demo_lines)
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            index = (index + 1) % lines.size
        }
    }
    val pulse = rememberInfiniteTransition(label = "capsule")
    val glow by pulse.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black)
            .border(1.dp, Accent.copy(alpha = glow * 0.5f), RoundedCornerShape(50))
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Color(0xFF0E8F43)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lyrics, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (slideInVertically(tween(420)) { it } + fadeIn(tween(420))) togetherWith
                    (slideOutVertically(tween(420)) { -it } + fadeOut(tween(300)))
            },
            label = "capsuleLine"
        ) { i ->
            Text(lines[i], color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun WelcomePage(visible: Boolean) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(24.dp))
    Box(modifier = Modifier.staggerIn(visible, 0)) { LiveCapsuleDemo() }
    Spacer(modifier = Modifier.height(24.dp))
    Text(stringResource(R.string.app_name), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.staggerIn(visible, 1))
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_tagline), color = InkDim, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.staggerIn(visible, 2))
    Spacer(modifier = Modifier.height(28.dp))
    listOf(
        Icons.Filled.Sync to R.string.onboarding_feature_sync,
        Icons.Filled.MusicNote to R.string.onboarding_feature_words,
        Icons.Filled.PeopleAlt to R.string.onboarding_feature_duet,
        Icons.Filled.Translate to R.string.onboarding_feature_translate,
        Icons.Filled.OfflinePin to R.string.onboarding_feature_offline
    ).forEachIndexed { i, (icon, text) ->
        Box(modifier = Modifier.staggerIn(visible, 3 + i)) { Feature(icon, text) }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier
            .staggerIn(visible, 8)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable { openUrl(context, PROJECT_URL) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Code, contentDescription = null, tint = InkDim, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(stringResource(R.string.onboarding_source), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(PROJECT_URL.removePrefix("https://"), color = Accent, fontSize = 13.sp)
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun Feature(icon: ImageVector, text: Int) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(stringResource(text), color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun CacheConsentPage() {
    val allowed by AppSettings.cacheUnofficial
    Spacer(modifier = Modifier.height(24.dp))
    Text(stringResource(R.string.cache_consent_title), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.cache_consent_message), color = InkDim, fontSize = 15.sp, lineHeight = 21.sp)
    Spacer(modifier = Modifier.height(16.dp))
    listOf(
        true to R.string.cache_consent_allow,
        false to R.string.cache_consent_deny
    ).forEach { (value, label) ->
        val selected = allowed == value
        val bg by animateColorAsState(if (selected) Accent else Color.White.copy(alpha = 0.07f), label = "cacheChoice")
        Text(
            text = stringResource(label),
            color = if (selected) Color.Black else Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable { AppSettings.setCacheUnofficial(value) }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun PermissionsPage(visible: Boolean) {
    val context = LocalContext.current
    // Re-read grants whenever we come back from a settings screen.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val notificationsGranted = remember(refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val listenerGranted = remember(refresh) { NotificationPermissionPolicy.hasNotificationListenerAccess(context) }

    Spacer(modifier = Modifier.height(24.dp))
    Text(stringResource(R.string.onboarding_permissions_title), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_permissions_desc), color = InkDim, fontSize = 15.sp, lineHeight = 21.sp)
    Spacer(modifier = Modifier.height(20.dp))

    val signedIn = remember(refresh) { com.example.spotifylyricsproxy.spotify.webapi.SpotifyTokenStore.hasCurrentScopes() }
    PermissionCard(
        modifier = Modifier.staggerIn(visible, 0),
        icon = Icons.Filled.MusicNote,
        title = R.string.onboarding_perm_spotify,
        desc = R.string.onboarding_perm_spotify_desc,
        granted = signedIn
    ) {
        com.example.spotifylyricsproxy.SpotifyAuthHolder.requestAuth()
    }
    PermissionCard(
        modifier = Modifier.staggerIn(visible, 1),
        icon = Icons.Filled.Notifications,
        title = R.string.onboarding_perm_notify,
        desc = R.string.onboarding_perm_notify_desc,
        granted = notificationsGranted
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    PermissionCard(
        modifier = Modifier.staggerIn(visible, 2),
        icon = Icons.Filled.OfflinePin,
        title = R.string.onboarding_perm_listener,
        desc = R.string.onboarding_perm_listener_desc,
        granted = listenerGranted
    ) {
        NotificationPermissionPolicy.promptNotificationListenerAccess(context)
    }
    // Not detectable by apps: shown as an action only.
    PermissionCard(
        modifier = Modifier.staggerIn(visible, 3),
        icon = Icons.Filled.Wifi,
        title = R.string.onboarding_perm_autostart,
        desc = R.string.onboarding_perm_autostart_desc,
        granted = null
    ) {
        openAutoStartSettings(context)
    }
    Spacer(modifier = Modifier.height(24.dp))
}

/** [granted] null means the state can't be read; the card always offers its action. */
@Composable
private fun PermissionCard(modifier: Modifier = Modifier, icon: ImageVector, title: Int, desc: Int, granted: Boolean?, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (granted == true) Accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.07f),
        label = "permTint"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint)
            .clickable(enabled = granted != true, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(stringResource(desc), color = InkDim, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (granted == true) {
            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.onboarding_granted), tint = Accent, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = stringResource(if (granted == null) R.string.onboarding_open_settings else R.string.onboarding_allow),
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, spring(dampingRatio = 0.7f), label = "dot")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Ink else Color.White.copy(alpha = 0.3f))
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

/**
 * Android has no standard screen for "auto-start / associated start"; each OEM ships its
 * own. Try the known screens and fall back to Spotify's app details, where ColorOS keeps
 * the toggle under battery usage. List follows judemanutd/AutoStarter.
 * ColorOS 14+'s own 关联启动 list (com.oplus.battery/...AssociateStartActivity) is
 * guarded by oplus.permission.OPLUS_COMPONENT_SAFE and cannot be opened by third-party apps.
 */
private val AUTO_START_SCREENS = listOf(
    // Older ColorOS
    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
    "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
    // MIUI / HyperOS
    "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
    // vivo / iQOO
    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
    "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
    // Huawei / Honor
    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    // Samsung
    "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity"
)

private fun openAutoStartSettings(context: Context) {
    for ((pkg, cls) in AUTO_START_SCREENS) {
        try {
            context.startActivity(
                Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) {
            // Not this vendor / not exported on this version: try the next one.
        }
    }
    openAppDetails(context, SPOTIFY_PACKAGE)
}

private fun openAppDetails(context: Context, packageName: String) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
