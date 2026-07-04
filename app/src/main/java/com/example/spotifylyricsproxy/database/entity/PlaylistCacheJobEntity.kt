package com.example.spotifylyricsproxy.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_cache_job")
data class PlaylistCacheJobEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "playlist_name")
    val playlistName: String = "",

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "last_scan_at")
    val lastScanAt: Long? = null,

    @ColumnInfo(name = "total_tracks")
    val totalTracks: Int = 0,

    @ColumnInfo(name = "cached_tracks")
    val cachedTracks: Int = 0,

    @ColumnInfo(name = "failed_tracks")
    val failedTracks: Int = 0,

    @ColumnInfo(name = "not_found_tracks")
    val notFoundTracks: Int = 0
)
