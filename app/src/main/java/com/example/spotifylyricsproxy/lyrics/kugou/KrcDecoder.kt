package com.example.spotifylyricsproxy.lyrics.kugou

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * Kugou KRC decryption and conversion to NetEase YRC text, which
 * [com.example.spotifylyricsproxy.lyrics.LrcParser] already understands. Pure JVM so it is unit-testable.
 *
 * Ported from Lyricify-Lyrics-Helper (Apache-2.0), Decrypter/Krc/Decrypter.cs
 *   https://github.com/WXRIW/Lyricify-Lyrics-Helper
 */
object KrcDecoder {

    private val KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
    )

    private val LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val WORD = Regex("""<(\d+),(\d+),\d+>([^<]*)""")

    /** base64 → drop "krc1" header → XOR with [KEY] → zlib inflate → UTF-8 (leading BOM removed). */
    fun decrypt(base64Content: String): String {
        val raw = Base64.getDecoder().decode(base64Content.trim())
        val data = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor KEY[i % KEY.size].toInt()).toByte() }
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toString("UTF-8").removePrefix("﻿")
    }

    /**
     * `[lineStart,lineDur]<wordOffset,wordDur,0>word…` (offsets relative to the line) →
     * `[lineStart,lineDur](wordStart,wordDur,0)word…` (absolute). Metadata lines are dropped.
     */
    fun krcToYrc(krc: String): String = krc.lineSequence().mapNotNull { raw ->
        val m = LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
        val start = m.groupValues[1].toLong()
        val words = WORD.findAll(m.groupValues[3]).joinToString("") { w ->
            "(${start + w.groupValues[1].toLong()},${w.groupValues[2]},0)${w.groupValues[3]}"
        }
        if (words.isEmpty()) null else "[$start,${m.groupValues[2]}]$words"
    }.joinToString("\n")
}
