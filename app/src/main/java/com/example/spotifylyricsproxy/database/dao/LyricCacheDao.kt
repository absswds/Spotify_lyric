package com.example.spotifylyricsproxy.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricCacheDao {

    @Query("SELECT * FROM lyric_cache WHERE spotify_track_id = :trackId")
    suspend fun getByTrackId(trackId: String): LyricCacheEntity?

    @Query("SELECT * FROM lyric_cache WHERE spotify_track_id = :trackId")
    fun observeByTrackId(trackId: String): Flow<LyricCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricCacheEntity)

    @Query("DELETE FROM lyric_cache WHERE spotify_track_id = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT COUNT(*) FROM lyric_cache")
    suspend fun count(): Int

    @Query("SELECT * FROM lyric_cache ORDER BY last_played_at DESC")
    fun observeAll(): Flow<List<LyricCacheEntity>>

    @Query("SELECT * FROM lyric_cache WHERE fetch_status = :status ORDER BY last_played_at DESC")
    fun observeByStatus(status: String): Flow<List<LyricCacheEntity>>
}
