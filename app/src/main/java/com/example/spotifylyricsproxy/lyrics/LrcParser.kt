package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.core.model.LyricWord
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * Parses stored lyric text into lines. The format is detected from the content, so
 * word-level lyrics travel through the same `syncedLyrics` string (and cache column)
 * as plain LRC:
 * - TTML (AMLL TTML DB / Apple Music style): word timing + duet agents
 * - NetEase YRC: `[lineStart,lineDur](wordStart,wordDur,0)word…`
 * - LRC: `[mm:ss.xx]text`
 */
object LrcParser {

    // Matches [mm:ss.xx] or [mm:ss.xxx]
    private val LINE_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
    private val YRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val YRC_WORD = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")
    private val WHITESPACE = Regex("\\s+")

    /**
     * @param translationLrc a separate LRC-timed translation (NetEase `tlyric`); each line
     *   gets the translation whose timestamp is closest to its own (within 1 s).
     */
    fun parse(lrcText: String, translationLrc: String? = null): List<LrcLine> {
        val trimmed = lrcText.trimStart()
        val lines = when {
            trimmed.startsWith("<tt") || trimmed.startsWith("<?xml") -> parseTtml(trimmed)
            lrcText.lineSequence().any { YRC_LINE.matches(it.trim()) } -> assignSpeakers(parseYrc(lrcText))
            else -> assignSpeakers(parseLrc(lrcText))
        }
        if (translationLrc.isNullOrBlank()) return lines
        val translated = parseLrc(translationLrc).filter { it.text.isNotBlank() }
        if (translated.isEmpty()) return lines
        return lines.map { line ->
            if (line.translation != null) return@map line
            val match = translated.minByOrNull { abs(it.startMs - line.startMs) }
            if (match != null && abs(match.startMs - line.startMs) <= 1_000) line.copy(translation = match.text) else line
        }
    }

    // "Jay:" / "aMEI：" / "合:" on a line of its own marks who sings the following lines.
    private val SPEAKER_LABEL = Regex("""^\s*([^:：\s][^:：]{0,15}?)\s*[:：]\s*$""")
    private val GROUP_LABELS = setOf("合", "合唱", "齐", "齊", "all", "both", "together", "男女", "全", "chorus")

    /**
     * Duets in line-synced lyrics are marked with speaker label lines. Remove those lines
     * (they are not sung and should not be translated) and put the second singer's lines
     * on the opposite side; group parts stay on the main side.
     */
    internal fun assignSpeakers(lines: List<LrcLine>): List<LrcLine> {
        if (lines.none { SPEAKER_LABEL.matches(it.text) }) return lines
        var main: String? = null
        var current: String? = null
        val out = ArrayList<LrcLine>(lines.size)
        for (line in lines) {
            val label = SPEAKER_LABEL.matchEntire(line.text)?.groupValues?.get(1)?.trim()
            if (label != null) {
                val key = label.lowercase()
                current = if (key in GROUP_LABELS) null else key
                if (main == null && current != null) main = current
                continue
            }
            out.add(line.copy(isSecondaryVoice = current != null && current != main))
        }
        return out
    }

    fun hasSyncedLyrics(lrcText: String): Boolean {
        return LINE_REGEX.containsMatchIn(lrcText) || parse(lrcText).isNotEmpty()
    }

    private fun parseLrc(lrcText: String): List<LrcLine> {
        return lrcText.lines()
            .mapNotNull { line ->
                LINE_REGEX.matchEntire(line.trim())?.let { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    var millis = match.groupValues[3].toLong()
                    // Normalize 2-digit millis (e.g. "20" -> 200ms)
                    if (millis < 100) millis *= 10
                    val startMs = minutes * 60_000 + seconds * 1_000 + millis
                    val text = match.groupValues[4].trim()
                    LrcLine(startMs, text)
                }
            }
            .sortedBy { it.startMs }
    }

