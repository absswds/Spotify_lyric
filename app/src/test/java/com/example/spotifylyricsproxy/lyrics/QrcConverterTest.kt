package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.lyrics.qqmusic.QrcConverter
import com.example.spotifylyricsproxy.lyrics.qqmusic.QrcDecrypter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrcConverterTest {

    @Test
    fun qrcLinesBecomeYrc() {
        val qrc = "[ti:Song]\n[1000,800]Hel(1000,300)lo (1300,500)\n[2000,500]World(2000,500)"
        assertEquals(
            "[1000,800](1000,300,0)Hel(1300,500,0)lo \n[2000,500](2000,500,0)World",
            QrcConverter.qrcToYrc(qrc)
        )
    }

    @Test
    fun decryptedXmlPayloadIsUnwrapped() {
        val xml = """<?xml version="1.0"?><QrcInfos><LyricInfo><Lyric_1 LyricType="1" LyricContent="[0,100]A&amp;B(0,100)
"/></LyricInfo></QrcInfos>"""
        val yrc = QrcConverter.decryptedToYrc(xml)!!
        assertEquals("[0,100](0,100,0)A&B", yrc)
        assertEquals(1, LrcParser.parse(yrc).first().words.size)
    }

    @Test
    fun noWordTimingGivesNull() {
        assertNull(QrcConverter.qrcToYrc("[00:01.00]plain lrc"))
    }

    @Test
    fun extractsCdataFromDownloadResponse() {
        val resp = "<!--\n<lyric><content type=\"file\"><![CDATA[E5D94A70]]></content><contentts><![CDATA[]]></contentts></lyric>\n-->"
        assertEquals("E5D94A70", QrcConverter.extractEncrypted(resp))
    }

    @Test
    fun decryptsRealQrcFirstBlockToZlibHeader() {
        // First 8-byte block of the QRC payload for QQ Music song 97773 (live lyric_download.fcg response).
        val block = QrcDecrypter.decryptBytes("E5D94A70C91F7022")
        assertEquals(0x78, block[0].toInt() and 0xff)
        assertTrue(((block[0].toInt() and 0xff) * 256 + (block[1].toInt() and 0xff)) % 31 == 0)
    }
}
