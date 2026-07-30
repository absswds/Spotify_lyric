package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.model.LyricCandidate

object LyricMatcher {

    private const val AUTO_ACCEPT_THRESHOLD = 75
    private const val MANUAL_REVIEW_THRESHOLD = 60

    // Simplified <-> Traditional normalization map for common song/artist chars.
    // Covers the CJK variants that break exact title matching across sources.
    private val CN_NORMALIZE = mapOf(
        // 传闻 case
        '传' to '傳', '闻' to '聞',
        // common
        '国' to '國', '爱' to '愛', '气' to '氣', '时' to '時', '来' to '來',
        '说' to '說', '会' to '會', '这' to '這', '那' to '那', '里' to '裡',
        '东' to '東', '风' to '風', '梦' to '夢', '长' to '長', '发' to '發',
        '边' to '邊', '学' to '學', '习' to '習', '乐' to '樂', '点' to '點',
        '让' to '讓', '对' to '對', '开' to '開', '关' to '關', '亲' to '親',
        '谢' to '謝', '觉' to '覺', '远' to '遠', '进' to '進', '过' to '過',
        '还' to '還', '吗' to '嗎', '呢' to '呢', '吧' to '吧', '听' to '聽',
        '画' to '畫', '鱼' to '魚', '鸟' to '鳥', '马' to '馬', '车' to '車',
        '飞' to '飛', '龙' to '龍', '凤' to '鳳', '岁' to '歲', '历' to '歷',
        '经' to '經', '务' to '務', '实' to '實', '总' to '總', '当' to '當',
        '选' to '選', '战' to '戰', '无' to '無', '术' to '術', '带' to '帶',
        '离' to '離', '灵' to '靈', '戏' to '戲', '动' to '動', '师' to '師',
        '机' to '機', '轮' to '輪', '阳' to '陽', '阴' to '陰', '银' to '銀',
        '错' to '錯', '语' to '語', '读' to '讀', '课' to '課', '饭' to '飯',
        '饮' to '飲', '养' to '養', '体' to '體', '号' to '號', '写' to '寫',
        '军' to '軍', '农' to '農', '贝' to '貝', '见' to '見', '觉' to '覺',
        '赛' to '賽', '红' to '紅', '绿' to '綠', '蓝' to '藍', '艺' to '藝',
        '张' to '張', '刘' to '劉', '陈' to '陳', '李' to '李', '王' to '王',
        '周' to '周', '吴' to '吳', '郑' to '鄭', '孙' to '孫', '黄' to '黃',
        '林' to '林', '何' to '何', '高' to '高', '梁' to '梁', '谢' to '謝',
        '许' to '許', '罗' to '羅', '宋' to '宋', '唐' to '唐', '韩' to '韓',
        '杨' to '楊', '朱' to '朱', '秦' to '秦', '尤' to '尤', '徐' to '徐',
        '蔡' to '蔡', '彭' to '彭', '萧' to '蕭', '潘' to '潘', '钟' to '鍾',
        '歌' to '歌', '曲' to '曲', '词' to '詞', '声' to '聲', '音' to '音'
    )

    private fun normalizeCN(s: String): String {
        return s.map { c -> CN_NORMALIZE[c] ?: c }.joinToString("")
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
