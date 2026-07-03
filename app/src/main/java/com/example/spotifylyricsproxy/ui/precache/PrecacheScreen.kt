package com.example.spotifylyricsproxy.ui.precache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrecacheScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("预缓存任务") }) },
        containerColor = Color(0xFFF7F8FC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            PlaylistSummary()
            Spacer(modifier = Modifier.height(14.dp))
            MetricsRow()
            Spacer(modifier = Modifier.height(14.dp))
            WorkerProgress()
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {}
                ) { Text("停止任务") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {}
                ) { Text("继续补齐") }
            }
        }
    }
}

@Composable
private fun PlaylistSummary() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "傍晚时分的旋律",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "共 128 首 · 创建于 2024-05-18",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF747D8C)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "67%",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF4F5EDC),
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { 0.67f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Color(0xFF4F5EDC),
                trackColor = Color(0xFFE4E7EF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("仅 Wi-Fi") })
                AssistChip(onClick = {}, label = { Text("充电时优先") })
            }
        }
    }
}

@Composable
private fun MetricsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("86", "已缓存", Color(0xFF1C8E55), Modifier.weight(1f))
        MetricCard("18", "仅文本", Color(0xFFC27A1A), Modifier.weight(1f))
        MetricCard("9", "失败", Color(0xFFD44747), Modifier.weight(1f))
        MetricCard("15", "未找到", Color(0xFF747D8C), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(value: String, label: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFF747D8C), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WorkerProgress() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("后台工作进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            WorkerRow("工作线程 1", "正在处理 32/43", 0.74f)
            WorkerRow("工作线程 2", "正在处理 28/43", 0.65f)
            WorkerRow("工作线程 3", "正在处理 26/42", 0.62f)
        }
    }
}

@Composable
private fun WorkerRow(title: String, subtitle: String, progress: Float) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF747D8C))
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF747D8C))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = Color(0xFF4F5EDC),
            trackColor = Color(0xFFE4E7EF)
        )
    }
}
