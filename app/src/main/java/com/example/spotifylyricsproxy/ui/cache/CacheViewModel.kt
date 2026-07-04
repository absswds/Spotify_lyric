package com.example.spotifylyricsproxy.ui.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CacheSummary(
    val total: Int = 0,
    val success: Int = 0,
    val plainOnly: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0
)

class CacheViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    private val _entries = MutableStateFlow<List<LyricCacheEntity>>(emptyList())
    val entries: StateFlow<List<LyricCacheEntity>> = _entries.asStateFlow()

    private val _summary = MutableStateFlow(CacheSummary())
    val summary: StateFlow<CacheSummary> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            db.lyricCacheDao().observeAll().collect { rows ->
                _entries.value = rows
                _summary.value = summarizeCacheEntries(rows)
            }
        }
    }
}

fun summarizeCacheEntries(entries: List<LyricCacheEntity>): CacheSummary =
    CacheSummary(
        total = entries.size,
        success = entries.count { it.fetchStatus == "success" },
        plainOnly = entries.count { it.fetchStatus == "plain_only" },
        notFound = entries.count { it.fetchStatus == "not_found" },
        failed = entries.count { it.fetchStatus == "failed" }
    )
