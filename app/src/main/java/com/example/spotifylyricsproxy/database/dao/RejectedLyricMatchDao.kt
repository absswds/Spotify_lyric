package com.example.spotifylyricsproxy.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spotifylyricsproxy.database.entity.RejectedLyricMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RejectedLyricMatchDao {

    @Query("SELECT source_lyric_id FROM rejected_lyric_matches WHERE spotify_track_id = :trackId")
    suspend fun getRejectedSourceLyricIds(trackId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: RejectedLyricMatchEntity)

    @Query("SELECT * FROM rejected_lyric_matches WHERE spotify_track_id = :trackId")
    fun observeByTrackId(trackId: String): Flow<List<RejectedLyricMatchEntity>>

    @Query("DELETE FROM rejected_lyric_matches WHERE spotify_track_id = :trackId AND source_lyric_id = :sourceLyricId")
    suspend fun remove(trackId: String, sourceLyricId: String)

    @Query("DELETE FROM rejected_lyric_matches WHERE spotify_track_id = :trackId")
    suspend fun clearForTrack(trackId: String)
}
