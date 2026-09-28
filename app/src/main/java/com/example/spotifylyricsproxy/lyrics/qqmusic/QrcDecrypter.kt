package com.example.spotifylyricsproxy.lyrics.qqmusic

import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

/**
 * Decrypts QQ Music QRC lyrics (hex string -> QQ's non-standard triple DES -> zlib).
 *
 * Ported from Lyricify Lyrics Helper (Apache-2.0),
 * https://github.com/WXRIW/Lyricify-Lyrics-Helper
 * `Decrypter/Qrc/Decrypter.cs` and `Decrypter/Qrc/DESHelper.cs`.
 * The DES variant differs from the standard one (bit ordering, sbox tables), so
 * javax.crypto can't be used. All "uint" values are Kotlin Ints with unsigned shifts.
 */
object QrcDecrypter {

    private val QQ_KEY = "!@#)(*\$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
    private const val ENCRYPT = 1
    private const val DECRYPT = 0

    fun decrypt(hex: String): String {
        val out = ByteArrayOutputStream()
        InflaterInputStream(decryptBytes(hex).inputStream()).use { it.copyTo(out) }
        return out.toByteArray().decodeToString().removePrefix("﻿")
    }

    /** Triple-DES stage only (still zlib-compressed). */
    internal fun decryptBytes(hex: String): ByteArray {
        val encrypted = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val schedule = Array(3) { Array(16) { ByteArray(6) } }
        tripleDesKeySetup(QQ_KEY, schedule, DECRYPT)
        val data = ByteArray(encrypted.size)
        val temp = ByteArray(8)
        for (i in 0 until encrypted.size - 7 step 8) {
            tripleDesCrypt(encrypted.copyOfRange(i, i + 8), temp, schedule)
            temp.copyInto(data, i)
        }
        return data
    }

    private fun bitNum(a: ByteArray, b: Int, c: Int): Int =
        (((a[b / 32 * 4 + 3 - b % 32 / 8].toInt() and 0xff) ushr (7 - b % 8)) and 1) shl c

    private fun bitNumIntR(a: Int, b: Int, c: Int): Int = ((a ushr (31 - b)) and 1) shl c

    private fun bitNumIntL(a: Int, b: Int, c: Int): Int = ((a shl b) and 0x80000000.toInt()) ushr c

    private fun sboxBit(a: Int): Int = (a and 0x20) or ((a and 0x1f) shr 1) or ((a and 0x01) shl 4)

