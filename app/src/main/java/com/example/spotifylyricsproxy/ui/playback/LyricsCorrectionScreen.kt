package com.example.spotifylyricsproxy.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.spotifylyricsproxy.core.model.LyricCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsCorrectionScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentLine by viewModel.currentLyricLine.collectAsState()
    val parsedLyrics by viewModel.parsedLyrics.collectAsState()
    val lyricStatus by viewModel.lyricStatus.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val currentOffsetMs by viewModel.currentOffsetMs.collectAsState()
    val showCandidatePicker by viewModel.showCandidatePicker.collectAsState()

    // Candidate picker dialog
    if (showCandidatePicker && candidates.size > 1) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCandidatePicker,
            title = {
                Text("选择候选歌词", fontWeight = FontWeight.SemiBold)
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates.size) { index ->
                        val c = candidates[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectCandidate(index) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == 0) Color(0xFF4F5EDC).copy(alpha = 0.12f)
                                else Color.White
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "候选 ${index + 1}: ${c.trackName}",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${c.artistName} · ${c.score}分",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF747B89)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCandidatePicker) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌词修正") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = Color(0xFFF7F8FC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Current song section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentTrack.title.ifEmpty { "未播放" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = currentTrack.artist.ifEmpty { "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF747B89)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current lyrics
                    val lineText = currentLine?.text ?: "暂无歌词"
                    Text(
                        text = "当前歌词行: $lineText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentLine != null) Color(0xFF27314A) else Color(0xFF747B89)
                    )
                    if (currentLine != null) {
                        Text(
                            text = "${parsedLyrics.size} 行同步歌词",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF747B89)
                        )
                    }

                    // Status
                    Spacer(modifier = Modifier.height(8.dp))
                    lyricStatus?.let { status ->
                        Text(
                            text = statusLabel(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF747B89)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Offset controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "歌词偏移",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (currentOffsetMs != 0L) {
                        Text(
                            text = "当前偏移: ${currentOffsetMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9500)
                        )
                    } else {
                        Text(
                            text = "无偏移",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF747B89)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Offset grid
                    Text(
                        text = "提前（歌词来得太晚时使用）",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF747B89)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OffsetChip("-0.5s") { viewModel.adjustOffset(-500) }
                        OffsetChip("-0.2s") { viewModel.adjustOffset(-200) }
                        OffsetChip("-0.1s") { viewModel.adjustOffset(-100) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "推迟（歌词来得太早时使用）",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF747B89)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OffsetChip("+0.1s") { viewModel.adjustOffset(100) }
                        OffsetChip("+0.2s") { viewModel.adjustOffset(200) }
                        OffsetChip("+0.5s") { viewModel.adjustOffset(500) }
                    }

                    if (currentOffsetMs != 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "重置偏移",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF4F5EDC),
                            modifier = Modifier.clickable { viewModel.resetOffset() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "操作",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = viewModel::reSearchLyrics,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF27314A))
                    ) {
                        Text("重新搜索歌词")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (lyricStatus is com.example.spotifylyricsproxy.lyrics.LyricStatus.Synced) {
                        OutlinedButton(
                            onClick = viewModel::rejectCurrentMatch,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30))
                        ) {
                            Text("标记为错误歌曲")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (candidates.size > 1) {
                        OutlinedButton(
                            onClick = viewModel::showCandidateSelection,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF27314A))
                        ) {
                            Text("查看候选列表 (${candidates.size} 个)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEFF1F6),
        contentColor = Color(0xFF27314A),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusLabel(status: com.example.spotifylyricsproxy.lyrics.LyricStatus): String = when (status) {
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Idle -> "等待播放"
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Searching -> "搜索中..."
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Synced -> "已匹配 (${status.score}分)"
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.PlainOnly -> "仅文本歌词"
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.NotFound -> "未找到歌词"
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.LowConfidence -> "低分匹配 (${status.score}分)"
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.ParseError -> "解析失败"
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Error -> "错误: ${status.message}"
}
