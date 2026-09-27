package com.example.spotifylyricsproxy.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.core.AppSettings
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.spotify.remote.AlbumArtCache
import com.example.spotifylyricsproxy.ui.playback.LyricDisplayPreferences
import com.example.spotifylyricsproxy.ui.theme.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Grouped settings. Every row here is wired to a real preference or action;
 * nothing is displayed that the app does not actually honour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    val clearedMessage = stringResource(R.string.settings_clear_cache_done)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.correction_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Appearance
                GroupTitle(stringResource(R.string.settings_group_theme))
                SettingsGroup {
                    val currentMode by ThemePreferences.themeMode
                    SettingsRow(stringResource(R.string.settings_group_theme), stringResource(R.string.settings_theme_desc))
                    ChoiceRow(
                        options = listOf(
                            "system" to stringResource(R.string.settings_theme_system),
                            "light" to stringResource(R.string.settings_theme_light),
                            "dark" to stringResource(R.string.settings_theme_dark)
                        ),
                        selected = currentMode,
                        onSelect = ThemePreferences::setThemeMode
                    )
                }

                GroupTitle(stringResource(R.string.settings_group_language))
                SettingsGroup {
                    val currentLocale by ThemePreferences.locale
                    SettingsRow(stringResource(R.string.settings_group_language), stringResource(R.string.settings_language_desc))
                    ChoiceRow(
                        options = listOf(
                            "system" to stringResource(R.string.settings_language_system),
                            "zh" to stringResource(R.string.settings_language_zh),
                            "zh-TW" to stringResource(R.string.settings_language_tw),
                            "en" to stringResource(R.string.settings_language_en),
                            "ja" to stringResource(R.string.settings_language_ja)
                        ),
                        selected = currentLocale,
                        onSelect = { code -> if (ThemePreferences.setLocale(code)) (context as? Activity)?.recreate() }
                    )
                }

                // Network
                GroupTitle(stringResource(R.string.settings_group_network))
                SettingsGroup {
                    val strategy by LyricDisplayPreferences.mobileDataStrategy
                    SettingsRow(stringResource(R.string.settings_mobile_strategy), stringResource(R.string.settings_mobile_strategy_desc))
                    ChoiceRow(
                        options = listOf(
                            "ask" to stringResource(R.string.settings_mobile_ask),
                            "allow" to stringResource(R.string.settings_mobile_allow),
                            "deny" to stringResource(R.string.settings_mobile_deny)
                        ),
                        selected = strategy,
                        onSelect = LyricDisplayPreferences::setMobileDataStrategy
                    )
                }

                // Playback
                GroupTitle(stringResource(R.string.settings_group_playback))
                SettingsGroup {
                    val globalOffset by AppSettings.globalOffsetMs
                    SettingsRow(
                        title = stringResource(R.string.settings_global_offset),
                        subtitle = stringResource(R.string.settings_global_offset_desc),
                        trailing = {
                            Stepper(
                                valueMs = globalOffset,
                                onChange = { AppSettings.setGlobalOffsetMs(globalOffset + it) }
                            )
                        }
                    )
                    Divider()
                    SettingsRow(
                        title = stringResource(R.string.settings_notification_permission),
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            stringResource(if (permissionGranted) R.string.settings_notification_granted else R.string.settings_notification_denied)
                        } else {
                            stringResource(R.string.settings_notification_legacy)
                        },
                        onClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted) {
                            { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        } else null
                    )
                }

                // Storage
                GroupTitle(stringResource(R.string.settings_group_storage))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_desc),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showClearConfirm = true }
                    )
                }

                // About
                GroupTitle(stringResource(R.string.settings_group_about))
                SettingsGroup {
                    SettingsRow(stringResource(R.string.settings_about_app), stringResource(R.string.settings_about_version_detail))
                    Divider()
                    SettingsRow(
                        title = stringResource(R.string.settings_github),
                        subtitle = stringResource(R.string.settings_github_desc),
                        leading = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github_mark),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 14.dp).size(22.dp)
                            )
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        },
                        onClick = { openProjectPage(context) }
                    )
                    Divider()
                    SettingsRow(stringResource(R.string.settings_author), stringResource(R.string.settings_author_detail))
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).lyricCacheDao().deleteAll()
                            AlbumArtCache.getInstance(context).clear()
                        }
                        snackbar.showSnackbar(clearedMessage)
                    }
                }) {
                    Text(stringResource(R.string.settings_clear_cache_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.generic_cancel)) }
            }
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** Segmented choice; the selected pill's colour animates between options. */
@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tween(220), label = "choiceBg"
            )
            val fg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(220), label = "choiceFg"
            )
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** −/+ stepper in 0.1 s increments. */
@Composable
private fun Stepper(valueMs: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−") { onChange(-100) }
        Text(
            text = String.format(Locale.US, "%+.1f s", valueMs / 1000f).replace("+0.0", "0.0"),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 60.dp)
        )
        StepButton("+") { onChange(100) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
    }
}

private fun openProjectPage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // No browser/activity can handle the URL; keep the settings screen usable.
    }
}

private const val PROJECT_URL = "https://github.com/absswds/Spotify_lyric"
