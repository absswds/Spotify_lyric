package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LrcLine
import kotlin.math.abs

/**
 * Cross-checks candidates from different sources against each other by line timing.
 * Timing only (not text) so Simplified/Traditional or romanised variants of the
 * same lyrics still agree, while a same-title wrong song or a badly offset
 * version does not.
 */
object LyricConsensus {
    private const val TOLERANCE_MS = 700L

    /**
     * How many of the shorter lyric's lines have a line in the other starting within the
     * tolerance. Symmetric, and measured from the shorter side because versions of the
     * same lyrics differ in extra lines (credits, a long line split in two), not in
     * timing. Only a real fragment (under half the lines) is scaled down.
     */
    fun agreement(a: List<LrcLine>, b: List<LrcLine>): Double {
        val sa = a.filter { it.text.isNotBlank() }.map { it.startMs }
        val sb = b.filter { it.text.isNotBlank() }.map { it.startMs }
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        val (small, large) = if (sa.size <= sb.size) sa to sb.sorted() else sb to sa.sorted()
        val hits = small.count { s ->
            val i = large.binarySearch(s).let { if (it >= 0) it else -it - 1 }
            (i < large.size && abs(large[i] - s) <= TOLERANCE_MS) ||
                (i > 0 && abs(large[i - 1] - s) <= TOLERANCE_MS)
        }
        val coverage = hits.toDouble() / small.size
        val sizeRatio = small.size.toDouble() / large.size
        return if (sizeRatio < 0.5) coverage * sizeRatio * 2 else coverage
    }

    /**
     * Best agreement of each entry with any entry from a different source, or null
     * when there is nothing to compare with.
     */
    fun bestAgreement(parsed: List<Pair<String, List<LrcLine>>>): List<Double?> =
        parsed.mapIndexed { i, (source, lines) ->
            if (lines.isEmpty()) return@mapIndexed null
            parsed.withIndex()
                .filter { (j, p) -> j != i && p.first != source && p.second.isNotEmpty() }
                .maxOfOrNull { agreement(lines, it.value.second) }
        }
}
