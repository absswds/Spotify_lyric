package com.example.spotifylyricsproxy.lyrics.netease

import com.google.gson.annotations.SerializedName

/**
 * Netease search response data models.
 * The legacy endpoint uses artists/album/duration while cloudsearch/pc
 * uses ar/al/dt, so each field accepts both response shapes.
 */
data class NeteaseSearchResponse(
    val code: Long = -1,
    val result: NeteaseSearchResult? = null
)

data class NeteaseSearchResult(
    val songs: List<NeteaseSong>? = null,
    val songCount: Int = 0
)

data class NeteaseSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<NeteaseArtist>? = null,
    @SerializedName("ar") val cloudArtists: List<NeteaseArtist>? = null,
    val album: NeteaseAlbum? = null,
    @SerializedName("al") val cloudAlbum: NeteaseAlbum? = null,
    val duration: Long = 0,
    @SerializedName("dt") val cloudDuration: Long = 0
) {
    val resolvedArtists: List<NeteaseArtist>
        get() = artists?.takeIf { it.isNotEmpty() } ?: cloudArtists.orEmpty()
    val resolvedAlbum: NeteaseAlbum?
        get() = album ?: cloudAlbum
    val resolvedDuration: Long
        get() = duration.takeIf { it > 0 } ?: cloudDuration
}

data class NeteaseArtist(
    val id: Long = 0,
    val name: String = ""
)

data class NeteaseAlbum(
    val id: Long = 0,
    val name: String = ""
)

/**
 * Netease lyric response data model.
 * Matches: GET https://music.163.com/api/song/lyric
 */
data class NeteaseLyricResponse(
    val code: Long = -1,
    val lrc: NeteaseLrc? = null,
    val tlyric: NeteaseLrc? = null  // translation
)

data class NeteaseLrc(
    val version: Int = 0,
    val lyric: String = ""
)
