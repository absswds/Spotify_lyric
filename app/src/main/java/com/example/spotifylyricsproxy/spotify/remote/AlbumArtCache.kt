package com.example.spotifylyricsproxy.spotify.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/**
 * Two-tier album-art cache: an in-memory LRU keyed by trackId, backed by a
 * file cache in the app's cache directory. Avoids re-downloading artwork on
 * every track change and works offline once cached.
 *
 * File layout: <cacheDir>/album_art/<sha1(trackId)>.png
 */
class AlbumArtCache private constructor(context: Context) {

    private val cacheDir: File = File(context.cacheDir, "album_art").apply {
        if (!exists()) mkdirs()
    }

    // Reuse decoded bitmaps across track visits — keep up to ~12MB in memory.
    private val memory: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(trackId: String): Bitmap? {
        if (trackId.isBlank()) return null
        memory[trackId]?.let { return it }
        val file = fileFor(trackId).takeIf { it.exists() } ?: return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath).also { bmp ->
                if (bmp != null) memory.put(trackId, bmp)
            }
        }.getOrNull()
    }

    fun put(trackId: String, bitmap: Bitmap) {
        if (trackId.isBlank()) return
        memory.put(trackId, bitmap)
        val file = fileFor(trackId)
        runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    fun clear() {
        memory.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(trackId: String): File {
        val sha = MessageDigest.getInstance("SHA-1")
            .digest(trackId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$sha.png")
    }

    companion object {
        @Volatile private var INSTANCE: AlbumArtCache? = null
        fun getInstance(context: Context): AlbumArtCache =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlbumArtCache(context.applicationContext).also { INSTANCE = it }
            }
    }
}