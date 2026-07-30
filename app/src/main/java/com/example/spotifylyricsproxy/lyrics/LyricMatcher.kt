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

        // Artist match (max 30, big penalty for strong mismatch)
        val primaryArtist = normalizeCN(expectedArtist.split(",", "&", "feat.", "ft.").first().trim())
        val candidateArtist = normalizeCN(candidate.artistName)
        if (candidateArtist.contains(primaryArtist, ignoreCase = true)) {
            score += 30
        } else if (candidateArtist.contains(normalizeCN(expectedArtist.take(3)), ignoreCase = true)) {
            score += 10
        } else {
            // Artist mismatch — heavily penalize to avoid wrong-match lyrics
            score -= 25
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

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""[-–—]\s*(Remastered|Live|Acoustic|Explicit|Radio Edit|Instrumental|Deluxe Edition|Bonus Track|feat\..*|ft\..*|\(.*?\))""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((Remastered|Live|Acoustic|Explicit|Radio Edit|Instrumental)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .lowercase()
    }
}
