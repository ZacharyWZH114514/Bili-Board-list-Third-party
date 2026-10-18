package za.bilbd.app.data

/** 数据源站点 */
object BoardSource {
    const val BASE = "https://biliboard.uk"
    const val SNAPSHOT_ASSET = "biliboard_snapshot.json"
    const val CATALOG_ASSET = "catalog.json"
    const val UA = "BiliBoardAndroid/1.0 (Android; +https://biliboard.uk)"
    fun cover(bvid: String) = "$BASE/api/provider/biliimg/bvid/$bvid"
    fun biliVideo(bvid: String) = "https://www.bilibili.com/video/$bvid"
}

/** P主 / 歌手 */
data class Credit(
    val name: String,
    val url: String?,
    val aliases: List<String> = emptyList(),
) {
    /** 去掉与正名重复的别名，用于展示 */
    val extraAliases: List<String>
        get() = aliases.filter { !it.equals(name, ignoreCase = true) }
}

/** 名次变化 */
sealed interface Movement {
    data object New : Movement
    data object Same : Movement
    data class Up(val delta: Int) : Movement
    data class Down(val delta: Int) : Movement
}

/**
 * 一首曲目在某一期榜单上的一条记录。
 * 采集脚本把曲目信息（曲名/P主/歌手）一并冗余写进了每条排名里，解析时直接取用。
 */
data class RankEntry(
    val bvid: String,
    val rank: Int,
    val title: String,
    val titleCn: String?,
    val producers: List<Credit>,
    val vocalists: List<Credit>,
    val pubtime: Long,
    val firstRecordedAt: Long?,
    val views: Long,
    val likes: Long,
    val coins: Long,
    val favorites: Long,
    val score: Long,
    val weeksOnBoard: Int,
    val peakRank: Int,
    val lastRank: Int?,
    val rankChangeKind: String?,
    val rankChangeDiff: Int,
    val rate: String?,
) {
    /** 名次变化展示：NEW / ▲n / ▼n / — */
    val movement: Movement
        get() = when {
            lastRank == null || rankChangeKind == "new" -> Movement.New
            lastRank > rank -> Movement.Up(lastRank - rank)
            lastRank < rank -> Movement.Down(rank - lastRank)
            else -> Movement.Same
        }

    val coverUrl: String get() = BoardSource.cover(bvid)
}

/** 一期榜单 */
data class Issue(
    val boardId: Int,
    val boardName: String,
    val issueId: Int,
    val year: Int,
    val week: Int,
    val videoCount: Int,
    val endDate: Long,
    val videoBvid: String?,
    val entries: List<RankEntry>,
) {
    val label: String get() = "第 $issueId 期"
    val period: String get() = "$year 年第 $week 周"
    val champion: RankEntry? get() = entries.minByOrNull { it.rank }
}

/** 一个榜单（周榜 / 传说曲周榜 / 年榜） */
data class Board(
    val id: Int,
    val name: String,
    val issues: List<Issue>,
) {
    val sortedIssues: List<Issue> get() = issues.sortedByDescending { it.issueId }
    val latest: Issue? get() = sortedIssues.firstOrNull()

    companion object {
        fun sorted(boards: List<Board>): List<Board> = boards.sortedBy { it.id }
    }
}

/** 一首曲目的上榜记录点 */
data class ChartPoint(
    val boardId: Int,
    val boardName: String,
    val issueId: Int,
    val year: Int,
    val week: Int,
    val rank: Int,
    val views: Long,
    val likes: Long,
    val coins: Long,
    val favorites: Long,
    val score: Long,
)

/** 聚合后的曲目 */
data class Song(
    val bvid: String,
    val title: String,
    val titleCn: String?,
    val producers: List<Credit>,
    val vocalists: List<Credit>,
    val pubtime: Long,
    val firstRecordedAt: Long?,
    /** 按 issueId 升序 */
    val history: List<ChartPoint>,
) {
    val coverUrl: String get() = BoardSource.cover(bvid)
    val biliUrl: String get() = BoardSource.biliVideo(bvid)
    val producerText: String get() = if (producers.isEmpty()) "未知P主" else producers.joinToString(" / ") { it.name }
    val vocalistText: String get() = if (vocalists.isEmpty()) "未知歌手" else vocalists.joinToString(" / ") { it.name }
    val bestRank: Int get() = history.minOfOrNull { it.rank } ?: 0
    val latest: ChartPoint? get() = history.maxByOrNull { it.issueId }
    val onBoardCount: Int get() = history.size
}

