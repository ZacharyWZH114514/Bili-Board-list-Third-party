package za.bilbd.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/** 命中来源 */
enum class HitOrigin { RANKED, CATALOG }

/** 搜索结果（统一内置榜单 / 内置曲库两种来源） */
data class SongHit(
    val bvid: String,
    val title: String,
    val titleCn: String?,
    val producerText: String,
    val vocalistText: String,
    val chartCount: Int,
    val bestRank: Int,
    val views: Long,
    val origin: HitOrigin,
    val score: Int,
) {
    val coverUrl: String get() = BoardSource.cover(bvid)
}

/** 仓库状态 */
data class RepoState(
    val loading: Boolean = true,
    val snapshot: Snapshot? = null,
    val loadError: String? = null,
    val catalog: Catalog? = null,
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    // ---- 数据更新 ----
    /** 正在联网更新 */
    val updating: Boolean = false,
    /** 生效的「上次更新时间」（取本地记录与内置快照时间的较大者） */
    val lastUpdateAt: Long = 0L,
    /** 内置快照自身的生成时间 */
    val bundledAt: Long = 0L,
    /** 上一次更新的结果说明 */
    val lastMessage: String? = null,
    /** 上一次更新的错误 */
    val lastError: String? = null,
    /** 增量覆盖的期数 / 补充的曲目数 */
    val liveIssueCount: Int = 0,
    val liveSongCount: Int = 0,
    /** 自动更新周期（天）；0 = 仅手动更新，-1 = 每天固定 18:00 */
    val updateIntervalDays: Int = SettingsStore.DEFAULT_DAYS,
) {
    /** 是不是「每天 18:00」那一档 */
    val isDailyAt18: Boolean get() = updateIntervalDays == SettingsStore.DAILY_AT_18

    /** 周期换算成毫秒；仅手动时为 0 */
    val updateIntervalMs: Long
        get() = when {
            isDailyAt18 -> DAY_MS
            updateIntervalDays <= 0 -> 0L
            else -> updateIntervalDays * DAY_MS
        }

    val autoUpdateEnabled: Boolean get() = updateIntervalDays != SettingsStore.MANUAL_ONLY

    /**
     * 下次自动更新时间。
     * 「每天 18:00」这一档是按**墙上时间**算的：过了今天 18:00 就是明天 18:00，
     * 而不是「距上次更新满 24 小时」。
     */
    val nextUpdateAt: Long get() = nextUpdateAtFrom(System.currentTimeMillis())

    /** 是否到了该自动更新的时候 */
    val isDue: Boolean get() = isDueAt(System.currentTimeMillis())

    // 下面两个带 now 参数的版本是纯函数，方便单测覆盖 18:00 的边界情况

    fun nextUpdateAtFrom(now: Long): Long = when {
        !autoUpdateEnabled || lastUpdateAt <= 0 -> 0L
        isDailyAt18 -> {
            val today = todayAt(DAILY_HOUR, now)
            if (now < today) today else today + DAY_MS
        }
        else -> lastUpdateAt + updateIntervalMs
    }

    fun isDueAt(now: Long): Boolean = when {
        !autoUpdateEnabled -> false
        lastUpdateAt <= 0 -> true
        // 最近一个已经过去的 18:00 之后没更新过，就算到点了
        isDailyAt18 -> lastUpdateAt < lastDeadlineAt(DAILY_HOUR, now)
        else -> now - lastUpdateAt >= updateIntervalMs
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        /** 「每天 18:00」这一档用的时刻 */
        const val DAILY_HOUR = 18

        /** [now] 所在那一天（本地时区）的某个整点 */
        fun todayAt(hour: Int, now: Long = System.currentTimeMillis()): Long =
            java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

        /** 相对 [now] 最近、且已经过去的那个整点 */
        fun lastDeadlineAt(hour: Int, now: Long = System.currentTimeMillis()): Long {
            val today = todayAt(hour, now)
            return if (now >= today) today else today - DAY_MS
        }
    }
}

