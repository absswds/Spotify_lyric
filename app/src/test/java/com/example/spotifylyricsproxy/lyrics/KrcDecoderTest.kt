package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.lyrics.kugou.KrcDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class KrcDecoderTest {

    private val krc = "[ti:t]\n[ar:a]\n[offset:0]\n[language:e30=]\n" +
        "[2250,900]<0,450,0>Hello<450,450,0> world\n[4000,500]<100,400,0>x\n"

    @Test
    fun `KRC converts to YRC with absolute word times and no metadata`() {
        assertEquals(
            "[2250,900](2250,450,0)Hello(2700,450,0) world\n[4000,500](4100,400,0)x",
            KrcDecoder.krcToYrc(krc)
        )
    }

    @Test
    fun `converted YRC parses with word timing`() {
        val lines = LrcParser.parse(KrcDecoder.krcToYrc(krc))
        assertEquals(2, lines.size)
        assertEquals(2250L, lines[0].startMs)
        assertEquals(2700L, lines[0].words[1].startMs)
    }

    @Test
    fun `decrypt reverses krc1 header, xor and zlib`() {
        val key = intArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69)
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos).use { it.write(("﻿" + krc).toByteArray()) }
        val z = bos.toByteArray()
        val enc = "krc1".toByteArray() + ByteArray(z.size) { i -> (z[i].toInt() xor key[i % 16]).toByte() }
        assertEquals(krc, KrcDecoder.decrypt(Base64.getEncoder().encodeToString(enc)))
    }
}
