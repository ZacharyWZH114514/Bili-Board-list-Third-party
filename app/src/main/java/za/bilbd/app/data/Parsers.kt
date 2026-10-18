package za.bilbd.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 解析。
 *
 * 内置快照使用精简后的键名（n/u/a、camcelCase），而 biliboard.uk 线上接口使用原始键名
 * （name/url/aliases、snake_case）。这里统一做成兼容读取，两种来源共用一套解析逻辑。
 */
object Parsers {

    // ---------------- 兼容读取工具 ----------------

    /** 缺失或显式 null 都返回 ""（Android 的 optString 会把 JSON null 读成字符串 "null"） */
    private fun JSONObject.str(vararg keys: String): String {
        for (k in keys) {
            if (!has(k) || isNull(k)) continue
            val v = optString(k, "")
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    private fun JSONObject.int(vararg keys: String): Int {
        for (k in keys) if (has(k) && !isNull(k)) return optInt(k, 0)
        return 0
    }

    private fun JSONObject.long(vararg keys: String): Long {
        for (k in keys) if (has(k) && !isNull(k)) return optLong(k, 0L)
        return 0L
    }

    private fun JSONObject.intOrNull(vararg keys: String): Int? {
        for (k in keys) if (has(k) && !isNull(k)) return optInt(k, 0)
        return null
    }

    private fun strList(arr: JSONArray?): List<String> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    private fun parseCredits(arr: JSONArray?): List<Credit> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<Credit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("n", "name")
            if (name.isBlank()) continue
            val url = o.str("u", "url", "wiki_url", "moegirl_url").ifBlank { null }
            val aliases = strList(o.optJSONArray("a") ?: o.optJSONArray("aliases"))
            out.add(Credit(name, url, aliases))
        }
        return out
    }

    // ---------------- 内置快照 ----------------

    fun parseSnapshot(json: String): Snapshot {
        val root = JSONObject(json)
        val generatedAt = root.str("generatedAt")
        val boardsArr = root.optJSONArray("boards") ?: JSONArray()
        val creditCache = HashMap<String, List<Credit>>()
        val boards = ArrayList<Board>(boardsArr.length())

        for (bi in 0 until boardsArr.length()) {
            val bj = boardsArr.optJSONObject(bi) ?: continue
            val boardId = bj.int("id", "board_id")
            val boardName = bj.str("name").ifBlank { "榜单 $boardId" }
            val issuesArr = bj.optJSONArray("issues") ?: JSONArray()
            val issues = ArrayList<Issue>(issuesArr.length())
            for (ii in 0 until issuesArr.length()) {
                val ij = issuesArr.optJSONObject(ii) ?: continue
                issues.add(parseIssue(ij, boardId, boardName, creditCache))
            }
            boards.add(Board(boardId, boardName, issues))
        }
        return Snapshot(generatedAt, Board.sorted(boards))
    }

    /** 解析某一期榜单（兼容内置格式与线上 /issues/{id}/rankings 返回的原始字段） */
    private fun parseIssue(
        ij: JSONObject,
        boardId: Int,
        boardName: String,
        creditCache: HashMap<String, List<Credit>>,
    ): Issue {
        val issueId = ij.int("issueId", "issue_id")
        val year = ij.int("year", "issue_year")
        val week = ij.int("week", "issue_week")
        val videoCount = ij.int("videoCount", "video_count")
        val endDate = ij.long("endDate", "end_date", "issueEndDate")
        val videoBvid = ij.str("videoBvid", "video_bvid").ifBlank { null }

        val rankArr = ij.optJSONArray("rankings") ?: JSONArray()
        val entries = ArrayList<RankEntry>(rankArr.length())
        for (ri in 0 until rankArr.length()) {
            val rj = rankArr.optJSONObject(ri) ?: continue
            entries.add(parseEntry(rj, creditCache))
        }
        return Issue(boardId, boardName, issueId, year, week, videoCount, endDate, videoBvid, entries)
    }

    private fun parseEntry(o: JSONObject, cache: HashMap<String, List<Credit>>): RankEntry {
        val pArr = o.optJSONArray("producers")
        val vArr = o.optJSONArray("vocalists")
        val producers = if (pArr == null) emptyList() else cache.getOrPut("p:${pArr}") { parseCredits(pArr) }
        val vocalists = if (vArr == null) emptyList() else cache.getOrPut("v:${vArr}") { parseCredits(vArr) }
        // 线上格式把统计放在 stats 子对象里，内置格式是平铺的
        val stats = o.optJSONObject("stats")
        val rankChange = o.optJSONObject("rankChange")
        return RankEntry(
            bvid = o.str("bvid"),
            rank = o.int("rank"),
            title = o.str("title"),
            titleCn = o.str("titleCn", "title_cn").ifBlank { null },
            producers = producers,
            vocalists = vocalists,
            pubtime = o.long("pubtime"),
            firstRecordedAt = o.long("firstRecordedAt", "first_recorded_at").takeIf { it > 0 },
            views = if (stats != null) stats.long("views") else o.long("views"),
            likes = if (stats != null) stats.long("likes") else o.long("likes"),
            coins = if (stats != null) stats.long("coins") else o.long("coins"),
            favorites = if (stats != null) stats.long("favorites") else o.long("favorites"),
            score = o.long("score"),
            weeksOnBoard = o.int("weeksOnBoard", "weeks_on_board"),
            peakRank = o.int("peakRank", "peak_rank"),
            lastRank = o.intOrNull("lastRank", "last_rank"),
            rankChangeKind = o.str("rankChangeKind").ifBlank { rankChange?.str("kind") ?: "" }.ifBlank { null },
            rankChangeDiff = if (o.has("rankChangeDiff")) o.int("rankChangeDiff") else (rankChange?.int("diff") ?: 0),
            rate = o.str("rate").ifBlank { null },
        )
    }

