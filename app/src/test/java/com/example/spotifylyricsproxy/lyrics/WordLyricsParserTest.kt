package com.example.spotifylyricsproxy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordLyricsParserTest {

    // Trimmed from AMLL TTML DB spotify-lyrics/2tqF9MPNdYdJU70U0ULO23.ttml, plus a v2 duet line.
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word"><head><metadata><ttm:agent type="person" xml:id="v1"/></metadata></head><body><div><p begin="23.627" end="26.141" itunes:key="L1" ttm:agent="v1"><span begin="23.627" end="23.750">塞</span><span begin="23.750" end="23.894">纳</span> <span begin="24.594" end="24.648">左</span><span begin="24.648" end="24.887">岸</span><span ttm:role="x-translation">translation</span></p><p begin="00:30.000" end="00:31.500" ttm:agent="v2"><span begin="30.0" end="30.5">Hello</span> <span begin="30.5" end="31.5">there</span></p></div></body></tt>"""

    @Test
    fun `TTML yields word timing, spaces and duet side`() {
        val lines = LrcParser.parse(ttml)
        assertEquals(2, lines.size)
        val first = lines[0]
        assertEquals(23627L, first.startMs)
        assertEquals(26141L, first.endMs)
        assertEquals("塞纳 左岸", first.text)
        assertEquals(4, first.words.size)
        assertEquals("纳 ", first.words[1].text)
        assertEquals(24594L, first.words[2].startMs)
        assertFalse(first.isSecondaryVoice)
        assertEquals("translation", first.translation)

        val second = lines[1]
        assertEquals(30000L, second.startMs)
        assertEquals("Hello there", second.text)
        assertTrue(second.isSecondaryVoice)
    }

    @Test
    fun `YRC yields word timing and skips JSON metadata`() {
        val yrc = """
            {"t":0,"c":[{"tx":"作词: "},{"tx":"方文山"}]}
            [23630,2510](23630,120,0)塞(23750,140,0)纳(24590,60,0) 左(24650,240,0)岸
        """.trimIndent()
        val lines = LrcParser.parse(yrc)
        assertEquals(1, lines.size)
        assertEquals(23630L, lines[0].startMs)
        assertEquals(26140L, lines[0].endMs)
        assertEquals("塞纳 左岸", lines[0].text)
        assertEquals(4, lines[0].words.size)
        assertEquals(24650L, lines[0].words[3].startMs)
        assertEquals(24890L, lines[0].words[3].endMs)
    }

    @Test
    fun `plain LRC still parses without words`() {
        val lines = LrcParser.parse("[00:01.00]hi\n[00:02.50]there")
        assertEquals(2, lines.size)
        assertTrue(lines[0].words.isEmpty())
    }

    @Test
    fun `TTML clock formats`() {
        assertEquals(23627L, LrcParser.parseTime("23.627"))
        assertEquals(62500L, LrcParser.parseTime("1:02.5"))
        assertEquals(3723400L, LrcParser.parseTime("01:02:03.400"))
        assertEquals(1500L, LrcParser.parseTime("1.5s"))
    }

    @Test
    fun `NetEase tlyric attaches to nearest line within one second`() {
        val lines = LrcParser.parse(
            "[00:10.00]Hello\n[00:20.00]World\n[00:40.00]Alone",
            translationLrc = "[00:10.00]你好\n[00:20.30]世界\n[00:30.00]远处"
        )
        assertEquals("你好", lines[0].translation)
        assertEquals("世界", lines[1].translation)
        assertEquals(null, lines[2].translation)
    }

    @Test
    fun `duet speaker labels are removed and split sides`() {
        val lines = LrcParser.parse(
            listOf(
                "[00:01.00]Jay:",
                "[00:02.00]first",
                "[00:03.00]aMEI：",
                "[00:04.00]second",
                "[00:05.00]合:",
                "[00:06.00]both",
                "[00:07.00]Jay:",
                "[00:08.00]third"
            ).joinToString("\n")
        )
        assertEquals(listOf("first", "second", "both", "third"), lines.map { it.text })
        assertEquals(listOf(false, true, false, false), lines.map { it.isSecondaryVoice })
    }

    @Test
    fun `lyrics without labels are untouched`() {
        val lines = LrcParser.parse("[00:01.00]hi\n[00:02.00]hello")
        assertEquals(2, lines.size)
        assertFalse(lines.any { it.isSecondaryVoice })
    }
}