/** 一次更新的结果 */
data class UpdateResult(
    val ok: Boolean,
    val message: String,
    val addedIssues: Int = 0,
    val addedSongs: Int = 0,
)

/**
 * 数据仓库。
 *
 * 策略：内置快照是「构建时的基线」，App 每 3 天向 biliboard.uk 做一次增量更新：
 *   - 榜单：抓每个榜最新的若干期（含新期与最新一期的播放量变化）
 *   - 曲库：从第 1 页往后扫，收集新增曲目（接口按发布时间从新到旧返回）
 * 增量落盘后与基线合并，所以离线时依旧能浏览已更新过的内容。
 */
class BoardRepository(context: Context) {

    private val appContext = context.applicationContext
    private val api = BoardApi()
    private val store = LiveStore(appContext)
    private val settings = SettingsStore(appContext)

    private val _state = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = _state.asStateFlow()

    private val loadMutex = Mutex()
    private val catalogMutex = Mutex()
    private val updateMutex = Mutex()

    /** 别名索引：任意名称/别名（小写） -> 该实体的全部名称 */
    private var aliasIndex: Map<String, Set<String>> = emptyMap()

    /** bvid -> 曲库条目，供收藏页按 bvid 反查 */
    private var catalogIndex: Map<String, CatalogSong> = emptyMap()

    // ------------------------------------------------------------------
    // 载入（内置基线 + 本地增量）
    // ------------------------------------------------------------------

    suspend fun loadBundled() {
        if (_state.value.snapshot != null) return
        loadMutex.withLock {
            if (_state.value.snapshot != null) return
            try {
                // 读 2.25MB 资源 + 解析 JSON 都是阻塞操作，必须切到 IO，
                // 否则会占住主线程（实测冷启动白屏 7.7 秒、跳 341 帧）
                val loaded = withContext(Dispatchers.IO) {
                    val bundled = Parsers.parseSnapshot(readAsset(BoardSource.SNAPSHOT_ASSET))
                    val bundledAt = parseIsoMillis(bundled.generatedAt)
                    val liveJson = store.readSnapshot()
                    val live = if (liveJson.isNullOrBlank()) null else runCatching { Parsers.parseSnapshot(liveJson) }.getOrNull()
                    val merged = if (live == null) bundled else bundled.mergeWith(live.boards)
                    LoadedSnapshot(bundled, bundledAt, merged)
                }
                val (bundled, bundledAt, merged) = loaded
                val stored = store.lastUpdateAt
                val effectiveLast = maxOf(stored, bundledAt)
                store.lastUpdateAt = effectiveLast
                _state.update {
                    it.copy(
                        loading = false,
                        snapshot = merged,
                        loadError = null,
                        bundledAt = bundledAt,
                        lastUpdateAt = effectiveLast,
                        liveIssueCount = countLiveIssues(bundled, merged),
                        updateIntervalDays = settings.updateIntervalDays.value,
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, loadError = t.message ?: t.toString()) }
            }
        }
    }

    private data class LoadedSnapshot(
        val bundled: Snapshot,
        val bundledAt: Long,
        val merged: Snapshot,
    )

    /** 增量相比基线多出来的期数 */
    private fun countLiveIssues(bundled: Snapshot, merged: Snapshot): Int {
        var n = 0
        for (b in merged.boards) {
            val base = bundled.board(b.id)?.issues?.map { it.issueId }?.toSet() ?: emptySet()
            n += b.issues.count { it.issueId !in base }
        }
        return n
    }

