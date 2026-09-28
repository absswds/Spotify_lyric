package com.example.spotifylyricsproxy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineSpeakerTest {

    @Test
    fun `inline speaker labels split sides and are stripped`() {
        val lrc = """
            [00:01.00]王菲：给你我平平淡淡的等待守候
            [00:05.00]陈奕迅：给你我轰轰烈烈的伤口
            [00:09.00]王菲：因为爱情 怎么会有沧桑
            [00:13.00]陈奕迅：所以一直是甜的
            [00:17.00]合：因为爱情
        """.trimIndent()
        val r = LrcParser.parse(lrc)
        assertEquals("给你我平平淡淡的等待守候", r[0].text)
        assertFalse(r[0].isSecondaryVoice)
        assertTrue(r[1].isSecondaryVoice)
        assertFalse(r[2].isSecondaryVoice)
        assertTrue(r[3].isSecondaryVoice)
        assertFalse(r[4].isSecondaryVoice)
        assertEquals("因为爱情", r[4].text)
    }

    @Test
    fun `a single colon line is left alone`() {
        val r = LrcParser.parse("[00:01.00]Note: this is a lyric\n[00:05.00]Another line")
        assertEquals("Note: this is a lyric", r[0].text)
        assertFalse(r.any { it.isSecondaryVoice })
    }

    @Test
    fun `credit lines are not speakers`() {
        val r = LrcParser.parse("[00:00.00]作词：林夕\n[00:01.00]作曲：陈小霞\n[00:05.00]歌词")
        assertFalse(r.any { it.isSecondaryVoice })
    }
}
