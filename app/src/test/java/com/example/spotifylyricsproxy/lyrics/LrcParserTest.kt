package com.example.spotifylyricsproxy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parse standard LRC with 2-digit milliseconds`() {
        val lrc = """
            [00:15.20]First line
            [00:30.50]Second line
            [01:00.00]Third line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(3, result.size)
        assertEquals(15200L, result[0].startMs)
        assertEquals("First line", result[0].text)
        assertEquals(30500L, result[1].startMs)
        assertEquals(60000L, result[2].startMs) // 1*60000 + 0*1000 + 0 = 60000
        assertEquals("Third line", result[2].text)
    }

    @Test
    fun `parse LRC with 3-digit milliseconds`() {
        val lrc = """
            [00:36.200]Line A
            [01:02.100]Line B
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(2, result.size)
        assertEquals(36200L, result[0].startMs)
        assertEquals(62100L, result[1].startMs) // 1*60000 + 2*1000 + 100 = 62100
    }

    @Test
    fun `lines are sorted by startMs`() {
        val lrc = """
            [01:00.00]Third
            [00:30.00]Second
            [00:10.00]First
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(3, result.size)
        assertEquals(10000L, result[0].startMs)
        assertEquals(30000L, result[1].startMs)
        assertEquals(60000L, result[2].startMs)
    }

    @Test
    fun `ignore metadata lines without timestamps`() {
        val lrc = """
            [ti:Title]
            [ar:Artist]
            [00:10.00]Actual line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(1, result.size)
        assertEquals("Actual line", result[0].text)
    }

    @Test
    fun `empty input returns empty list`() {
        val result = LrcParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `hasSyncedLyrics detects valid LRC`() {
        assertTrue(LrcParser.hasSyncedLyrics("[00:10.00]Hello"))
        assertFalse(LrcParser.hasSyncedLyrics("Plain lyrics without timestamps"))
        assertFalse(LrcParser.hasSyncedLyrics(""))
    }
}
