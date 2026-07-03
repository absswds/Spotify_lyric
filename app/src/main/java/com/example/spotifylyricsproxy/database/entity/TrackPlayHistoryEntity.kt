package com.example.spotifylyricsproxy.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_play_history")
data class TrackPlayHistoryEntity(
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

    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "play_count")
    val playCount: Int = 1
)
