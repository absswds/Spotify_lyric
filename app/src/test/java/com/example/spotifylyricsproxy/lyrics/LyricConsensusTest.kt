package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricConsensusTest {
    private fun lines(vararg starts: Long) = starts.map { LrcLine(it, "x$it") }

    @Test fun sameTimingAgrees() {
        assertTrue(LyricConsensus.agreement(lines(1000, 5000, 9000), lines(1200, 5300, 8800)) > 0.9)
    }

    @Test fun shiftedTimingDisagrees() {
        assertTrue(LyricConsensus.agreement(lines(1000, 5000, 9000), lines(3000, 7000, 11000)) < 0.3)
    }

    @Test fun extraCreditAndSplitLinesStillAgree() {
        val lrclib = lines(10000, 14000, 18000, 22000, 26000, 30000, 34000, 38000)
        // Same timing plus two credit lines and two split halves.
        val netease = lines(0, 1000, 10000, 12000, 14000, 18000, 22000, 24000, 26000, 30000, 34000, 38000)
        assertTrue(LyricConsensus.agreement(netease, lrclib) >= 0.9)
        assertEquals(LyricConsensus.agreement(netease, lrclib), LyricConsensus.agreement(lrclib, netease), 1e-9)
    }

    @Test fun fragmentDoesNotAgree() {
        val full = lines(*LongArray(20) { 5000L + it * 4000L })
        assertTrue(LyricConsensus.agreement(lines(5000, 9000, 13000), full) < 0.6)
    }

    @Test fun onlyComparesAcrossSources() {
        val r = LyricConsensus.bestAgreement(listOf("a" to lines(1000), "a" to lines(1000)))
        assertNull(r[0]); assertNull(r[1])
        val r2 = LyricConsensus.bestAgreement(listOf("a" to lines(1000), "b" to lines(1000)))
        assertEquals(1.0, r2[0]!!, 1e-9)
    }
}
