package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LyricCandidate
import android.icu.text.Transliterator

object LyricMatcher {

    private const val AUTO_ACCEPT_THRESHOLD = 75
    private const val MANUAL_REVIEW_THRESHOLD = 60

    /**
     * Normalize both Simplified and Traditional Chinese to Traditional via the
     * system ICU transliterator (no hand-written map, covers all CJK variants).
     * Spotify may deliver Traditional titles while Netease/QQ return Simplified,
     * so we convert both sides to the same script before comparing.
     */
    private val toTraditional: Transliterator by lazy {
        Transliterator.getInstance("Simplified-Traditional")
    }
    private val cnLock = Any()

    private fun normalizeCN(s: String): String = synchronized(cnLock) {
        try { toTraditional.transliterate(s) } catch (_: Exception) { s }
    }

    fun score(
        candidate: LyricCandidate,
        expectedTitle: String,
        expectedArtist: String,
        expectedAlbum: String = "",
        expectedDurationMs: Long = 0
    ): LyricCandidate {
        var score = 0

        // Title match (max 30)
        val cleanedExpected = cleanTitle(normalizeCN(expectedTitle))
        val cleanedCandidate = cleanTitle(normalizeCN(candidate.trackName))

        if (cleanedExpected.equals(cleanedCandidate, ignoreCase = true)) {
            score += 30
        } else if (cleanedCandidate.contains(cleanedExpected, ignoreCase = true) ||
                   cleanedExpected.contains(cleanedCandidate, ignoreCase = true)) {
            score += 18
        }

        // Artist match (max 30, penalize only when both sides have usable artists)
        val expectedArtists = splitArtists(expectedArtist).map { normalizeCN(it) }.filter { it.length >= 2 }
        val candidateArtists = splitArtists(candidate.artistName).map { normalizeCN(it) }.filter { it.length >= 2 }
        when {
            expectedArtists.isEmpty() || candidateArtists.isEmpty() -> {
                // Missing artist metadata should not punish a good title/duration match.
            }
            expectedArtists.any { expected ->
                candidateArtists.any { candidate ->
                    candidate.contains(expected, ignoreCase = true) || expected.contains(candidate, ignoreCase = true)
                }
            } -> score += 30
            expectedArtists.any { expected ->
                candidateArtists.any { candidate ->
                    expected.take(2).let { it.length >= 2 && candidate.contains(it, ignoreCase = true) }
                }
            } -> score += 10
            else -> score -= 18
        }

        // Duration match (max 15)
        if (expectedDurationMs > 0 && candidate.durationMs > 0) {
            val diff = kotlin.math.abs(expectedDurationMs - candidate.durationMs)
            when {
                diff < 2_000 -> score += 15
                diff < 5_000 -> score += 6
            }
        }

        // Album match (max 5)
        if (expectedAlbum.isNotEmpty() &&
            normalizeCN(candidate.albumName).contains(normalizeCN(expectedAlbum), ignoreCase = true)) {
            score += 5
        }

        // Synced lyrics bonus (max 8)
        if (!candidate.syncedLyrics.isNullOrEmpty()) {
            score += 8
        } else if (!candidate.plainLyrics.isNullOrEmpty()) {
            score -= 20
        }

        return candidate.copy(score = score)
    }

    fun isAutoAccept(score: Int): Boolean = score >= AUTO_ACCEPT_THRESHOLD
    fun needsManualReview(score: Int): Boolean =
        score in MANUAL_REVIEW_THRESHOLD until AUTO_ACCEPT_THRESHOLD

    /**
     * Filter out candidates whose sourceLyricId appears in the rejection list.
     * Returns the filtered list preserving order.
     */
    fun filterRejected(
        candidates: List<LyricCandidate>,
        rejectedIds: Set<String>
    ): List<LyricCandidate> = candidates.filter { candidate ->
        candidate.id.toString() !in rejectedIds
    }

    private fun splitArtists(artists: String): List<String> =
        artists.split(Regex("""\s*(?:,|，|&|、|/|feat\.?|ft\.?)\s*""", RegexOption.IGNORE_CASE))
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""[-–—]\s*(Remastered|Live|Acoustic|Explicit|Radio Edit|Instrumental|Deluxe Edition|Bonus Track|feat\..*|ft\..*|\(.*?\))""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((Remastered|Live|Acoustic|Explicit|Radio Edit|Instrumental)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .lowercase()
    }
}