    /** 懒加载全站曲库（离线检索用），基线 + 增量合并成一份 */
    suspend fun ensureCatalog(): Catalog? {
        _state.value.catalog?.let { return it }
        catalogMutex.withLock {
            _state.value.catalog?.let { return it }
            _state.update { it.copy(catalogLoading = true, catalogError = null) }
            return try {
                // 同样：2.13MB 曲库的读取、解析与建索引都放到 IO 线程
                withContext(Dispatchers.IO) {
                    val bundled = Parsers.parseCatalog(readAsset(BoardSource.CATALOG_ASSET))
                    val merged = mergeCatalog(bundled, readLiveCatalogSongs())
                    aliasIndex = buildAliasIndex(merged)
                    catalogIndex = HashMap<String, CatalogSong>(merged.songs.size).apply {
                        for (s in merged.songs) put(s.bvid, s)
                    }
                    bundled to merged
                }.let { (bundled, merged) ->
                    _state.update {
                        it.copy(
                            catalog = merged,
                            catalogLoading = false,
                            liveSongCount = (merged.songs.size - bundled.songs.size).coerceAtLeast(0),
                        )
                    }
                    merged
                }
            } catch (t: Throwable) {
                _state.update { it.copy(catalogLoading = false, catalogError = t.message ?: t.toString()) }
                null
            }
        }
    }

    private fun buildAliasIndex(cat: Catalog): Map<String, Set<String>> {
        val idx = HashMap<String, MutableSet<String>>()
        fun link(a: String, b: String) {
            val ka = a.trim().lowercase()
            val kb = b.trim().lowercase()
            if (ka.isEmpty() || kb.isEmpty()) return
            idx.getOrPut(ka) { LinkedHashSet() }.add(b)
            idx.getOrPut(kb) { LinkedHashSet() }.add(a)
        }
        for ((alias, canonical) in cat.aliases) link(alias, canonical)
        return idx
    }

    private fun readAsset(name: String): String =
        appContext.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** 改自动更新周期（天）；0 表示只在手动点「立即更新」时联网 */
    fun setUpdateIntervalDays(days: Int) {
        settings.setUpdateIntervalDays(days)
        _state.update { it.copy(updateIntervalDays = days) }
    }

    // ------------------------------------------------------------------
    // 更新
    // ------------------------------------------------------------------

    /** 到了更新周期就更新一次；否则什么都不做 */
    suspend fun autoUpdateIfDue(): UpdateResult? {
        val st = _state.value
        if (st.snapshot == null) return null
        if (!st.isDue) return null
        return updateNow()
    }

    /**
     * 立即执行一次增量更新。任何一步失败都不会破坏已有数据。
     */
    suspend fun updateNow(): UpdateResult = updateMutex.withLock {
        val base = _state.value.snapshot ?: return UpdateResult(false, "内置数据尚未载入")
        _state.update { it.copy(updating = true, lastError = null) }

        return try {
            val catalog = ensureCatalog()
            val known = HashSet<String>(catalog?.songs?.size ?: 0)
            catalog?.songs?.forEach { known.add(it.bvid) }
            readLiveCatalogSongs().forEach { known.add(it.bvid) }

            val issuesAdded = fetchRankings(base)
            val songsAdded = fetchNewSongs(known)

            val now = System.currentTimeMillis()
            store.lastUpdateAt = now
            store.lastUpdateOk = true
            val msg = buildString {
                if (issuesAdded == 0 && songsAdded == 0) append("已是最新，没有新增内容")
                else {
                    if (issuesAdded > 0) append("更新了 $issuesAdded 期榜单")
                    if (issuesAdded > 0 && songsAdded > 0) append("，")
                    if (songsAdded > 0) append("新增 $songsAdded 首曲目")
                }
            }
            _state.update {
                it.copy(
                    updating = false,
                    lastUpdateAt = now,
                    lastMessage = msg,
                    lastError = null,
                )
            }
            UpdateResult(true, msg, issuesAdded, songsAdded)
        } catch (t: Throwable) {
            val msg = t.message ?: t.toString()
            _state.update { it.copy(updating = false, lastError = msg) }
            UpdateResult(false, "更新失败：$msg")
        }
    }