    /** 解析线上 /api/public/boards 返回的榜单目录 */
    fun parseBoardRefs(json: String): List<Board> {
        val arr = JSONArray(json)
        val out = ArrayList<Board>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Board(o.int("id", "board_id"), o.str("name"), emptyList()))
        }
        return out
    }

    /**
     * 解析线上 /api/public/boards/{id}/issues 返回的期列表。
     * 每一期还没有 rankings，entries 为空。
     */
    fun parseIssues(json: String, boardId: Int, boardName: String): List<Issue> {
        val arr = JSONArray(json)
        val out = ArrayList<Issue>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Issue(
                    boardId = boardId,
                    boardName = boardName,
                    // 每种拼写都列全：snake_case / camelCase 都认，避免服务端换写法时静默变成 0
                    issueId = o.int("issue_id", "issueId"),
                    year = o.int("year", "issue_year", "issueYear"),
                    week = o.int("week", "issue_week", "issueWeek"),
                    videoCount = o.int("video_count", "videoCount"),
                    endDate = o.long("end_date", "endDate"),
                    videoBvid = o.str("video_bvid", "videoBvid").ifBlank { null },
                    entries = emptyList(),
                )
            )
        }
        return out
    }

    /** 解析线上 /api/public/boards/{id}/issues/{issue}/rankings，返回带条目的完整一期 */
    fun parseRankings(json: String, board: Board, issue: Issue): Issue {
        val arr = JSONArray(json)
        val cache = HashMap<String, List<Credit>>()
        val entries = ArrayList<RankEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            entries.add(parseEntry(o, cache))
        }
        return issue.copy(entries = entries)
    }

    // ---------------- 全站曲库（离线搜索） ----------------

    /** 构造一条曲库条目（同时预计算小写检索文本） */
    fun catalogSongOf(
        bvid: String,
        title: String,
        titleCn: String?,
        producers: List<String>,
        vocalists: List<String>,
        pubtime: Long,
        chartCount: Int,
    ): CatalogSong {
        val sb = StringBuilder(72)
        sb.append(bvid.lowercase()).append('\u0001')
        sb.append(title.lowercase()).append('\u0001')
        titleCn?.let { sb.append(it.lowercase()).append('\u0001') }
        for (p in producers) sb.append(p.lowercase()).append('\u0001')
        for (v in vocalists) sb.append(v.lowercase()).append('\u0001')
        return CatalogSong(
            bvid = bvid,
            title = title,
            titleCn = titleCn,
            producers = producers,
            vocalists = vocalists,
            pubtime = pubtime,
            chartCount = chartCount,
            searchText = sb.toString(),
        )
    }

    /** 线上曲库条目 -> 内置曲库条目 */
    fun remoteToCatalogSong(r: RemoteSong): CatalogSong =
        catalogSongOf(r.bvid, r.title, r.titleCn, r.producers, r.vocalists, r.pubtime, r.chartCount)

    fun parseCatalog(json: String): Catalog {
        val root = JSONObject(json)
        val arr = root.optJSONArray("songs") ?: JSONArray()
        val songs = ArrayList<CatalogSong>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val bvid = o.str("b")
            if (bvid.isEmpty()) continue
            songs.add(
                catalogSongOf(
                    bvid = bvid,
                    title = o.str("t"),
                    titleCn = o.str("c").ifBlank { null },
                    producers = strList(o.optJSONArray("p")),
                    vocalists = strList(o.optJSONArray("v")),
                    pubtime = o.long("u"),
                    chartCount = o.optJSONArray("h")?.length() ?: 0,
                )
            )
        }
        val aliasObj = root.optJSONObject("aliases")
        val aliases = HashMap<String, String>()
        if (aliasObj != null) {
            val it = aliasObj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = aliasObj.optString(k, "")
                if (v.isNotEmpty()) aliases[k] = v
            }
        }
        return Catalog(root.str("generatedAt"), root.int("total"), songs, aliases)
    }

    /** 解析线上 /api/public/songs?search= 返回的 { data:[...], total, page, per_page } */
    fun parseRemoteSongs(json: String): List<RemoteSong> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<RemoteSong>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val bvid = o.str("bvid")
            if (bvid.isEmpty()) continue
            val prods = ArrayList<String>()
            o.optJSONArray("producers")?.let { pa ->
                for (j in 0 until pa.length()) pa.optJSONObject(j)?.let { prods.add(it.str("name", "n")) }
            }
            val vocs = ArrayList<String>()
            o.optJSONArray("vocalists")?.let { va ->
                for (j in 0 until va.length()) va.optJSONObject(j)?.let { vocs.add(it.str("name", "n")) }
            }
            out.add(
                RemoteSong(
                    bvid = bvid,
                    title = o.str("title"),
                    titleCn = o.str("title_cn", "titleCn").ifBlank { null },
                    producers = prods,
                    vocalists = vocs,
                    pubtime = o.long("pubtime"),
                    chartCount = o.optJSONArray("rank_history")?.length() ?: 0,
                )
            )
        }
        return out
    }
}

/** 线上曲库搜索结果 */
data class RemoteSong(
    val bvid: String,
    val title: String,
    val titleCn: String?,
    val producers: List<String>,
    val vocalists: List<String>,
    val pubtime: Long,
    val chartCount: Int,
) {
    val coverUrl: String get() = BoardSource.cover(bvid)
    val producerText: String get() = if (producers.isEmpty()) "未知P主" else producers.joinToString(" / ")
    val vocalistText: String get() = if (vocalists.isEmpty()) "未知歌手" else vocalists.joinToString(" / ")
}
