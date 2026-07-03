package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LrcLine

object LyricSyncEngine {

    fun findCurrentLine(lines: List<LrcLine>, positionMs: Long): LrcLine? {
        if (lines.isEmpty()) return null
        // Binary search for the last line with startMs <= positionMs
        var lo = 0
        var hi = lines.lastIndex
        var result: LrcLine? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].startMs <= positionMs) {
                result = lines[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    fun findNextLine(lines: List<LrcLine>, currentLine: LrcLine?): LrcLine? {
        if (lines.isEmpty() || currentLine == null) return lines.firstOrNull()
        val idx = lines.indexOf(currentLine)
        return if (idx >= 0 && idx + 1 < lines.size) lines[idx + 1] else null
    }
}