    /** NetEase YRC. Metadata lines are JSON (`{"t":…}`) and are skipped. */
    private fun parseYrc(text: String): List<LrcLine> {
        return text.lineSequence()
            .mapNotNull { raw ->
                val m = YRC_LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
                val start = m.groupValues[1].toLong()
                val dur = m.groupValues[2].toLong()
                val words = YRC_WORD.findAll(m.groupValues[3]).map { w ->
                    val ws = w.groupValues[1].toLong()
                    LyricWord(ws, ws + w.groupValues[2].toLong(), w.groupValues[3])
                }.filter { it.text.isNotEmpty() }.toList()
                val lineText = words.joinToString("") { it.text }.trim()
                if (lineText.isEmpty()) null
                else LrcLine(start, lineText, words, start + dur)
            }
            .sortedBy { it.startMs }
            .toList()
    }

    /**
     * TTML as published by the AMLL TTML DB. Each `<p>` is a line, each timed `<span>` a
     * word; untimed text between spans (spaces) is appended to the previous word.
     * Translation / romanisation / background-vocal spans (`ttm:role`) are skipped.
     * Uses javax.xml DOM, available on both Android and the JVM unit-test runtime.
     */
    private fun parseTtml(text: String): List<LrcLine> {
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(InputSource(StringReader(text)))
        } catch (_: Exception) {
            return emptyList()
        }
        // iTunes-style block: <translations><translation><text for="L1">…</text>…
        val blockTranslations = HashMap<String, String>()
        val texts = doc.getElementsByTagName("text")
        for (i in 0 until texts.length) {
            val t = texts.item(i) as? Element ?: continue
            val key = t.getAttribute("for")
            val parent = t.parentNode as? Element
            if (key.isNotEmpty() && parent?.tagName == "translation" && key !in blockTranslations) {
                t.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let { blockTranslations[key] = it }
            }
        }
        val paragraphs = doc.getElementsByTagName("p")
        val lines = mutableListOf<LrcLine>()
        var mainAgent: String? = null
        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue
            val begin = parseTime(p.getAttribute("begin")) ?: continue
            val end = parseTime(p.getAttribute("end"))
            val agent = p.getAttribute("ttm:agent").ifEmpty { null }
            if (mainAgent == null) mainAgent = agent

            val words = mutableListOf<LyricWord>()
            var translation: String? = blockTranslations[p.getAttribute("itunes:key")]
            val children = p.childNodes
            for (c in 0 until children.length) {
                val node = children.item(c)
                when {
                    node is Element && node.tagName == "span" -> {
                        if (node.hasAttribute("ttm:role")) {
                            // AMLL inline translation: <span ttm:role="x-translation">…</span>
                            if (node.getAttribute("ttm:role") == "x-translation" && translation == null) {
                                translation = node.textContent?.trim()?.takeIf { it.isNotEmpty() }
                            }
                            continue
                        }
                        val ws = parseTime(node.getAttribute("begin")) ?: continue
                        val we = parseTime(node.getAttribute("end")) ?: ws
                        val t = node.textContent.orEmpty()
                        if (t.isNotEmpty()) words.add(LyricWord(ws, we.coerceAtLeast(ws), t))
                    }
                    node.nodeType == Node.TEXT_NODE && words.isNotEmpty() -> {
                        val t = node.nodeValue.orEmpty()
                        if (t.isNotEmpty()) {
                            val last = words.removeAt(words.lastIndex)
                            words.add(last.copy(text = last.text + t.replace(WHITESPACE, " ")))
                        }
                    }
                }
            }
            val lineText = words.joinToString("") { it.text }.trim()
                .ifEmpty { p.textContent.orEmpty().trim() }
            if (lineText.isEmpty()) continue
            val secondary = agent != null && agent != mainAgent && agent != "v1000"
            lines.add(LrcLine(begin, lineText, words.toList(), end, secondary, translation))
        }
        return lines.sortedBy { it.startMs }
    }

    /** TTML clock values: "23.627", "1:02.5", "01:02:03.400", optionally suffixed with "s". */
    internal fun parseTime(value: String?): Long? {
        val v = value?.trim()?.removeSuffix("s") ?: return null
        if (v.isEmpty()) return null
        val parts = v.split(':')
        return try {
            var seconds = 0.0
            for (part in parts) seconds = seconds * 60 + part.toDouble()
            (seconds * 1000).toLong()
        } catch (_: NumberFormatException) {
            null
        }
    }
}
