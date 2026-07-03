package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LyricCandidate

object LyricMatcher {

    private const val AUTO_ACCEPT_THRESHOLD = 75
    private const val MANUAL_REVIEW_THRESHOLD = 60

    fun score(
        candidate: LyricCandidate,
        expectedTitle: String,
        expectedArtist: String,
        expectedAlbum: String = "",
        expectedDurationMs: Long = 0
    ): LyricCandidate {
        var score = 0

        // Title match
        val cleanedExpected = cleanTitle(expectedTitle)
        val cleanedCandidate = cleanTitle(candidate.trackName)

        if (cleanedExpected.equals(cleanedCandidate, ignoreCase = true)) {
            score += 35
        } else if (cleanedCandidate.contains(cleanedExpected, ignoreCase = true) ||
                   cleanedExpected.contains(cleanedCandidate, ignoreCase = true)) {
            score += 22
        }

        // Artist match
        val primaryArtist = expectedArtist.split(",", "&", "feat.", "ft.").first().trim()
        if (candidate.artistName.contains(primaryArtist, ignoreCase = true)) {
            score += 20
        } else if (candidate.artistName.contains(expectedArtist.take(3), ignoreCase = true)) {
            score += 8
        }

        // Duration match
        if (expectedDurationMs > 0 && candidate.durationMs > 0) {
            val diff = kotlin.math.abs(expectedDurationMs - candidate.durationMs)
            when {
                diff < 2_000 -> score += 18
                diff < 5_000 -> score += 8
            }
        }

        // Album match
        if (expectedAlbum.isNotEmpty() &&
            candidate.albumName.contains(expectedAlbum, ignoreCase = true)) {
            score += 8
        }

        // Synced lyrics bonus
        if (!candidate.syncedLyrics.isNullOrEmpty()) {
            score += 12
        } else if (!candidate.plainLyrics.isNullOrEmpty()) {
            score -= 20
        }

        return candidate.copy(score = score)
    }

    fun isAutoAccept(score: Int): Boolean = score >= AUTO_ACCEPT_THRESHOLD
    fun needsManualReview(score: Int): Boolean =
        score in MANUAL_REVIEW_THRESHOLD until AUTO_ACCEPT_THRESHOLD

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""[-–—]\s*(Remastered|Live|Acoustic|Explicit|Radio Edit|Instrumental|Deluxe Edition|Bonus Track|feat\..*|ft\..*|\(.*?\))""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((Remastered|Live|Acoustic|Explicit|Radio Edit|Instrumental)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .lowercase()
    }
}
