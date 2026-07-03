package com.example.spotifylyricsproxy.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyric_cache")
data class LyricCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "spotify_track_id")
    val spotifyTrackId: String,

    @ColumnInfo(name = "spotify_uri")
    val spotifyUri: String = "",

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "artist")
    val artist: String = "",

    @ColumnInfo(name = "album")
    val album: String = "",

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "source")
    val source: String = "lrclib",

    @ColumnInfo(name = "synced_lyrics")
    val syncedLyrics: String? = null,

    @ColumnInfo(name = "plain_lyrics")
    val plainLyrics: String? = null,

    @ColumnInfo(name = "offset_ms")
    val offsetMs: Long = 0,

    @ColumnInfo(name = "fetch_status")
    val fetchStatus: String = "success",

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Int = 0,

    @ColumnInfo(name = "last_tried_at")
    val lastTriedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long? = null,

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
