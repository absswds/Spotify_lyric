package com.example.spotifylyricsproxy.lyrics.qqmusic

/**
 * Turns QQ Music's `lyric_download.fcg` response into NetEase YRC text so `LrcParser` can read it.
 *
 * Response/QRC handling follows Lyricify Lyrics Helper (Apache-2.0),
 * https://github.com/WXRIW/Lyricify-Lyrics-Helper
 * `Providers/Web/QQMusic/Api.cs` (GetLyricsAsync) and `Parsers/QrcParser.cs`.
 * QRC line: `[lineStart,lineDur]word(wordStart,wordDur)word(wordStart,wordDur)`.
 */
object QrcConverter {

    // The response is wrapped in <!-- --> and isn't well-formed XML, so use regexes.
    private val CONTENT = Regex("""<content\b[^>]*><!\[CDATA\[([0-9A-Fa-f]+)]]>""")
    private val LYRIC_CONTENT = Regex("""LyricContent="([^"]*)"""")
    private val QRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val QRC_WORD = Regex("""(.*?)\((\d+),(\d+)\)""")

    /** Hex-encrypted original-lyric payload from the download response, or null. */
    fun extractEncrypted(response: String): String? =
        CONTENT.find(response)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    /** Decrypted payload -> YRC text, or null when there are no word-timed lines. */
    fun decryptedToYrc(decrypted: String): String? {
        val qrc = LYRIC_CONTENT.find(decrypted)?.groupValues?.get(1)?.let(::unescapeXml) ?: decrypted
        return qrcToYrc(qrc)
    }

    fun qrcToYrc(qrc: String): String? {
        val lines = qrc.lineSequence().mapNotNull { raw ->
            val m = QRC_LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
            val words = QRC_WORD.findAll(m.groupValues[3]).joinToString("") { w ->
                "(${w.groupValues[2]},${w.groupValues[3]},0)${w.groupValues[1]}"
            }
            if (words.isEmpty()) null else "[${m.groupValues[1]},${m.groupValues[2]}]$words"
        }.toList()
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")
}
