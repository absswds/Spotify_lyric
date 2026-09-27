package com.example.spotifylyricsproxy.core.model

data class LrcLine(
    val startMs: Long,
    val text: String,
    /** Per-word timing from word-level formats (TTML, NetEase YRC); empty for plain LRC. */
    val words: List<LyricWord> = emptyList(),
    /** End of the line when the format provides it; null for plain LRC. */
    val endMs: Long? = null,
    /** True when a duet's second singer sings this line (shown on the opposite side). */
    val isSecondaryVoice: Boolean = false,
    /** Translation shipped with the lyrics (NetEase tlyric, TTML translation); simplified Chinese. */
    val translation: String? = null
)

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    /** The word including any trailing space, so joined words reproduce the line text. */
    val text: String
)
