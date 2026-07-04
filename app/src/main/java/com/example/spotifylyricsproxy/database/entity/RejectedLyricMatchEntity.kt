package com.example.spotifylyricsproxy.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rejected_lyric_matches",
    indices = [Index(value = ["spotify_track_id", "source_lyric_id"], unique = true)]
)
data class RejectedLyricMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "spotify_track_id")
    val spotifyTrackId: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "source_lyric_id")
    val sourceLyricId: String,

    @ColumnInfo(name = "reason")
    val reason: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
