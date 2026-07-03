package com.example.spotifylyricsproxy.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spotifylyricsproxy.database.entity.TrackPlayHistoryEntity

@Dao
interface TrackPlayHistoryDao {

    @Query("SELECT * FROM track_play_history WHERE spotify_track_id = :trackId")
    suspend fun getByTrackId(trackId: String): TrackPlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackPlayHistoryEntity)

    @Query("UPDATE track_play_history SET play_count = play_count + 1, last_played_at = :now WHERE spotify_track_id = :trackId")
    suspend fun incrementPlayCount(trackId: String, now: Long = System.currentTimeMillis())
}
