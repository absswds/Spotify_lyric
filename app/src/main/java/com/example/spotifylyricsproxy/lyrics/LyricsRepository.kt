package com.example.spotifylyricsproxy.lyrics

import com.example.spotifylyricsproxy.core.AppSettings
import android.os.SystemClock
import android.util.Log
import com.example.spotifylyricsproxy.core.model.LrcLine
import com.example.spotifylyricsproxy.core.model.LyricCandidate
import com.example.spotifylyricsproxy.database.AppDatabase
import com.example.spotifylyricsproxy.database.entity.LyricCacheEntity
import com.example.spotifylyricsproxy.database.entity.RejectedLyricMatchEntity
import com.example.spotifylyricsproxy.database.entity.TrackPlayHistoryEntity
import com.example.spotifylyricsproxy.lyrics.LyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LrclibLyricsSource
import com.example.spotifylyricsproxy.lyrics.lrclib.LyricsSearchRequest
import com.example.spotifylyricsproxy.lyrics.amll.AmllTtmlLyricsSource
import com.example.spotifylyricsproxy.lyrics.netease.NeteaseLyricsSource
import com.example.spotifylyricsproxy.lyrics.qqmusic.QQMusicLyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class LyricsRepository private constructor(private val database: AppDatabase) {

    companion object {
        private const val TAG = "LyricsRepo"
        private const val RETRY_DELAY_MS = 60 * 60 * 1000L // 1 hour
        /**
         * Below this the best candidate matched the title but not the artist (e.g. another
         * singer's song of the same name): don't show it or cache it; offer a manual search.
         */
        private const val MIN_ACCEPT_SCORE = 50
        private const val EARLY_GRACE_MS = 600L
        /** NetEase YRC word timing: `[lineStart,lineDur](wordStart,wordDur,0)`. */
        private val YRC_HINT = Regex("""^\[\d+,\d+]\(\d+,\d+,\d+\)""", RegexOption.MULTILINE)
        private const val CONSENSUS_OK = 0.6
        private const val CONSENSUS_BAD = 0.3
        /** Per-provider cap so the fastest good answer is not held back by a slow one. */
        private const val SOURCE_TIMEOUT_MS = 5_000L
        private const val NETEASE_SOURCE = "netease"
        private const val QQ_MUSIC_SOURCE = "qqmusic"

        /**
         * Preferred source when scores are tied. LRCLIB returns the cleanest
         * LRC (pure lyrics, no "作曲/作词" info lines) and is the only source
         * whose lyrics are allowed into the offline cache.
         */
        private const val DEFAULT_SOURCE = "lrclib"

        @Volatile
        private var instance: LyricsRepository? = null

        /** Get or create the shared singleton, bound to the database. */
        fun getInstance(database: AppDatabase): LyricsRepository {
            return instance ?: synchronized(this) {
                instance ?: LyricsRepository(database).also { instance = it }
            }
        }
    }

    private val sources: List<LyricsSource> = listOf(
        AmllTtmlLyricsSource(),
        NeteaseLyricsSource(),
        QQMusicLyricsSource(),
        LrclibLyricsSource()
    )

    /**
     * Providers whose lyrics may be displayed during the current process but
     * never written to Room — offline cache therefore only ever contains
     * LRCLIB (and manually imported) lyrics.
     */
    private val memoryOnlySources = setOf(NETEASE_SOURCE, QQ_MUSIC_SOURCE)

    /** Query every source IN PARALLEL, collect all candidates, return all for scoring. */
    private suspend fun aggregateSearch(request: LyricsSearchRequest): List<LyricCandidate> = coroutineScope {
        val allCandidates = mutableListOf<LyricCandidate>()
        val results = kotlinx.coroutines.channels.Channel<List<LyricCandidate>>(sources.size)
        val jobs = sources.map { source ->
            launch { results.send(searchOne(source, request)) }
        }
        // Don't wait for the slowest source once the answer is settled: an exact AMLL
        // hit, or word-timed lyrics plus another source to cross-check their timing
        // (then only a short grace for stragglers).
        var deadline = SystemClock.uptimeMillis() + SOURCE_TIMEOUT_MS
        repeat(sources.size) {
            val remaining = deadline - SystemClock.uptimeMillis()
            val batch = (if (remaining > 0) withTimeoutOrNull(remaining) { results.receive() } else null)
                ?: return@repeat
            allCandidates.addAll(batch)
            if (allCandidates.any { it.source == AmllTtmlLyricsSource.SOURCE && !it.syncedLyrics.isNullOrEmpty() }) {
                deadline = 0L
            } else if (allCandidates.map { it.source }.distinct().size >= 2 &&
                allCandidates.any { c -> c.syncedLyrics?.let { YRC_HINT.containsMatchIn(it) || it.trimStart().startsWith("<tt") } == true }
            ) {
                deadline = minOf(deadline, SystemClock.uptimeMillis() + EARLY_GRACE_MS)
            }
        }
        jobs.forEach { it.cancel() }
        allCandidates
    }

    private suspend fun searchOne(source: LyricsSource, request: LyricsSearchRequest): List<LyricCandidate> =
        try {
            // One slow provider must not hold every other result hostage.
            val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) { source.search(request) }.orEmpty()
            if (result.isNotEmpty()) Log.i(TAG, "Source '${source.name}' returned ${result.size} candidates for ${request.trackName}")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Source '${source.name}' failed: ${e.message}")
            emptyList()
        }

    private val cacheDao = database.lyricCacheDao()
    private val historyDao = database.trackPlayHistoryDao()
    private val rejectedDao = database.rejectedLyricMatchDao()

    /**
     * Penalize cover/remix/fan versions so original recordings rank higher, and prefer
     * word-timed lyrics: an AMLL hit is keyed by the exact Spotify id, so it scores a full 100;
     * other word-level results get a small nudge over line-only ones of similar score.
     */
    private fun adjustForQuality(list: List<LyricCandidate>): List<LyricCandidate> {
        val parsed = list.map { c -> c.source to (c.syncedLyrics?.let { LrcParser.parse(it) } ?: emptyList()) }
        val agreement = LyricConsensus.bestAgreement(parsed)
        // Other sources agree with each other: a candidate that disagrees with all of
        // them is likely the wrong song or badly timed.
        val othersAgree = agreement.any { it != null && it >= CONSENSUS_OK }
        return list.mapIndexed { i, c ->
            var score = c.score
            if (LyricMatcher.looksLikeCover(c.trackName)) score -= 30
            val agree = agreement[i]
            if (c.source == AmllTtmlLyricsSource.SOURCE) {
                score = 100 // exact Spotify-id match
            } else {
                // Word timing is only worth preferring when its timing is confirmed
                // (or there is nothing to compare against).
                if (parsed[i].second.any { it.words.isNotEmpty() } && (agree == null || agree >= CONSENSUS_OK)) {
                    score = (score + 5).coerceAtMost(100)
                }
                if (othersAgree && agree != null && agree < CONSENSUS_BAD) score -= 25
                // Unconfirmed word timing must not win a tie against line-only lyrics.
                else if (agree != null && agree < CONSENSUS_OK && parsed[i].second.any { it.words.isNotEmpty() }) score -= 1
            }
            Log.i(TAG, "Quality: source=${c.source} base=${c.score} final=$score agree=$agree words=${parsed[i].second.any { it.words.isNotEmpty() }} lines=${parsed[i].second.size}")
            c.copy(score = score)
        }
    }

    /** Sort candidates by score desc; on ties, prefer the default source. */
    private fun sortCandidates(list: List<LyricCandidate>): List<LyricCandidate> =
        list.sortedWith(
            compareByDescending<LyricCandidate> { it.score }
                .thenByDescending { c -> c.syncedLyrics?.let { LrcParser.parse(it).any { l -> l.words.isNotEmpty() } } == true }
                .thenByDescending { it.source == DEFAULT_SOURCE }
        )

    init {
        // Enforce the session-only NetEase policy on existing installations too.
        // This runs once for the shared repository instance. QQ Music is also
        // memory-only: the offline cache should only ever hold LRCLIB lyrics.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            cacheDao.deleteBySource(NETEASE_SOURCE)
            cacheDao.deleteBySource(QQ_MUSIC_SOURCE)
        }
    }

    private val _parsedLyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val parsedLyrics: StateFlow<List<LrcLine>> = _parsedLyrics.asStateFlow()

    private val _currentLine = MutableStateFlow<LrcLine?>(null)
    val currentLine: StateFlow<LrcLine?> = _currentLine.asStateFlow()

    private val _lyricStatus = MutableStateFlow<LyricStatus>(LyricStatus.Idle)
    val lyricStatus: StateFlow<LyricStatus> = _lyricStatus.asStateFlow()

    /** Cached candidates for manual selection. */
    private val _candidates = MutableStateFlow<List<LyricCandidate>>(emptyList())
    val candidates: StateFlow<List<LyricCandidate>> = _candidates.asStateFlow()

    /** Source of the currently displayed lyrics: "cache", "lrclib", "netease", "manual", or empty. */
    private val _lyricSource = MutableStateFlow("")
    val lyricSource: StateFlow<String> = _lyricSource.asStateFlow()

    private var currentTrackId: String = ""
    private var lastSearchTitle: String = ""
    private var lastSearchArtist: String = ""
    private var lastSearchAlbum: String = ""
    private var lastSearchDurationMs: Long = 0L
    private var _offsetMs: Long = 0L

    // Expose offset so UI can display it
    private val _currentOffsetMs = MutableStateFlow(0L)
    val currentOffsetMs: StateFlow<Long> = _currentOffsetMs.asStateFlow()

    /** Bumped on every fetch; a fetch whose generation is no longer current must not publish. */
    @Volatile private var fetchGeneration = 0L

    fun getCurrentTrackId(): String = currentTrackId
    fun getOffsetMs(): Long = _offsetMs

    suspend fun fetchLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0,
        forceOnline: Boolean = false
    ) {
        if (title.isEmpty() || artist.isEmpty()) return
        val generation = ++fetchGeneration
        // A previous track's search may finish after this one started; drop its result.
        fun stale() = generation != fetchGeneration
        currentTrackId = trackId
        lastSearchTitle = title
        lastSearchArtist = artist
        lastSearchAlbum = album
        lastSearchDurationMs = durationMs
        _candidates.value = emptyList()
        // Clear previous song's lyrics immediately; show "Searching…" while
        // we check cache and query online sources.
        _parsedLyrics.value = emptyList()
        _currentLine.value = null
        _lyricStatus.value = LyricStatus.Searching

        // Load rejected lyric IDs for blacklist filtering
        val rejectedIds = withContext(Dispatchers.IO) {
            rejectedDao.getRejectedSourceLyricIds(trackId).toSet()
        }
        if (stale()) return

        // On unmetered (WiFi), skip cache and go directly to online sources.
        // Cache is only a fallback when every online source returns nothing.
        // On metered (mobile data), cache-first is still the default.
        var cached: LyricCacheEntity? = null
        if (!forceOnline) {
            cached = withContext(Dispatchers.IO) { cacheDao.getByTrackId(trackId) }
        } else {
            // WiFi still searches online, but show any cached lyrics right away
            // instead of a blank "Searching…" while every source answers.
            val preview = withContext(Dispatchers.IO) { cacheDao.getByTrackId(trackId) }
            if (stale()) return
            if (preview?.fetchStatus == "success" && preview.confidenceScore >= MIN_ACCEPT_SCORE) {
                val lines = preview.syncedLyrics?.let { LrcParser.parse(it) }.orEmpty()
                if (lines.isNotEmpty()) {
                    _offsetMs = preview.offsetMs
                    _currentOffsetMs.value = _offsetMs
                    _parsedLyrics.value = lines
                    _lyricStatus.value = LyricStatus.Synced(100)
                    _lyricSource.value = preview.source
                }
            }
        }
        if (stale()) return
        // Cached line-only lyrics are shown right away, but we still search online:
        // NetEase word timing is never cached, so it can only be found live.
        var upgrading = false
        if (cached != null) {
            _offsetMs = cached.offsetMs
            _currentOffsetMs.value = _offsetMs
            Log.i(TAG, "Cache hit: $title (status=${cached.fetchStatus}, source=${cached.source}, offset=${_offsetMs}ms)")
            updatePlayHistory(cached)

            // Manual lyrics always override: never re-search LRCLIB
            if (cached.source == "manual") {
                cached.syncedLyrics?.let { synced ->
                    val lines = LrcParser.parse(synced)
                    if (lines.isNotEmpty()) {
                        _parsedLyrics.value = lines
                        _lyricStatus.value = LyricStatus.Synced(100)
                        _lyricSource.value = cached.source
                        return
                    }
                }
            }

            when (cached.fetchStatus) {
                "success" -> if (cached.confidenceScore >= MIN_ACCEPT_SCORE) {
                    cached.syncedLyrics?.let { synced ->
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            _parsedLyrics.value = lines
                            _lyricStatus.value = LyricStatus.Synced(cached.confidenceScore)
                            _lyricSource.value = cached.source
                            if (lines.any { it.words.isNotEmpty() } || cached.source == AmllTtmlLyricsSource.SOURCE) return
                            upgrading = true
                        }
                    }
                } else {
                    Log.i(TAG, "Ignoring low-confidence cache (${cached.confidenceScore}) for $title")
                }
                "not_found", "failed" -> {
                    // Always re-search when playing a song, don't wait for retry timer
                    Log.d(TAG, "not_found/failed cache — re-searching for $title")
                }
                "plain_only" -> {
                    cached.syncedLyrics?.let { synced ->
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            _parsedLyrics.value = lines
                            _lyricStatus.value = LyricStatus.Synced(cached.confidenceScore)
                            _lyricSource.value = cached.source
                            return
                        }
                    }
                }
            }
        }

        // Not cached or needs refresh — keep old lyrics visible when forceOnline
        if (!forceOnline && !upgrading) {
            _lyricStatus.value = LyricStatus.Searching
        }

        try {
            val request = LyricsSearchRequest(
                trackName = title,
                artistName = artist,
                albumName = album,
                durationMs = durationMs,
                trackId = trackId
            )

            val candidates = withContext(Dispatchers.IO) { aggregateSearch(request) }
            if (stale()) return

            if (candidates.isEmpty()) {
                Log.w(TAG, "No lyrics found for: $title - $artist")
                // Fall back to cache on forceOnline, so WiFi users still see cached lyrics when online fails
                if (tryFallbackToCache(trackId)) return
                if (_parsedLyrics.value.isNotEmpty()) return // cached preview already showing
                cacheNotfound(trackId, title, artist, album, durationMs)
                _parsedLyrics.value = emptyList()
                _lyricStatus.value = LyricStatus.NotFound
                return
            }

            // Score, filter rejected, sort
            var scored = candidates.map {
                LyricMatcher.score(it, title, artist, album, durationMs)
            }
            // Penalize cover/remix/fan versions so original recordings rank higher.
            // The NetEase cloudsearch/pc endpoint frequently returns covers as top results,
            // and artist matching via contains() can let wrong matches through (e.g., "周杰伦-" vs "周杰伦").
            scored = adjustForQuality(scored)
            // DEBUG: dump every candidate's score breakdown
            scored.forEach { c ->
                Log.i(TAG, "Candidate: source=${c.source} title='${c.trackName}' artist='${c.artistName}' score=${c.score} synced=${!c.syncedLyrics.isNullOrEmpty()}")
            }
            // Sort so the best match is deterministic: score desc, ties to
            // the default source. filterRejected keeps order, so maxByOrNull
            // below picks the sorted head.
            scored = sortCandidates(scored)
            val filtered = LyricMatcher.filterRejected(scored, rejectedIds)

            if (filtered.isEmpty()) {
                Log.w(TAG, "All candidates rejected for: $title - $artist")
                // Keep the scored list so the user can still manually choose if needed
                _candidates.value = sortCandidates(scored)
                _parsedLyrics.value = emptyList()
                _lyricStatus.value = LyricStatus.LowConfidence(0)
                return
            }

            val best = filtered.maxByOrNull { it.score }!!
            _candidates.value = sortCandidates(scored)
            Log.i(TAG, "Best match: ${best.trackName} (score: ${best.score})")
            if (best.score < MIN_ACCEPT_SCORE) {
                // Probably a different song with the same title: wrong lyrics are worse
                // than none. Keep the candidates for the manual picker; cache nothing.
                Log.w(TAG, "Best match too weak (${best.score}) for $title - $artist")
                _parsedLyrics.value = emptyList()
                _lyricStatus.value = LyricStatus.LowConfidence(best.score)
                return
            }

            // Cache the result
            cacheResult(trackId, title, artist, album, durationMs, best)
            if (stale()) return

            // Show lyrics regardless of score. Score is only for candidate ranking,
            // never for hiding content.
            val syncedLyrics = best.syncedLyrics
            if (syncedLyrics.isNullOrEmpty()) {
                _parsedLyrics.value = emptyList()
                _lyricStatus.value = LyricStatus.PlainOnly
                return
            }

            val lines = LrcParser.parse(syncedLyrics, best.translation)
            if (lines.isEmpty()) {
                _parsedLyrics.value = emptyList()
                _lyricStatus.value = LyricStatus.ParseError
                return
            }

            _parsedLyrics.value = lines
            _lyricStatus.value = LyricStatus.Synced(best.score)
            _lyricSource.value = best.source

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (stale()) return
            Log.e(TAG, "Failed to fetch lyrics", e)
            _lyricStatus.value = LyricStatus.Error(e.message ?: "未知错误")
        }
    }

    /** Manually select a candidate by index from [candidates]. */
    suspend fun selectCandidate(index: Int) {
        val list = _candidates.value
        if (index < 0 || index >= list.size) return
        val candidate = list[index]
        Log.i(TAG, "Manual select: source=${candidate.source} title=${candidate.trackName} score=${candidate.score} hasSynced=${!candidate.syncedLyrics.isNullOrEmpty()}")

        // Clear the previous provider's line before applying the new candidate.
        // Without this, a plain-only or invalid LRC candidate left the old NetEase
        // line visible in MediaSession/notification while the playback UI was empty.
        _parsedLyrics.value = emptyList()
        _currentLine.value = null
        _lyricSource.value = candidate.source

        cacheResult(
            trackId = currentTrackId,
            title = candidate.trackName,
            artist = candidate.artistName,
            album = candidate.albumName,
            durationMs = candidate.durationMs,
            // The user picked it: trust it regardless of the automatic score.
            best = candidate.copy(score = 100)
        )

        val synced = candidate.syncedLyrics
        if (synced.isNullOrEmpty()) {
            Log.w(TAG, "Manual select has no synced lyrics: source=${candidate.source}")
            _lyricStatus.value = LyricStatus.PlainOnly
            return
        }

        val lines = LrcParser.parse(synced, candidate.translation)
        Log.i(TAG, "Manual select: source=${candidate.source} syncedLen=${synced.length} parsedLines=${lines.size}")
        if (lines.isEmpty()) {
            Log.w(TAG, "Manual select LRC parsed 0 lines: source=${candidate.source}")
            _lyricStatus.value = LyricStatus.ParseError
            return
        }

        _parsedLyrics.value = lines
        _lyricStatus.value = LyricStatus.Synced(candidate.score)
        Log.i(TAG, "Manual select applied ${lines.size} synced lines: source=${candidate.source}")
    }

    /** Re-fetch and re-score from network, then update candidates. */
    suspend fun reSearch(
        title: String = "",
        artist: String = "",
        album: String = "",
        durationMs: Long = 0
    ) {
        val t = currentTrackId.ifBlank { return }
        android.util.Log.i(TAG, "reSearch: trackId=$t lastTitle=$lastSearchTitle lastArtist=$lastSearchArtist")
        // Clear all rejections for this track — user explicitly asked for a fresh search.
        withContext(Dispatchers.IO) { rejectedDao.clearForTrack(t) }
        val rejectedIds = emptySet<String>()

        _lyricStatus.value = LyricStatus.Searching
        try {
            val cached = withContext(Dispatchers.IO) { cacheDao.getByTrackId(t) }
            val searchTitle = title.ifBlank {
                cached?.title?.takeIf { it.isNotBlank() } ?: lastSearchTitle.ifBlank { return }
            }
            val searchArtist = artist.ifBlank {
                cached?.artist?.takeIf { it.isNotBlank() } ?: lastSearchArtist.ifBlank { return }
            }
            val searchAlbum = album.ifBlank { cached?.album?.takeIf { it.isNotBlank() } ?: lastSearchAlbum }
            val searchDuration = if (durationMs > 0) durationMs else (cached?.durationMs ?: lastSearchDurationMs)

            var results = withContext(Dispatchers.IO) {
                aggregateSearch(LyricsSearchRequest(
                    trackName = searchTitle,
                    artistName = searchArtist,
                    albumName = searchAlbum,
                    durationMs = searchDuration,
                    trackId = t
                ))
            }
            if (results.isEmpty()) {
                // Cross-device fallback: the App Remote track info may be stale
                // when playback is on a Spotify Connect device (tablet). Try
                // searching with just the title (no artist) to increase recall.
                Log.i(TAG, "reSearch returned 0 results for '$searchTitle $searchArtist', trying title-only")
                val titleOnlyResults = withContext(Dispatchers.IO) {
                    aggregateSearch(LyricsSearchRequest(
                        trackName = searchTitle,
                        artistName = ""
                    ))
                }
                if (titleOnlyResults.isEmpty()) {
                    _lyricStatus.value = LyricStatus.NotFound
                    _candidates.value = emptyList()
                    return
                }
                results = titleOnlyResults
            }
            var scored = results.map {
                LyricMatcher.score(it, searchTitle, searchArtist, searchAlbum, searchDuration)
            }
            // Penalize cover/remix/fan versions so original recordings rank higher.
            scored = adjustForQuality(scored)
            scored = sortCandidates(scored)
            val filtered = LyricMatcher.filterRejected(scored, rejectedIds)

            _candidates.value = scored

            val best = filtered.maxByOrNull { it.score }
            if (best != null && LyricMatcher.isAutoAccept(best.score)) {
                cacheResult(t, searchTitle, searchArtist, searchAlbum, searchDuration, best)
                val synced = best.syncedLyrics
                if (!synced.isNullOrEmpty()) {
                    _parsedLyrics.value = LrcParser.parse(synced, best.translation)
                    _lyricStatus.value = LyricStatus.Synced(best.score)
                    Log.i(TAG, "reSearch auto-accepted: ${best.trackName} score=${best.score} lines=${_parsedLyrics.value.size}")
                } else {
                    _lyricStatus.value = LyricStatus.PlainOnly
                }
            } else if (best != null && best.score >= MIN_ACCEPT_SCORE) {
                val synced = best.syncedLyrics
                if (!synced.isNullOrEmpty()) {
                    _parsedLyrics.value = LrcParser.parse(synced, best.translation)
                    _lyricStatus.value = LyricStatus.Synced(best.score)
                    Log.i(TAG, "reSearch accepted ${best.trackName} score=${best.score} lines=${_parsedLyrics.value.size}")
                } else {
                    _lyricStatus.value = LyricStatus.PlainOnly
                }
            } else if (scored.isNotEmpty()) {
                _parsedLyrics.value = emptyList()
                _lyricStatus.value = LyricStatus.LowConfidence(scored.first().score)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Re-search failed", e)
            _lyricStatus.value = LyricStatus.Error(e.message ?: "重新搜索失败")
        }
    }

    /** Mark the current best match as wrong and blacklist it. */
    suspend fun rejectCurrentMatch(reason: String = "") {
        val candidates = _candidates.value
        if (candidates.isEmpty()) return
        val best = candidates.first()
        val entity = RejectedLyricMatchEntity(
            spotifyTrackId = currentTrackId,
            source = best.source,
            sourceLyricId = best.id.toString(),
            reason = reason.ifBlank { "用户手动标记为错误" }
        )
        withContext(Dispatchers.IO) {
            rejectedDao.insert(entity)
        }
        Log.i(TAG, "Rejected match: ${best.trackName} (reason=$reason)")

        // Clear current lyrics so user sees empty state
        _parsedLyrics.value = emptyList()
        _currentLine.value = null
        _lyricStatus.value = LyricStatus.NotFound

        // Re-run scoring with the new rejection
        reSearch()
    }

    /** Set per-track offset and save to database. */
    suspend fun setOffsetMs(offsetMs: Long) {
        _offsetMs = offsetMs
        _currentOffsetMs.value = offsetMs
        if (currentTrackId.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val entity = cacheDao.getByTrackId(currentTrackId)
                if (entity != null) {
                    cacheDao.upsert(entity.copy(offsetMs = offsetMs))
                }
            }
        }
    }

    /** Adjust offset by [deltaMs] (positive = later, negative = earlier). */
    suspend fun adjustOffset(deltaMs: Long) {
        setOffsetMs(_offsetMs + deltaMs)
    }

    fun updatePosition(positionMs: Long) {
        val lines = _parsedLyrics.value
        if (lines.isEmpty()) return
        // Apply offset: adjust the playback position by the offset
        // Positive offset = lyrics appear later, so we look at an earlier position.
        // The per-song offset stacks on top of the global one from Settings.
        val adjustedPosition = (positionMs - _offsetMs - AppSettings.globalOffsetMs.value).coerceAtLeast(0)
        val line = LyricSyncEngine.findCurrentLine(lines, adjustedPosition)
        if (line != _currentLine.value) {
            _currentLine.value = line
        }
    }

    private suspend fun cacheResult(
        trackId: String, title: String, artist: String,
        album: String, durationMs: Long, best: LyricCandidate
    ) {
        // NetEase lyrics are session-only by policy: display them in memory but never persist them.
        if (best.source in memoryOnlySources) {
            Log.i(TAG, "Skipping Room cache for memory-only source=${best.source}")
            return
        }

        val fetchStatus = when {
            best.syncedLyrics != null -> "success"
            best.plainLyrics != null -> "plain_only"
            else -> "not_found"
        }
        val entity = LyricCacheEntity(
            spotifyTrackId = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            syncedLyrics = best.syncedLyrics,
            plainLyrics = best.plainLyrics,
            fetchStatus = fetchStatus,
            confidenceScore = best.score,
            source = best.source,
            offsetMs = _offsetMs
        )
        withContext(Dispatchers.IO) { cacheDao.upsert(entity) }
    }

    private suspend fun cacheNotfound(
        trackId: String, title: String, artist: String,
        album: String, durationMs: Long
    ) {
        val entity = LyricCacheEntity(
            spotifyTrackId = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            fetchStatus = "not_found",
            nextRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS
        )
        withContext(Dispatchers.IO) { cacheDao.upsert(entity) }
    }

    private suspend fun updatePlayHistory(cached: LyricCacheEntity) {
        withContext(Dispatchers.IO) {
            cacheDao.upsert(
                cached.copy(lastPlayedAt = System.currentTimeMillis())
            )
            historyDao.upsert(
                TrackPlayHistoryEntity(
                    spotifyTrackId = cached.spotifyTrackId,
                    title = cached.title,
                    artist = cached.artist,
                    album = cached.album,
                    durationMs = cached.durationMs,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Save manually imported lyrics for a track.
     * Once saved, fetchLyrics will always return these lyrics and never re-search online sources.
     */
    suspend fun saveManualLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0,
        lrcText: String
    ) {
        val lines = LrcParser.parse(lrcText)
        if (lines.isEmpty()) {
            Log.w(TAG, "saveManualLyrics: LRC text produced 0 lines")
            return
        }
        val entity = LyricCacheEntity(
            spotifyTrackId = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            source = "manual",
            syncedLyrics = lrcText,
            plainLyrics = null,
            fetchStatus = "success",
            confidenceScore = 100,
            nextRetryAt = null,
            offsetMs = 0L
        )
        withContext(Dispatchers.IO) { cacheDao.upsert(entity) }
        _parsedLyrics.value = lines
        _lyricStatus.value = LyricStatus.Synced(100)
        Log.i(TAG, "Manual lyrics saved: $title (${lines.size} lines)")
    }

    fun reset() {
        _parsedLyrics.value = emptyList()
        _currentLine.value = null
        _lyricStatus.value = LyricStatus.Idle
        _candidates.value = emptyList()
        _offsetMs = 0L
        _currentOffsetMs.value = 0L
        currentTrackId = ""
    }

    /** Set status to indicate online search was skipped because of metered connection. */
    fun setMobileDataRestricted() {
        _lyricStatus.value = LyricStatus.MobileDataRestricted
    }

    /**
     * Try to load cached lyrics for [trackId]. Returns true if cache was found and applied.
     * Used as fallback when [forceOnline] search returns nothing.
     */
    private suspend fun tryFallbackToCache(trackId: String): Boolean {
        val cached = withContext(Dispatchers.IO) { cacheDao.getByTrackId(trackId) }
        if (cached == null) {
            Log.d(TAG, "tryFallbackToCache: no cache entry for $trackId")
            return false
        }
        if (cached.fetchStatus != "success" && cached.fetchStatus != "plain_only") {
            Log.d(TAG, "tryFallbackToCache: cache entry status=${cached.fetchStatus}, no usable lyrics")
            return false
        }
        val synced = cached.syncedLyrics
        if (synced == null) {
            Log.d(TAG, "tryFallbackToCache: cache has status=${cached.fetchStatus} but no syncedLyrics")
            return false
        }
        val lines = LrcParser.parse(synced)
        if (lines.isEmpty()) return false
        _offsetMs = cached.offsetMs
        _currentOffsetMs.value = _offsetMs
        _parsedLyrics.value = lines
        _lyricStatus.value = LyricStatus.Synced(cached.confidenceScore)
        _lyricSource.value = cached.source
        Log.d(TAG, "Fallback to cache for $trackId (source=${cached.source})")
        return true
    }
}

sealed class LyricStatus {
    data object Idle : LyricStatus()
    data object Searching : LyricStatus()
    data class Synced(val score: Int) : LyricStatus()
    data object PlainOnly : LyricStatus()
    data object NotFound : LyricStatus()
    data object ParseError : LyricStatus()
    data class LowConfidence(val score: Int) : LyricStatus()
    data class Error(val message: String) : LyricStatus()
    data object MobileDataRestricted : LyricStatus()
}
