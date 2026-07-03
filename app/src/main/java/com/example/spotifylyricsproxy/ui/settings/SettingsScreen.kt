package com.example.spotifylyricsproxy.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
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
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsGroup(
                title = "权限",
                items = listOf(
                    SettingsItem(
                        label = "通知权限",
                        description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (permissionGranted) "已授权" else "未授权 — 点击申请"
                        } else {
                            "Android 12 及以下无需额外申请"
                        },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        }
                    )
                )
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "更多设置将在后续 MVP 中添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    items: List<SettingsItem>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        items.forEach { item ->
            SettingsItemRow(item)
        }
    }
}

@Composable
private fun SettingsItemRow(item: SettingsItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled) { item.onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.enabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.enabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class SettingsItem(
    val label: String,
    val description: String,
    val enabled: Boolean = false,
    val onClick: () -> Unit = {}
)
