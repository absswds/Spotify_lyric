package com.example.spotifylyricsproxy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditLinesTest {

    @Test
    fun `leading title and credit lines are dropped`() {
        val lrc = """
            [00:00.00]周傑倫 - 七里香
            [00:01.00]詞：方文山
            [00:02.00]曲：周傑倫
            [00:03.00]編曲：鐘興民
            [00:20.00]窗外的麻雀在電線桿上多嘴
        """.trimIndent()
        assertEquals(listOf("窗外的麻雀在電線桿上多嘴"), LrcParser.parse(lrc).map { it.text })
    }

    @Test
    fun `sung lines with colons later in the song are kept`() {
        val lrc = "[00:10.00]First line\n[00:20.00]Note: I said no"
        assertEquals(2, LrcParser.parse(lrc).size)
    }

    @Test
    fun `a lone dashed first line without credits is kept`() {
        assertEquals(2, LrcParser.parse("[00:01.00]Run - run away\n[00:05.00]Second").size)
    }
}
