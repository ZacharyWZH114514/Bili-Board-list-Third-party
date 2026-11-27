package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件的数据拼装。
 *
 * 这块出错的后果很隐蔽：桌面上的小组件不会崩，只会显示错的东西（期号对不上、
 * 播放量写成 2324000、标题长到把布局撑爆），而且没人会天天盯着桌面检查。
 */
class WidgetSnapshotTest {

    private fun entry(
        rank: Int,
        title: String,
        producer: String,
        views: Long,
        bvid: String,
        titleCn: String? = null,
    ) = RankEntry(
        bvid = bvid,
        rank = rank,
        title = title,
        titleCn = titleCn,
        producers = listOf(Credit(name = producer, url = null)),
        vocalists = emptyList(),
        pubtime = 0L,
        firstRecordedAt = null,
        views = views,
        likes = 0, coins = 0, favorites = 0, score = 0,
        weeksOnBoard = 1, peakRank = rank, lastRank = null,
        rankChangeKind = null, rankChangeDiff = 0, rate = null,
    )

    private fun snapshot(vararg issues: Issue): Snapshot {
        val board = Board(id = 1, name = "周榜", issues = issues.toList())
        return Snapshot(generatedAt = "test", boards = listOf(board))
    }

    private fun issue(id: Int, entries: List<RankEntry>) = Issue(
        boardId = 1, boardName = "周榜", issueId = id, year = 2026, week = 46,
        videoCount = entries.size, endDate = 1_700_000_000_000L,
        videoBvid = null, entries = entries, videoPubdate = 1_700_086_400_000L,
    )

    // ---------- 取出前三 ----------

    @Test
    fun build_sortsByRankAndCachesUpToTheLimit() {
        // 缓存条数按 MAX_ROWS 来（小组件再按格子高度截取），这里只验证排序和上限
        val snap = snapshot(
            issue(
                127, listOf(
                    entry(3, "第三首", "P3", 100, "BV3"),
                    entry(1, "第一首", "P1", 300, "BV1"),
                    entry(2, "第二首", "P2", 200, "BV2"),
                    entry(4, "第四首", "P4", 50, "BV4"),
                )
            )
        )
        val w = WidgetSnapshot.build(snap)
        assertNotNull(w)
        assertEquals(listOf(1, 2, 3, 4), w!!.rows.map { it.rank })
        assertEquals("第一首", w.rows[0].title)
    }

    @Test
    fun build_capsAtMaxRows() {
        val many = (1..30).map { entry(it, "第${it}首", "P${it}", 100L + it, "BV${it}") }
        val w = WidgetSnapshot.build(snapshot(issue(127, many)))
        assertEquals(WidgetSnapshot.MAX_ROWS, w!!.rows.size)
        // 截取的是**名次靠前**的那几首，不是随便几首
        assertEquals(1, w.rows.first().rank)
        assertEquals(WidgetSnapshot.MAX_ROWS, w.rows.last().rank)
    }

    @Test
    fun build_usesLatestIssue() {
        val snap = snapshot(
            issue(126, listOf(entry(1, "旧的", "P", 1, "BV0"))),
            issue(127, listOf(entry(1, "新的", "P", 1, "BV1"))),
        )
        assertEquals("新的", WidgetSnapshot.build(snap)?.rows?.first()?.title)
        assertEquals("第 127 期", WidgetSnapshot.build(snap)?.issueLabel)
    }

    @Test
    fun build_returnsNullWhenNothingToShow() {
        assertNull(WidgetSnapshot.build(null))
        assertNull("没有期数", WidgetSnapshot.build(snapshot()))
        assertNull("期数为空", WidgetSnapshot.build(snapshot(issue(127, emptyList()))))
    }

    // ---------- 显示格式 ----------

