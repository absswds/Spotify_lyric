package com.example.spotifylyricsproxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Every UI string must exist in all four locales, with the same format arguments:
 * a key missing from one locale silently falls back to Simplified Chinese, and a
 * mismatched "%1$s" / "%d" crashes String.format at runtime.
 */
class LocaleStringsTest {

    private val locales = listOf("values", "values-zh-rTW", "values-en", "values-ja")
    private val format = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc%]""")

    /** Gradle runs unit tests from the module directory. */
    private fun stringsFile(locale: String): File =
        listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml"))
            .first { it.exists() }

    private fun load(locale: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile(locale))
        val out = mutableMapOf<String, String>()
        for (tag in listOf("string", "string-array", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                if (e.getAttribute("translatable") == "false") continue
                out[e.getAttribute("name")] = e.textContent
            }
        }
        return out
    }

    @Test
    fun `every locale has the same keys`() {
        val all = locales.associateWith { load(it) }
        val keys = all.values.flatMap { it.keys }.toSet()
        val missing = all.mapValues { (_, map) -> (keys - map.keys).sorted() }.filterValues { it.isNotEmpty() }
        assertTrue("Missing translations: $missing", missing.isEmpty())
    }

    @Test
    fun `format arguments match the default locale`() {
        val base = load("values")
        for (locale in locales.drop(1)) {
            for ((key, text) in load(locale)) {
                val expected = base[key]?.let { format.findAll(it).map { m -> m.value }.sorted().toList() } ?: continue
                val actual = format.findAll(text).map { it.value }.sorted().toList()
                assertEquals("$locale/$key format arguments", expected, actual)
            }
        }
    }
}