    private val SBOX1 = intArrayOf(
        14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
        0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
        4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
        15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
    )
    private val SBOX2 = intArrayOf(
        15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
        3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
        0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
        13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
    )
    private val SBOX3 = intArrayOf(
        10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
        13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
        13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
        1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
    )
    private val SBOX4 = intArrayOf(
        7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
        13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
        10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
        3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
    )
    private val SBOX5 = intArrayOf(
        2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
        14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
        4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
        11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
    )
    private val SBOX6 = intArrayOf(
        12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
        10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
        9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
        4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
    )
    private val SBOX7 = intArrayOf(
        4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
        13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
        1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
        6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
    )
    private val SBOX8 = intArrayOf(
        13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
        1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
        7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
        2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
    )

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
        9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    )
    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
        13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )
    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9,
        22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
        43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    private fun keySchedule(key: ByteArray, schedule: Array<ByteArray>, mode: Int) {
        var c = 0
        var d = 0
        for (i in 0 until 28) c = c or bitNum(key, KEY_PERM_C[i], 31 - i)
        for (i in 0 until 28) d = d or bitNum(key, KEY_PERM_D[i], 31 - i)
        for (i in 0 until 16) {
            val s = KEY_RND_SHIFT[i]
            c = ((c shl s) or (c ushr (28 - s))) and 0xfffffff0.toInt()
            d = ((d shl s) or (d ushr (28 - s))) and 0xfffffff0.toInt()
            val toGen = if (mode == DECRYPT) 15 - i else i
            val row = schedule[toGen]
            row.fill(0)
            for (j in 0 until 24) {
                row[j / 8] = (row[j / 8].toInt() or bitNumIntR(c, KEY_COMPRESSION[j], 7 - j % 8)).toByte()
            }
            for (j in 24 until 48) {
                row[j / 8] = (row[j / 8].toInt() or bitNumIntR(d, KEY_COMPRESSION[j] - 27, 7 - j % 8)).toByte()
            }
        }
    }

    private fun ip(state: IntArray, input: ByteArray) {
        val order0 = intArrayOf(57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7)
        var s0 = 0
        var s1 = 0
        for (k in 0 until 32) {
            s0 = s0 or bitNum(input, order0[k], 31 - k)
            s1 = s1 or bitNum(input, order0[k] - 1, 31 - k)
        }
        state[0] = s0
        state[1] = s1
    }

    private fun invIp(state: IntArray, out: ByteArray) {
        // out[3] uses bits 7,15,23,31; out[2] 6,14,..; out[0] 4,..; out[7] 3,..; out[4] 0,..
        val byteIndex = intArrayOf(4, 5, 6, 7, 0, 1, 2, 3) // for base bit 0..7
        for (base in 0 until 8) {
            out[byteIndex[base]] = (
                bitNumIntR(state[1], base, 7) or bitNumIntR(state[0], base, 6) or
                    bitNumIntR(state[1], base + 8, 5) or bitNumIntR(state[0], base + 8, 4) or
                    bitNumIntR(state[1], base + 16, 3) or bitNumIntR(state[0], base + 16, 2) or
                    bitNumIntR(state[1], base + 24, 1) or bitNumIntR(state[0], base + 24, 0)
                ).toByte()
        }
    }

    private val P_PERM = intArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
        1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)

    private fun f(stateIn: Int, key: ByteArray): Int {
        val st = stateIn
        val t1 = bitNumIntL(st, 31, 0) or ((st and 0xf0000000.toInt()) ushr 1) or bitNumIntL(st, 4, 5) or
            bitNumIntL(st, 3, 6) or ((st and 0x0f000000) ushr 3) or bitNumIntL(st, 8, 11) or
            bitNumIntL(st, 7, 12) or ((st and 0x00f00000) ushr 5) or bitNumIntL(st, 12, 17) or
            bitNumIntL(st, 11, 18) or ((st and 0x000f0000) ushr 7) or bitNumIntL(st, 16, 23)
        val t2 = bitNumIntL(st, 15, 0) or ((st and 0x0000f000) shl 15) or bitNumIntL(st, 20, 5) or
            bitNumIntL(st, 19, 6) or ((st and 0x00000f00) shl 13) or bitNumIntL(st, 24, 11) or
            bitNumIntL(st, 23, 12) or ((st and 0x000000f0) shl 11) or bitNumIntL(st, 28, 17) or
            bitNumIntL(st, 27, 18) or ((st and 0x0000000f) shl 9) or bitNumIntL(st, 0, 23)

        val l = IntArray(6)
        l[0] = (t1 ushr 24) and 0xff
        l[1] = (t1 ushr 16) and 0xff
        l[2] = (t1 ushr 8) and 0xff
        l[3] = (t2 ushr 24) and 0xff
        l[4] = (t2 ushr 16) and 0xff
        l[5] = (t2 ushr 8) and 0xff
        for (k in 0 until 6) l[k] = l[k] xor (key[k].toInt() and 0xff)

        val s = (SBOX1[sboxBit(l[0] shr 2)] shl 28) or
            (SBOX2[sboxBit(((l[0] and 0x03) shl 4) or (l[1] shr 4))] shl 24) or
            (SBOX3[sboxBit(((l[1] and 0x0f) shl 2) or (l[2] shr 6))] shl 20) or
            (SBOX4[sboxBit(l[2] and 0x3f)] shl 16) or
            (SBOX5[sboxBit(l[3] shr 2)] shl 12) or
            (SBOX6[sboxBit(((l[3] and 0x03) shl 4) or (l[4] shr 4))] shl 8) or
            (SBOX7[sboxBit(((l[4] and 0x0f) shl 2) or (l[5] shr 6))] shl 4) or
            SBOX8[sboxBit(l[5] and 0x3f)]

        var result = 0
        for (k in 0 until 32) result = result or bitNumIntL(s, P_PERM[k], k)
        return result
    }

    private fun crypt(input: ByteArray, output: ByteArray, key: Array<ByteArray>) {
        val state = IntArray(2)
        ip(state, input)
        for (idx in 0 until 15) {
            val t = state[1]
            state[1] = f(state[1], key[idx]) xor state[0]
            state[0] = t
        }
        state[0] = f(state[1], key[15]) xor state[0]
        invIp(state, output)
    }

    private fun tripleDesKeySetup(key: ByteArray, schedule: Array<Array<ByteArray>>, mode: Int) {
        if (mode == ENCRYPT) {
            keySchedule(key, schedule[0], mode)
            keySchedule(key.copyOfRange(8, key.size), schedule[1], DECRYPT)
            keySchedule(key.copyOfRange(16, key.size), schedule[2], mode)
        } else {
            keySchedule(key, schedule[2], mode)
            keySchedule(key.copyOfRange(8, key.size), schedule[1], ENCRYPT)
            keySchedule(key.copyOfRange(16, key.size), schedule[0], mode)
        }
    }

    private fun tripleDesCrypt(input: ByteArray, output: ByteArray, key: Array<Array<ByteArray>>) {
        crypt(input, output, key[0])
        crypt(output, output, key[1])
        crypt(output, output, key[2])
    }
}
