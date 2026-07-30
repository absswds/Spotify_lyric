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
import androidx.compose.ui.res.stringResource
import com.example.spotifylyricsproxy.R

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
                Text(stringResource(R.string.correction_candidate_title), fontWeight = FontWeight.SemiBold)
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
                                    text = stringResource(R.string.correction_candidate_format, index + 1, c.trackName),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.correction_candidate_subtitle, c.artistName, c.score),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF747B89)
                                )
                                // Show lyrics source provider
                                Text(
                                    text = "📡 " + sourceDisplayName(c.source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9AA0A6),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCandidatePicker) {
                    Text(stringResource(R.string.generic_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.correction_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.correction_back))
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
                        text = currentTrack.title.ifEmpty { stringResource(R.string.correction_no_track) },
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
                    val lineText = currentLine?.text ?: stringResource(R.string.correction_status_no_lyrics)
                    Text(
                        text = stringResource(R.string.correction_current_line_label, lineText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentLine != null) Color(0xFF27314A) else Color(0xFF747B89)
                    )
                    if (currentLine != null) {
                        Text(
                            text = stringResource(R.string.correction_line_count_format, parsedLyrics.size),
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
                        text = stringResource(R.string.correction_offset_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (currentOffsetMs != 0L) {
                        Text(
                            text = "${stringResource(R.string.correction_current_offset_label)}: ${currentOffsetMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9500)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.correction_no_offset),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF747B89)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Offset grid
                    Text(
                        text = stringResource(R.string.correction_advance_label),
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
                        text = stringResource(R.string.correction_delay_label),
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
                            text = stringResource(R.string.correction_reset_offset),
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
                        text = stringResource(R.string.correction_actions_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = viewModel::reSearchLyrics,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF27314A))
                    ) {
                        Text(stringResource(R.string.correction_research))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (lyricStatus is com.example.spotifylyricsproxy.lyrics.LyricStatus.Synced) {
                        OutlinedButton(
                            onClick = viewModel::rejectCurrentMatch,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30))
                        ) {
                            Text(stringResource(R.string.correction_mark_wrong))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (candidates.size > 1) {
                        OutlinedButton(
                            onClick = viewModel::showCandidateSelection,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF27314A))
                        ) {
                            Text(stringResource(R.string.correction_view_candidates, candidates.size))
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

@Composable
private fun statusLabel(status: com.example.spotifylyricsproxy.lyrics.LyricStatus): String = when (status) {
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Idle -> stringResource(R.string.playback_title_waiting)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Searching -> stringResource(R.string.correction_status_searching)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Synced -> stringResource(R.string.correction_status_synced, status.score)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.PlainOnly -> stringResource(R.string.correction_status_plain)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.NotFound -> stringResource(R.string.correction_status_notfound)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.LowConfidence -> stringResource(R.string.correction_status_low, status.score)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.ParseError -> stringResource(R.string.correction_status_parse_error)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.Error -> stringResource(R.string.correction_status_error, status.message)
    is com.example.spotifylyricsproxy.lyrics.LyricStatus.MobileDataRestricted -> stringResource(R.string.correction_status_mobile_restricted)
}

/** Map internal source name to user-facing display label. */
private fun sourceDisplayName(source: String): String = when (source) {
    "netease" -> "🎵 网易云"
    "qqmusic" -> "🎵 QQ音乐"
    "lrclib" -> "🌐 LRCLIB"
    "cache" -> "💾 缓存"
    "manual" -> "📝 手动导入"
    else -> source
}
