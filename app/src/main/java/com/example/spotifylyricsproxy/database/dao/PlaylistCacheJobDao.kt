package com.example.spotifylyricsproxy.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spotifylyricsproxy.database.entity.PlaylistCacheJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistCacheJobDao {

    @Query("SELECT * FROM playlist_cache_job ORDER BY playlist_name ASC")
    fun observeAll(): Flow<List<PlaylistCacheJobEntity>>

    @Query("SELECT * FROM playlist_cache_job ORDER BY playlist_name ASC")
    suspend fun getAll(): List<PlaylistCacheJobEntity>

    @Query("SELECT * FROM playlist_cache_job WHERE playlist_id = :playlistId")
    suspend fun getByPlaylistId(playlistId: String): PlaylistCacheJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaylistCacheJobEntity)

    @Query("DELETE FROM playlist_cache_job WHERE playlist_id = :playlistId")
    suspend fun delete(playlistId: String)

    @Query("UPDATE playlist_cache_job SET enabled = :enabled WHERE playlist_id = :playlistId")
    suspend fun setEnabled(playlistId: String, enabled: Boolean)
}
