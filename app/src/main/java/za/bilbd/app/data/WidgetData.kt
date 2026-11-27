package za.bilbd.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件的数据。
 *
 * 小组件跑在**另一个进程**（桌面/launcher 的 AppWidgetHost），所以不能指望它去读
 * 2.3MB 的快照、更不能临时联网 —— 那会把桌面卡住。这里的做法是：
 *   App 里数据一就绪，就把「小组件要显示的那几行」算好、存成一小段 JSON；
 *   小组件只读这段 JSON，几十字节，秒开。
 *
 * [build] 是纯函数，方便单测：标题截断、播放量缩写、日期偏移这些都在这里，
 * 出错的话小组件会显示成脏数据，比在 UI 上更容易被忽略。
 */
data class WidgetSnapshot(
    /** 榜单名，比如「周榜」 */
    val boardName: String,
    /** 期号短语，比如「第 127 期」 */
    val issueLabel: String,
    /** 期号日期，比如「2026-11-11」 */
    val dateText: String,
    /** 榜单前几名 */
    val rows: List<WidgetRow>,
    /** 这份数据是什么时候算出来的 */
    val builtAt: Long,
) {
    companion object {
        /**
         * 缓存里最多存几行。
         *
         * 具体显示几条由小组件按**当前格子高度**决定（4×2 显示 3 条、4×4 能到 6 条），
         * 所以这里多存一点，拉伸后才不会一拉高就露出空白。
         */
        const val MAX_ROWS = 18

        /**
         * 从快照里取「周榜最新一期」的前几名。
         *
         * @param dateOffsetDays 和 App 里「周榜日期偏移」保持一致，免得两边显示的日期不一样
         */
        fun build(
            snapshot: Snapshot?,
            dateOffsetDays: Int = 0,
            boardId: Int = DEFAULT_WIDGET_BOARD_ID,
            maxRows: Int = MAX_ROWS,
            now: Long = System.currentTimeMillis(),
        ): WidgetSnapshot? {
            // 用户可以在设置里选「周榜 / 传说曲周榜」；万一那个榜没数据，退回周榜
            val board = snapshot?.board(boardId) ?: snapshot?.board(WEEKLY_BOARD_ID) ?: return null
            val issue = board.latest ?: return null
            val rows = issue.entries
                .sortedBy { it.rank }
                .take(maxRows)
                .map { e ->
                    WidgetRow(
                        rank = e.rank,
                        title = shorten(e.title, MAX_TITLE),
                        titleCn = shorten(e.titleCn.orEmpty(), MAX_TITLE_CN),
                        artist = shorten(producerText(e), MAX_ARTIST),
                        viewsText = formatViews(e.views),
                        bvid = e.bvid,
                    )
                }
            if (rows.isEmpty()) return null
            return WidgetSnapshot(
                boardName = board.name.ifBlank { "周榜" },
                issueLabel = issue.label,
                dateText = formatDateSeconds(issue.labelDate + dateOffsetDays * DAY_SECONDS),
                rows = rows,
                builtAt = now,
            )
        }

        /** 周榜的 boardId（兜底用） */
        const val WEEKLY_BOARD_ID = 1

        /** 小组件默认显示周榜 */
        const val DEFAULT_WIDGET_BOARD_ID = 1
        /** 榜单里的时间戳是「秒」为单位，和 App 的 formatDate 保持一致，别自己发明毫秒 */
        private const val DAY_SECONDS = 86_400L
        private const val MAX_TITLE = 18
        private const val MAX_TITLE_CN = 16
        private const val MAX_ARTIST = 14

        /** RankEntry 上没有现成的 P主 文本（那是 Song 上的），自己拼一个 */
        internal fun producerText(e: RankEntry): String =
            if (e.producers.isEmpty()) "未知P主" else e.producers.joinToString(" / ") { it.name }

        /** 太长的标题在小格子里会换行挤爆布局，先按字符数截断 */
        internal fun shorten(text: String, max: Int): String {
            val t = text.trim()
            return if (t.length <= max) t else t.take(max - 1) + "…"
        }

        /** 播放量：和 App 里一样用「万 / 亿」，别在小组件上显示 2324000 这种长串 */
        internal fun formatViews(views: Long): String = when {
            views >= 100_000_000L -> fmt(views / 100_000_000.0) + "亿"
            views >= 10_000L -> fmt(views / 10_000.0) + "万"
            else -> views.toString()
        }

        private fun fmt(v: Double): String {
            val s = String.format(Locale.US, "%.1f", v)
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }

        /** 入参是**秒**（和 Issue.labelDate 同单位）。之前按毫秒处理，日期直接跑到 1970 */
        internal fun formatDateSeconds(sec: Long): String {
            if (sec <= 0L) return "—"
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sec * 1000L))
        }

        fun toJson(s: WidgetSnapshot): String = JSONObject().apply {
            put("boardName", s.boardName)
            put("issueLabel", s.issueLabel)
            put("dateText", s.dateText)
            put("builtAt", s.builtAt)
            put("rows", JSONArray().apply {
                s.rows.forEach {
                    put(JSONObject().apply {
                        put("rank", it.rank)
                        put("title", it.title)
                        put("titleCn", it.titleCn)
                        put("artist", it.artist)
                        put("viewsText", it.viewsText)
                        put("bvid", it.bvid)
                    })
                }
            })
        }.toString()

        fun fromJson(json: String?): WidgetSnapshot? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                val arr = o.optJSONArray("rows") ?: return null
                val rows = (0 until arr.length()).mapNotNull { i ->
                    val r = arr.optJSONObject(i) ?: return@mapNotNull null
                    WidgetRow(
                        rank = r.optInt("rank", 0),
                        title = r.optString("title", ""),
                        titleCn = r.optString("titleCn", ""),
                        artist = r.optString("artist", ""),
                        viewsText = r.optString("viewsText", ""),
                        bvid = r.optString("bvid", ""),
                    )
                }
                if (rows.isEmpty()) return null
                WidgetSnapshot(
                    boardName = o.optString("boardName", "周榜"),
                    issueLabel = o.optString("issueLabel", ""),
                    dateText = o.optString("dateText", ""),
                    rows = rows,
                    builtAt = o.optLong("builtAt", 0L),
                )
            }.getOrNull()
        }
    }
}

/** 小组件里的一行 */
data class WidgetRow(
    val rank: Int,
    /** 原曲名（多为日文） */
    val title: String,
    /** 中文译名；没有就是空串 */
    val titleCn: String,
    val artist: String,
    val viewsText: String,
    val bvid: String,
)

/** 小组件数据的小仓库：一段 JSON，存在自己的 SharedPreferences 里 */
object WidgetStore {
    private const val PREFS = "biliboard_widget"
    private const val KEY = "state"
    private const val KEY_ROWS = "rows"

    fun save(ctx: Context, snapshot: WidgetSnapshot) {
        runCatching {
            ctx.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, WidgetSnapshot.toJson(snapshot)).apply()
        }
    }

    /** 用户在设置里指定的显示条数；0 = 跟随尺寸自动 */
    fun rows(ctx: Context): Int = runCatching {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ROWS, 0)
    }.getOrDefault(0)

    fun saveRows(ctx: Context, rows: Int) {
        runCatching {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_ROWS, rows).apply()
        }
    }

    fun load(ctx: Context): WidgetSnapshot? = runCatching {
        WidgetSnapshot.fromJson(
            ctx.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
        )
    }.getOrNull()
}