    /**
     * 抓取每个榜最新的若干期排名，写回增量文件，并与当前状态合并。
     * 返回新增（此前没有的）期数。
     */
    private suspend fun fetchRankings(base: Snapshot): Int {
        val boardsArr = JSONArray(api.boardsRaw())
        val boardsJson = JSONArray()
        var added = 0

        for (i in 0 until boardsArr.length()) {
            val bj = boardsArr.optJSONObject(i) ?: continue
            val boardId = bj.optInt("id", 0)
            val boardName = bj.optString("name", "榜单 $boardId")

            val issuesArr = JSONArray(api.issuesRaw(boardId))
            val refs = (0 until issuesArr.length())
                .mapNotNull { issuesArr.optJSONObject(it) }
                .sortedByDescending { it.optInt("issue_id", 0) }
                .take(MAX_LIVE_ISSUES)

            if (refs.isEmpty()) continue
            val known = base.board(boardId)?.issues?.map { it.issueId }?.toSet() ?: emptySet()

            val issuesOut = JSONArray()
            for (ref in refs) {
                val issueId = ref.optInt("issue_id", 0)
                if (issueId <= 0) continue
                val rankings = api.rankingsRaw(boardId, issueId)
                val o = JSONObject(ref.toString())
                o.put("rankings", JSONArray(rankings))
                issuesOut.put(o)
                if (issueId !in known) added++
                delay(REQUEST_GAP_MS)
            }
            boardsJson.put(JSONObject().put("id", boardId).put("name", boardName).put("issues", issuesOut))
        }

        if (boardsJson.length() == 0) return 0

        val liveJson = JSONObject()
            .put("generatedAt", isoNow())
            .put("boards", boardsJson)
            .toString()
        store.writeSnapshot(liveJson)

        // 与基线合并后立刻反映到界面上
        val live = Parsers.parseSnapshot(liveJson)
        val merged = base.mergeWith(live.boards)
        _state.update {
            it.copy(
                snapshot = merged,
                liveIssueCount = countLiveIssues(base, merged),
            )
        }
        return added
    }

    /**
     * 曲库按发布时间从新到旧返回；从第 1 页往后扫，遇到整页都没有新曲目就停。
     * 返回新增曲目数。
     */
    private suspend fun fetchNewSongs(known: HashSet<String>): Int {
        val existing = readLiveCatalogRaw()
        val out = JSONArray()
        var added = 0
        var page = 1

        while (page <= MAX_CATALOG_PAGES) {
            val arr = runCatching { JSONObject(api.songsPageRaw(page, PAGE_SIZE)).optJSONArray("data") }
                .getOrNull() ?: break
            if (arr.length() == 0) break

            var pageAdded = 0
            for (j in 0 until arr.length()) {
                val o = arr.optJSONObject(j) ?: continue
                val bvid = o.optString("bvid", "")
                if (bvid.isEmpty() || !known.add(bvid)) continue
                out.put(o)
                pageAdded++
            }
            added += pageAdded
            // 整页都是已知曲目 -> 已经追上了已有数据
            if (pageAdded == 0) break
            page++
            delay(REQUEST_GAP_MS)
        }

        if (added == 0) return 0

        // 新的放前面，并限制总量，避免文件无限增长
        val merged = JSONArray()
        for (i in 0 until out.length()) merged.put(out.get(i))
        for (i in 0 until existing.length()) {
            if (merged.length() >= MAX_LIVE_SONGS) break
            merged.put(existing.get(i))
        }
        store.writeCatalog(
            JSONObject().put("generatedAt", isoNow()).put("data", merged).toString()
        )

        // 把新曲目并入内存里的曲库
        val newSongs = Parsers.parseRemoteSongs(
            JSONObject().put("data", out).toString()
        ).map { Parsers.remoteToCatalogSong(it) }
        _state.value.catalog?.let { current ->
            val mergedCatalog = mergeCatalog(current, newSongs)
            _state.update {
                it.copy(catalog = mergedCatalog, liveSongCount = it.liveSongCount + newSongs.size)
            }
        }
        return added
    }

    private fun readLiveCatalogRaw(): JSONArray = runCatching {
        val json = store.readCatalog() ?: return JSONArray()
        JSONObject(json).optJSONArray("data") ?: JSONArray()
    }.getOrDefault(JSONArray())