/** 本地内置的完整榜单快照 */
class Snapshot(
    val generatedAt: String,
    val boards: List<Board>,
) {
    /** bvid -> 曲目 */
    val songs: Map<String, Song>
    val songList: List<Song>

    init {
        val map = LinkedHashMap<String, MutableSong>()
        for (board in boards) {
            for (issue in board.issues) {
                for (e in issue.entries) {
                    map.getOrPut(e.bvid) { MutableSong(e.bvid) }.add(board, issue, e)
                }
            }
        }
        val built = LinkedHashMap<String, Song>(map.size)
        for ((bvid, m) in map) built[bvid] = m.build()
        songs = built
        songList = built.values.toList()
    }

    val issueCount: Int get() = boards.sumOf { it.issues.size }
    val entryCount: Int get() = boards.sumOf { b -> b.issues.sumOf { it.entries.size } }

    fun board(id: Int): Board? = boards.firstOrNull { it.id == id }
    fun issue(boardId: Int, issueId: Int): Issue? =
        board(boardId)?.issues?.firstOrNull { it.issueId == issueId }

    fun song(bvid: String): Song? = songs[bvid]

    /** 与实时抓取到的数据合并（同号期以实时数据为准） */
    fun mergeWith(external: List<Board>): Snapshot {
        if (external.isEmpty()) return this
        val merged = ArrayList<Board>(boards.size + external.size)
        for (board in boards) {
            val extra = external.firstOrNull { it.id == board.id }
            if (extra == null) { merged.add(board); continue }
            val byId = LinkedHashMap<Int, Issue>()
            for (i in board.issues) byId[i.issueId] = i
            for (i in extra.issues) byId[i.issueId] = i
            merged.add(board.copy(issues = byId.values.toList()))
        }
        for (extra in external) if (merged.none { it.id == extra.id }) merged.add(extra)
        return Snapshot(generatedAt, Board.sorted(merged))
    }

    private class MutableSong(val bvid: String) {
        private var title: String = ""
        private var titleCn: String? = null
        private var producers: List<Credit> = emptyList()
        private var vocalists: List<Credit> = emptyList()
        private var pubtime: Long = 0L
        private var firstRecordedAt: Long? = null
        private val points = ArrayList<ChartPoint>(8)

        fun add(board: Board, issue: Issue, e: RankEntry) {
            if (title.isEmpty()) title = e.title
            if (titleCn.isNullOrEmpty()) titleCn = e.titleCn
            if (producers.isEmpty()) producers = e.producers
            if (vocalists.isEmpty()) vocalists = e.vocalists
            if (pubtime == 0L) pubtime = e.pubtime
            if (firstRecordedAt == null) firstRecordedAt = e.firstRecordedAt
            points.add(
                ChartPoint(
                    boardId = board.id, boardName = board.name,
                    issueId = issue.issueId, year = issue.year, week = issue.week,
                    rank = e.rank, views = e.views, likes = e.likes, coins = e.coins,
                    favorites = e.favorites, score = e.score,
                )
            )
        }

        fun build(): Song {
            points.sortBy { it.issueId }
            return Song(
                bvid = bvid,
                title = title.ifEmpty { bvid },
                titleCn = titleCn,
                producers = producers,
                vocalists = vocalists,
                pubtime = pubtime,
                firstRecordedAt = firstRecordedAt,
                history = points.toList(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 全站曲库（离线搜索用）
// ---------------------------------------------------------------------------

/** 曲库中的一首曲目（只有检索所需的字段） */
data class CatalogSong(
    val bvid: String,
    val title: String,
    val titleCn: String?,
    val producers: List<String>,
    val vocalists: List<String>,
    val pubtime: Long,
    /** 上榜次数 */
    val chartCount: Int,
    /** 预计算的小写检索文本 */
    val searchText: String,
) {
    val producerText: String get() = if (producers.isEmpty()) "未知P主" else producers.joinToString(" / ")
    val vocalistText: String get() = if (vocalists.isEmpty()) "未知歌手" else vocalists.joinToString(" / ")
}

/** 全站曲库 */
class Catalog(
    val generatedAt: String,
    val total: Int,
    val songs: List<CatalogSong>,
    /** 别名（小写） -> 正名 */
    val aliases: Map<String, String>,
)
