package com.example.spotifylyricsproxy.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.ui.theme.ThemePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.correction_back)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_theme)) {
                Text(
                    text = stringResource(R.string.settings_theme_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val currentMode by ThemePreferences.themeMode
                    listOf(
                        Triple("system", stringResource(R.string.settings_theme_system), stringResource(R.string.settings_theme_system_desc)),
                        Triple("light", stringResource(R.string.settings_theme_light), stringResource(R.string.settings_theme_light_desc)),
                        Triple("dark", stringResource(R.string.settings_theme_dark), stringResource(R.string.settings_theme_dark_desc))
                    ).forEach { (mode, label, _) ->
                        FilterChip(
                            selected = currentMode == mode,
                            onClick = { ThemePreferences.setThemeMode(mode) },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            // Language section
            SettingsGroup(title = stringResource(R.string.settings_group_language)) {
                Text(
                    text = stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val currentLocale by ThemePreferences.locale
                    listOf(
                        "system" to stringResource(R.string.settings_language_system),
                        "zh" to stringResource(R.string.settings_language_zh),
                        "zh-TW" to stringResource(R.string.settings_language_tw),
                        "en" to stringResource(R.string.settings_language_en),
                        "ja" to stringResource(R.string.settings_language_ja)
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = currentLocale == code,
                            onClick = {
                                val changed = ThemePreferences.setLocale(code)
                                if (changed) {
                                    (context as? Activity)?.recreate()
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_cache)) {
                ToggleRow(stringResource(R.string.settings_auto_cache), stringResource(R.string.settings_auto_cache_desc), true)
                ToggleRow(stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_desc), true)
                ToggleRow(stringResource(R.string.settings_cache_album_art), stringResource(R.string.settings_cache_album_art_desc), true)
            }

            SettingsGroup(title = stringResource(R.string.settings_group_playback)) {
                ToggleRow(stringResource(R.string.settings_mediasession_proxy), stringResource(R.string.settings_mediasession_proxy_desc), true)
                ToggleRow(stringResource(R.string.settings_notification_lyrics), stringResource(R.string.settings_notification_lyrics_desc), true)
                Text(stringResource(R.string.settings_global_offset), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Slider(value = 0.5f, onValueChange = {})
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_offset_early), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.settings_offset_late), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_permission)) {
                SettingsItemRow(
                    label = stringResource(R.string.settings_notification_permission),
                    description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (permissionGranted) stringResource(R.string.settings_notification_granted) else stringResource(R.string.settings_notification_denied)
                    } else {
                        stringResource(R.string.settings_notification_legacy)
                    },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_storage)) {
                SettingsItemRow(stringResource(R.string.settings_clear_cache), stringResource(R.string.settings_clear_cache_desc))
                SettingsItemRow(stringResource(R.string.scan_lyrics_folder), stringResource(R.string.scan_lyrics_folder_desc))
            }

            SettingsGroup(title = stringResource(R.string.settings_group_about)) {
                SettingsItemRow(
                    label = stringResource(R.string.settings_about_app),
                    description = stringResource(R.string.settings_about_version_detail)
                )
                SettingsItemRow(
                    label = stringResource(R.string.settings_github),
                    description = stringResource(R.string.settings_github_desc),
                    iconRes = R.drawable.ic_github_mark,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    enabled = true,
                    onClick = { openProjectPage(context) }
                )
                SettingsItemRow(
                    label = stringResource(R.string.settings_author),
                    description = stringResource(R.string.settings_author_detail)
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val titleColor = if (isDarkTheme) Color(0xFF9CA7FF) else Color(0xFF4F5EDC)
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = {})
    }
}

@Composable
private fun SettingsItemRow(
    label: String,
    description: String,
    iconRes: Int? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
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
