package com.example.spotifylyricsproxy.ui.cache

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(viewModel: CacheViewModel) {
    val entries by viewModel.entries.collectAsState()
    val summary by viewModel.summary.collectAsState()

    val filterStatus by viewModel.filterStatus.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("缓存管理") }) },
        containerColor = Color(0xFFF7F8FC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SearchPlaceholder()
            Spacer(modifier = Modifier.height(12.dp))
            FilterRow(
                summary = summary,
                selectedFilter = filterStatus,
                onSelectFilter = viewModel::setFilter
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (entries.isEmpty()) {
                EmptyCacheState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.spotifyTrackId }) { entry ->
                        CacheEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEFF1F6)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = Color(0xFF7A8291)
            )
            Text(
                text = "搜索歌曲 / 歌手 / 专辑",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7A8291)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    summary: CacheSummary,
    selectedFilter: String?,
    onSelectFilter: (String?) -> Unit
) {
    val filters = listOf(
        null to "全部 ${summary.total}",
        "success" to "已缓存 ${summary.success}",
        "plain_only" to "仅文本 ${summary.plainOnly}",
        "not_found" to "未找到 ${summary.notFound}",
        "failed" to "失败 ${summary.failed}"
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (status, label) ->
            FilterChip(
                selected = selectedFilter == status,
                onClick = { onSelectFilter(status) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun CacheEntryCard(entry: LyricCacheEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIcon(entry.fetchStatus)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { "未知歌曲" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.artist.ifBlank { "未知歌手" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF747B89),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = statusLabel(entry.fetchStatus),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(entry.fetchStatus)
            )
        }
    }
}

@Composable
private fun StatusIcon(status: String) {
    val color = statusColor(status)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (status == "success") Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun EmptyCacheState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFEFF1F6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color(0xFF747B89),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无歌词缓存",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "播放或预缓存歌曲后，歌词缓存状态会显示在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF747B89),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun statusLabel(status: String): String =
    when (status) {
        "success" -> "已缓存"
        "plain_only" -> "仅文本"
        "not_found" -> "未找到"
        "failed" -> "失败"
        else -> status
    }

private fun statusColor(status: String): Color =
    when (status) {
        "success" -> Color(0xFF34C759)
        "plain_only" -> Color(0xFFFF9500)
        "not_found" -> Color(0xFF747B89)
        "failed" -> Color(0xFFFF3B30)
        else -> Color(0xFF747B89)
    }
