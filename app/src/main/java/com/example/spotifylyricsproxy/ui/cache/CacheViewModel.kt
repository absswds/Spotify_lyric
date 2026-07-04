package com.example.spotifylyricsproxy.ui.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _allEntries = MutableStateFlow<List<LyricCacheEntity>>(emptyList())

    /** Currently active filter status, null = show all */
    private val _filterStatus = MutableStateFlow<String?>(null)
    val filterStatus: StateFlow<String?> = _filterStatus.asStateFlow()

    private val _entries = MutableStateFlow<List<LyricCacheEntity>>(emptyList())
    val entries: StateFlow<List<LyricCacheEntity>> = _entries.asStateFlow()

    private val _summary = MutableStateFlow(CacheSummary())
    val summary: StateFlow<CacheSummary> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            db.lyricCacheDao().observeAll().collect { rows ->
                _allEntries.value = rows
                _summary.value = summarizeCacheEntries(rows)
                applyFilter()
            }
        }
        // Re-filter whenever filterStatus changes
        viewModelScope.launch {
            _filterStatus.collect { applyFilter() }
        }
    }

    private fun applyFilter() {
        val filter = _filterStatus.value
        val all = _allEntries.value
        _entries.value = if (filter == null) all
        else all.filter { it.fetchStatus == filter }
    }

    fun setFilter(status: String?) {
        _filterStatus.value = status
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
