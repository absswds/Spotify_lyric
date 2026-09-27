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
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.notification.NotificationPermissionPolicy
import kotlinx.coroutines.launch

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
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2735), Color(0xFF0B0F18))))
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp)
                    ) {
                        if (page == 0) WelcomePage() else PermissionsPage()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PagerDots(count = 2, current = pager.currentPage)
                Spacer(modifier = Modifier.weight(1f))
                val last = pager.currentPage == 1
                Text(
                    text = stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .clickable {
                            if (last) onFinish() else scope.launch { pager.animateScrollToPage(1) }
                        }
                        .padding(horizontal = 26.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(24.dp))
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Accent, Color(0xFF0E8F43)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Lyrics, contentDescription = null, tint = Color.Black, modifier = Modifier.size(38.dp))
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(stringResource(R.string.app_name), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    Text(stringResource(R.string.onboarding_tagline), color = InkDim, fontSize = 16.sp, lineHeight = 22.sp)
    Spacer(modifier = Modifier.height(28.dp))
    Feature(Icons.Filled.Sync, R.string.onboarding_feature_sync)
    Feature(Icons.Filled.MusicNote, R.string.onboarding_feature_words)
    Feature(Icons.Filled.PeopleAlt, R.string.onboarding_feature_duet)
    Feature(Icons.Filled.Translate, R.string.onboarding_feature_translate)
    Feature(Icons.Filled.OfflinePin, R.string.onboarding_feature_offline)
    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier
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
private fun PermissionsPage() {
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

    PermissionCard(
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
        icon = Icons.Filled.OfflinePin,
        title = R.string.onboarding_perm_listener,
        desc = R.string.onboarding_perm_listener_desc,
        granted = listenerGranted
    ) {
        NotificationPermissionPolicy.promptNotificationListenerAccess(context)
    }
    // Not detectable by apps: shown as an action only.
    PermissionCard(
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
private fun PermissionCard(icon: ImageVector, title: Int, desc: Int, granted: Boolean?, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (granted == true) Accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.07f),
        label = "permTint"
    )
    Row(
        modifier = Modifier
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
