package com.example.spotifylyricsproxy.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import com.example.spotifylyricsproxy.database.entity.PlaylistCacheJobEntity
import com.example.spotifylyricsproxy.lyrics.LyricMatcher
import com.example.spotifylyricsproxy.lyrics.lrclib.LrclibLyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiClient
import com.example.spotifylyricsproxy.spotify.webapi.SpotifyWebApiAuth

class PlaylistPrecacheWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PrecacheWorker"
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_PLAYLIST_NAME = "playlist_name"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_PROGRESS = "progress"
        const val KEY_CACHED = "cached"
        const val KEY_FAILED = "failed"
        const val KEY_NOT_FOUND = "not_found"
        const val KEY_TOTAL = "total"
    }

    override suspend fun doWork(): Result {
        val playlistId = inputData.getString(KEY_PLAYLIST_ID) ?: return Result.failure()
        val playlistName = inputData.getString(KEY_PLAYLIST_NAME) ?: ""
        val accessToken = inputData.getString(KEY_ACCESS_TOKEN) ?: return Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        val cacheDao = db.lyricCacheDao()
        val playlistDao = db.playlistCacheJobDao()
        val lyricsSource = LrclibLyricsSource()
        val api = SpotifyWebApiClient.api
        val authHeader = "Bearer $accessToken"

        var cached = 0
        var failed = 0
        var notFound = 0

        return try {
            Log.i(TAG, "Starting precache for playlist=$playlistName ($playlistId)")
            setProgress(workDataOf(KEY_PLAYLIST_ID to playlistId, KEY_PROGRESS to 0))

            // Fetch all tracks from playlist
            val allTracks = mutableListOf<Pair<String, String>>() // trackId -> title, artist
            var offset = 0
            var totalTracks = 0

            do {
                Log.i(TAG, "Fetching tracks offset=$offset for playlist=$playlistId")
                val response = api.getPlaylistTracks(authHeader, playlistId, offset = offset)
                if (totalTracks == 0) totalTracks = response.total
                Log.i(TAG, "Got ${response.items.size} items, total=$totalTracks")

                response.items.forEach { item ->
                    item.track?.let { track ->
                        val artistNames = track.artists.joinToString(", ") { it.name }
                        allTracks.add(track.id to "${track.name}||$artistNames||${track.durationMs}")
                    } ?: Log.w(TAG, "Item has null track")
                }
                offset += 100
            } while (offset < totalTracks && allTracks.isNotEmpty())

            Log.i(TAG, "Total tracks fetched: ${allTracks.size}")

            if (allTracks.isEmpty()) {
                Log.w(TAG, "No tracks fetched, saving empty job")
                updatePlaylistJob(playlistDao, playlistId, playlistName, 0, 0, 0, 0)
                return Result.success()
            }

            // Process each track
            allTracks.forEachIndexed { index, (trackId, metadata) ->
                if (isStopped) return Result.success()

                val parts = metadata.split("||")
                val title = parts.getOrElse(0) { "" }
                val artist = parts.getOrElse(1) { "" }
                val durationMs = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0

                // Skip if already cached
                val existing = cacheDao.getByTrackId(trackId)
                if (existing != null && existing.fetchStatus == "success") {
                    cached++
                } else {
                    try {
                        val candidates = lyricsSource.search(
                            LyricsSearchRequest(title, artist, "", durationMs)
                        )
                        if (candidates.isNotEmpty()) {
                            val best = candidates.sortedByDescending {
                                LyricMatcher.score(it, title, artist, "", durationMs).score
                            }.first()
                            val scored = LyricMatcher.score(best, title, artist, "", durationMs)
                            val synced = scored.syncedLyrics
                            val status = when {
                                !synced.isNullOrEmpty() -> "success"
                                !scored.plainLyrics.isNullOrEmpty() -> "plain_only"
                                else -> "not_found"
                            }
                            cacheDao.upsert(
                                LyricCacheEntity(
                                    spotifyTrackId = trackId,
                                    title = title,
                                    artist = artist,
                                    durationMs = durationMs,
                                    source = "lrclib",
                                    syncedLyrics = synced,
                                    plainLyrics = scored.plainLyrics,
                                    fetchStatus = status,
                                    confidenceScore = scored.score
                                )
                            )
                            if (status == "success") cached++
                            else notFound++
                        } else {
                            cacheDao.upsert(
                                LyricCacheEntity(
                                    spotifyTrackId = trackId,
                                    title = title,
                                    artist = artist,
                                    durationMs = durationMs,
                                    fetchStatus = "not_found",
                                    nextRetryAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                                )
                            )
                            notFound++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed for $title", e)
                        failed++
                    }
                }

                // Update progress
                val progress = ((index + 1) * 100) / allTracks.size
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to progress,
                        KEY_CACHED to cached,
                        KEY_FAILED to failed,
                        KEY_NOT_FOUND to notFound,
                        KEY_TOTAL to totalTracks,
                        KEY_PLAYLIST_ID to playlistId
                    )
                )
            }

            // Save final stats
            updatePlaylistJob(playlistDao, playlistId, playlistName, totalTracks, cached, failed, notFound)
            Log.i(TAG, "Precache complete: $cached cached, $notFound not found, $failed failed")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Precache failed", e)
            Result.retry()
        }
    }

    private suspend fun updatePlaylistJob(
        dao: com.example.spotifylyricsproxy.database.dao.PlaylistCacheJobDao,
        playlistId: String,
        playlistName: String,
        total: Int,
        cached: Int,
        failed: Int,
        notFound: Int
    ) {
        dao.upsert(
            PlaylistCacheJobEntity(
                playlistId = playlistId,
                playlistName = playlistName,
                totalTracks = total,
                cachedTracks = cached,
                failedTracks = failed,
                notFoundTracks = notFound,
                lastScanAt = System.currentTimeMillis()
            )
        )
    }
}