    private fun readLiveCatalogSongs(): List<CatalogSong> = runCatching {
        val raw = readLiveCatalogRaw()
        if (raw.length() == 0) return emptyList()
        Parsers.parseRemoteSongs(JSONObject().put("data", raw).toString())
            .map { Parsers.remoteToCatalogSong(it) }
    }.getOrDefault(emptyList())

    private fun mergeCatalog(bundled: Catalog, extra: List<CatalogSong>): Catalog {
        if (extra.isEmpty()) return bundled
        val seen = HashSet<String>(bundled.songs.size + extra.size)
        bundled.songs.forEach { seen.add(it.bvid) }
        val fresh = extra.filter { seen.add(it.bvid) }
        if (fresh.isEmpty()) return bundled
        return Catalog(
            generatedAt = bundled.generatedAt,
            total = bundled.total + fresh.size,
            songs = fresh + bundled.songs,
            aliases = bundled.aliases,
        )
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 按 bvid 查询曲目：优先内置榜单（信息最全），否则退回曲库 */
    fun lookupHit(bvid: String): SongHit? {
        _state.value.snapshot?.song(bvid)?.let { s ->
            return SongHit(
                bvid = s.bvid, title = s.title, titleCn = s.titleCn,
                producerText = s.producerText, vocalistText = s.vocalistText,
                chartCount = s.onBoardCount, bestRank = s.bestRank,
                views = s.latest?.views ?: 0L, origin = HitOrigin.RANKED, score = 0,
            )
        }
        catalogIndex[bvid]?.let { c ->
            return SongHit(
                bvid = c.bvid, title = c.title, titleCn = c.titleCn,
                producerText = c.producerText, vocalistText = c.vocalistText,
                chartCount = c.chartCount, bestRank = 0, views = 0L,
                origin = HitOrigin.CATALOG, score = 0,
            )
        }
        return null
    }

    // ------------------------------------------------------------------
    // P主 关注
    // ------------------------------------------------------------------

    /**
     * P主 → 曲目 的倒排索引，惰性构建。
     * 曲库 14000+ 首，每次点进 P主页都全表扫一遍太浪费；
     * 记录索引对应的曲库实例，曲库更新后自动重建。
     */
    private var producerIndexCache: Pair<Catalog, Map<String, List<CatalogSong>>>? = null

    private fun producerIndex(catalog: Catalog): Map<String, List<CatalogSong>> {
        producerIndexCache?.let { (c, idx) -> if (c === catalog) return idx }
        val built = ProducerIndex.build(catalog.songs)
        producerIndexCache = catalog to built
        return built
    }

    /** 某位 P主 的全部曲目，按投稿时间从新到旧。曲库没加载时返回空表。 */
    fun producerSongs(name: String): List<CatalogSong> {
        val catalog = _state.value.catalog ?: return emptyList()
        return producerIndex(catalog)[name.trim()].orEmpty()
    }

    /**
     * 已关注 P主 的最新投稿合并流，按投稿时间从新到旧，同一首只出现一次
     * （一首歌可能有多个 P主，而用户可能都关注了）。
     */
    fun followingFeed(names: Collection<String>): List<CatalogSong> {
        val catalog = _state.value.catalog ?: return emptyList()
        return ProducerIndex.mergeFeed(producerIndex(catalog), names)
    }

    /**
     * 曲库里投稿最多的 P主，用于在「关注」页给出推荐。
     * 没有关注任何人时给点可点的东西，比一句空话有用。
     */
    fun topProducers(limit: Int = 12): List<Pair<String, Int>> {
        val catalog = _state.value.catalog ?: return emptyList()
        return ProducerIndex.topProducers(producerIndex(catalog), limit)
    }

    // ------------------------------------------------------------------
    // 检索
    // ------------------------------------------------------------------

    private fun expandTerms(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val terms = LinkedHashSet<String>()
        terms.add(q)
        aliasIndex[q]?.forEach { terms.add(it.lowercase()) }
        return terms.toList()
    }

    /** 离线检索：先查榜单聚合曲目，再查全站曲库 */
    fun searchLocal(query: String): List<SongHit> {
        val terms = expandTerms(query)
        if (terms.isEmpty()) return emptyList()
        val snap = _state.value.snapshot
        val cat = _state.value.catalog
        val out = LinkedHashMap<String, SongHit>()

        snap?.songList?.forEach { s ->
            val sc = score(terms, s.title, s.titleCn, s.producers.map { it.name }, s.vocalists.map { it.name }, s.bvid, s.onBoardCount)
            if (sc > 0) {
                out[s.bvid] = SongHit(
                    bvid = s.bvid, title = s.title, titleCn = s.titleCn,
                    producerText = s.producerText, vocalistText = s.vocalistText,
                    chartCount = s.onBoardCount, bestRank = s.bestRank,
                    views = s.latest?.views ?: 0L, origin = HitOrigin.RANKED, score = sc + 100,
                )
            }
        }
        cat?.songs?.forEach { c ->
            if (out.containsKey(c.bvid)) return@forEach
            val sc = score(terms, c.title, c.titleCn, c.producers, c.vocalists, c.bvid, c.chartCount)
            if (sc > 0) {
                out[c.bvid] = SongHit(
                    bvid = c.bvid, title = c.title, titleCn = c.titleCn,
                    producerText = c.producerText, vocalistText = c.vocalistText,
                    chartCount = c.chartCount, bestRank = 0, views = 0L,
                    origin = HitOrigin.CATALOG, score = sc,
                )
            }
        }
        return out.values.sortedWith(
            compareByDescending<SongHit> { it.score }
                .thenByDescending { it.chartCount }
                .thenByDescending { it.bvid }
        ).take(MAX_HITS)
    }

    private fun score(
        terms: List<String>,
        title: String,
        titleCn: String?,
        producers: List<String>,
        vocalists: List<String>,
        bvid: String,
        chartCount: Int,
    ): Int {
        val t = title.lowercase()
        val tc = titleCn?.lowercase()
        val bl = bvid.lowercase()
        var best = 0
        for (term in terms) {
            val s = when {
                bl == term -> 1000
                t == term -> 900
                tc != null && tc == term -> 880
                t.startsWith(term) -> 700
                tc != null && tc.startsWith(term) -> 680
                t.contains(term) -> 520
                tc != null && tc.contains(term) -> 500
                producers.any { it.lowercase().contains(term) } -> 380
                vocalists.any { it.lowercase().contains(term) } -> 340
                else -> 0
            }
            if (s > best) best = s
        }
        if (best > 0 && chartCount > 0) best += 20
        return best
    }

    companion object {
        private const val MAX_HITS = 300

        /** 每次更新最多抓的期数（每个榜） */
        private const val MAX_LIVE_ISSUES = 6

        /** 每次更新最多扫的曲库页数 */
        private const val MAX_CATALOG_PAGES = 10
        private const val PAGE_SIZE = 100

        /** 增量曲目的总量上限 */
        private const val MAX_LIVE_SONGS = 3000

        /** 请求间隔，做个礼貌的限速 */
        private const val REQUEST_GAP_MS = 120L

        private fun isoNow(): String = Instant.now().toString()

        fun parseIsoMillis(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
        }

        private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

        fun formatTime(ms: Long): String = if (ms <= 0) "—" else timeFmt.format(Date(ms))

        /** 「3 天后」「12 小时后」这类相对描述 */
        fun formatRelative(ms: Long): String {
            val diff = ms - System.currentTimeMillis()
            if (diff <= 0) return "即将更新"
            val hours = diff / 3_600_000L
            return when {
                hours >= 24 -> "${hours / 24} 天后"
                hours >= 1 -> "${hours} 小时后"
                else -> "${diff / 60_000L} 分钟后"
            }
        }
    }
}
