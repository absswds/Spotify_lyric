package com.example.spotifylyricsproxy.ui.cache

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("缓存管理") }) },
        containerColor = Color(0xFFF7F8FC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SearchPlaceholder()
            Spacer(modifier = Modifier.height(12.dp))
            FilterRow()
            Spacer(modifier = Modifier.height(14.dp))
            sampleRows.forEach { item ->
                CacheSongRow(item)
                Spacer(modifier = Modifier.height(10.dp))
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

@Composable
private fun FilterRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("全部 128", "已缓存 86", "仅文本 18", "未找到 15", "失败 9").forEach {
            AssistChip(onClick = {}, label = { Text(it) })
        }
    }
}

@Composable
private fun CacheSongRow(item: CacheSongItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = item.color.copy(alpha = 0.16f)
            ) {}
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF77808F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusBadge(item.status, item.statusColor)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Refresh, contentDescription = "重新下载")
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = color
        ) {}
        Text(
            text = text,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private data class CacheSongItem(
    val title: String,
    val artist: String,
    val status: String,
    val statusColor: Color,
    val color: Color
)

private val sampleRows = listOf(
    CacheSongItem("今晚的风经过窗边", "城市信号 · 黄昏日记", "已缓存", Color(0xFF1C8E55), Color(0xFF5967D8)),
    CacheSongItem("星空下的约定", "蓝色电台", "仅文本", Color(0xFFC27A1A), Color(0xFF355C7D)),
    CacheSongItem("回忆里的晴天", "微光合唱团", "未找到", Color(0xFF777F8D), Color(0xFFB8C0CC)),
    CacheSongItem("雨后地铁站", "浅色清晨", "失败", Color(0xFFD44747), Color(0xFFD85C5C))
)
