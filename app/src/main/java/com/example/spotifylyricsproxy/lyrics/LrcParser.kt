package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LrcLine

object LrcParser {

    // Matches [mm:ss.xx] or [mm:ss.xxx]
    private val LINE_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    fun parse(lrcText: String): List<LrcLine> {
        return lrcText.lines()
            .mapNotNull { line ->
                LINE_REGEX.matchEntire(line.trim())?.let { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    var millis = match.groupValues[3].toLong()
                    // Normalize 2-digit millis (e.g. "20" -> 200ms)
                    if (millis < 100) millis *= 10
                    val startMs = minutes * 60_000 + seconds * 1_000 + millis
                    val text = match.groupValues[4].trim()
                    LrcLine(startMs, text)
                }
            }
            .sortedBy { it.startMs }
    }

    fun hasSyncedLyrics(lrcText: String): Boolean {
        return LINE_REGEX.containsMatchIn(lrcText)
    }
}
