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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotifylyricsproxy.R
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(viewModel: CacheViewModel) {
    val entries by viewModel.entries.collectAsState()
    val summary by viewModel.summary.collectAsState()

    val filterStatus by viewModel.filterStatus.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.cache_title)) }) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    summary: CacheSummary,
    selectedFilter: String?,
    onSelectFilter: (String?) -> Unit
) {
    val filters = listOf(
        null to "${stringResource(R.string.cache_filter_all)} ${summary.total}",
        "success" to "${stringResource(R.string.cache_filter_synced)} ${summary.success}",
        "plain_only" to "${stringResource(R.string.cache_filter_plain)} ${summary.plainOnly}",
        "not_found" to "${stringResource(R.string.cache_filter_not_found)} ${summary.notFound}",
        "failed" to "${stringResource(R.string.cache_filter_failed_fmt, summary.failed)}"
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIcon(entry.fetchStatus)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title.ifBlank { stringResource(R.string.generic_unknown_track) },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.source == "manual") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.cache_source_manual),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = entry.artist.ifBlank { stringResource(R.string.generic_unknown_artist) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.cache_empty_all),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.cache_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun statusLabel(status: String): String =
    when (status) {
        "success" -> stringResource(R.string.cache_filter_synced)
        "plain_only" -> stringResource(R.string.cache_filter_plain)
        "not_found" -> stringResource(R.string.cache_filter_not_found)
        "failed" -> stringResource(R.string.cache_filter_failed)
        else -> status
    }

@Composable
private fun statusColor(status: String): Color =
    when (status) {
        "success" -> Color(0xFF34C759)
        "plain_only" -> Color(0xFFFF9500)
        "not_found" -> MaterialTheme.colorScheme.onSurfaceVariant
        "failed" -> Color(0xFFFF3B30)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