    @Test
    fun viewsUseChineseUnits() {
        assertEquals("232.4万", WidgetSnapshot.formatViews(2_324_000))
        assertEquals("1亿", WidgetSnapshot.formatViews(100_000_000))
        assertEquals("2120.6万", WidgetSnapshot.formatViews(21_206_000))
        assertEquals("9999", WidgetSnapshot.formatViews(9999))
        assertEquals("1万", WidgetSnapshot.formatViews(10_000))
    }

    @Test
    fun cachesEnoughRowsForTallLayouts() {
        // 4×4 的格子里最多显示 6 条，缓存至少得留够，否则拉高后底下是空的
        assertEquals(18, WidgetSnapshot.MAX_ROWS)
    }

    @Test
    fun longTitlesAreTruncated() {
        val title = "这是一个特别特别长的日文歌名シニカルナイトプラン"
        val w = WidgetSnapshot.build(
            snapshot(issue(127, listOf(entry(1, title, "P", 1, "BV1"))))
        )
        val shown = w!!.rows[0].title
        assertTrue("没截断：$shown", shown.length <= 18)
        assertTrue("应该带省略号：$shown", shown.endsWith("…"))
    }

    @Test
    fun chineseTitleIsCarriedAndOptional() {
        val withCn = WidgetSnapshot.build(
            snapshot(issue(127, listOf(entry(1, "えねみぃ", "P", 1, "BV1", titleCn = "敌人"))))
        )!!
        assertEquals("敌人", withCn.rows[0].titleCn)

        // 没有译名的曲子不能变成 "null" 或空指针
        val noCn = WidgetSnapshot.build(
            snapshot(issue(127, listOf(entry(1, "えねみぃ", "P", 1, "BV1", titleCn = null))))
        )!!
        assertEquals("", noCn.rows[0].titleCn)
    }

    @Test
    fun dateHonoursOffsetDays() {
        // 日期以 labelDate（= videoPubdate）为准，偏移按自然日平移。
        // 这里不写死 "2026-11-11"：时间戳和时区一变，写死的断言只会误导人。
        val one = issue(127, listOf(entry(1, "t", "P", 1, "BV1")))
        val snap = snapshot(one)

        val base = WidgetSnapshot.build(snap, dateOffsetDays = 0)!!.dateText
        val plus2 = WidgetSnapshot.build(snap, dateOffsetDays = 2)!!.dateText
        val minus1 = WidgetSnapshot.build(snap, dateOffsetDays = -1)!!.dateText

        // labelDate 是**秒**，别再按毫秒算（4.4 就是把 1.79e9 秒当毫秒，日期跑到了 1970）
        assertEquals(WidgetSnapshot.formatDateSeconds(one.labelDate), base)
        assertEquals(WidgetSnapshot.formatDateSeconds(one.labelDate + 2 * 86_400L), plus2)
        assertEquals(WidgetSnapshot.formatDateSeconds(one.labelDate - 86_400L), minus1)
        assertTrue("偏移后日期不该相同", base != plus2 && base != minus1)
    }

    // ---------- 存取 ----------

    @Test
    fun jsonRoundTripKeepsEverything() {
        val original = WidgetSnapshot(
            boardName = "周榜",
            issueLabel = "第 127 期",
            dateText = "2026-11-11",
            rows = listOf(WidgetRow(1, "シスターに懐メモ", "向修女忏悔吧", "廃原メモリ", "41.9万", "BV1")),
            builtAt = 123L,
        )
        val back = WidgetSnapshot.fromJson(WidgetSnapshot.toJson(original))
        assertEquals(original, back)
    }

    @Test
    fun badJsonReturnsNullInsteadOfCrashing() {
        // 小组件进程读到的可能是上一次写到一半的脏数据，不能因此把桌面搞崩
        assertNull(WidgetSnapshot.fromJson(null))
        assertNull(WidgetSnapshot.fromJson(""))
        assertNull(WidgetSnapshot.fromJson("{oops"))
        assertNull(WidgetSnapshot.fromJson("""{"rows":[]}"""))
    }
}
