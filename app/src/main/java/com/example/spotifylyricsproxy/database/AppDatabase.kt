package com.example.spotifylyricsproxy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.spotifylyricsproxy.database.dao.LyricCacheDao
import com.example.spotifylyricsproxy.database.dao.TrackPlayHistoryDao
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import com.example.spotifylyricsproxy.database.entity.TrackPlayHistoryEntity

@Database(
    entities = [LyricCacheEntity::class, TrackPlayHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lyricCacheDao(): LyricCacheDao
    abstract fun trackPlayHistoryDao(): TrackPlayHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spotify_lyrics.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
