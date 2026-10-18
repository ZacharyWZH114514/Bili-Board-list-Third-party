package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据解析。
 *
 * 这是风险最高的一层：它把线上 JSON 变成整个应用的数据，
 * 解析错了就是「界面显示的东西不对」，而且不会崩、不会报错，只能靠对数据才能发现。
 *
 * 重点测 `Parsers` 的多别名容错——biliboard 不同接口返回的字段名
 * 有 snake_case 也有 camelCase，`int()/str()` 会依次尝试。
 */
class ParsersTest {

    // ---------- 榜单目录 ----------

    @Test
    fun boards_readsSnakeCase() {
        val boards = Parsers.parseBoardRefs(
            """[{"board_id":1,"name":"周榜"},{"board_id":2,"name":"传说曲周榜"}]"""
        )
        assertEquals(2, boards.size)
        assertEquals(1, boards[0].id)
        assertEquals("周榜", boards[0].name)
        assertEquals(2, boards[1].id)
    }

    @Test
    fun boards_readsCamelCase() {
        val boards = Parsers.parseBoardRefs("""[{"id":7,"name":"年榜"}]""")
        assertEquals(1, boards.size)
        assertEquals(7, boards[0].id)
        assertEquals("年榜", boards[0].name)
    }

    @Test
    fun boards_emptyArrayIsFine() {
        assertTrue(Parsers.parseBoardRefs("[]").isEmpty())
    }

    @Test
    fun boards_skipsNonObjectEntries() {
        val boards = Parsers.parseBoardRefs("""[null,{"board_id":1,"name":"A"},42,"x"]""")
        assertEquals(1, boards.size)
        assertEquals(1, boards[0].id)
    }

    @Test
    fun boards_missingFieldsFallBackSafely() {
        val boards = Parsers.parseBoardRefs("""[{"board_id":3}]""")
        assertEquals(1, boards.size)
        assertEquals(3, boards[0].id)
        assertEquals("", boards[0].name)
    }

    // ---------- 期列表 ----------

    @Test
    fun issues_readsSnakeCase() {
        val issues = Parsers.parseIssues(
            """[{"issue_id":122,"year":2026,"week":41,"video_count":20,"end_date":1700000000,"video_bvid":"BV1xx"}]""",
            boardId = 1,
            boardName = "周榜",
        )
        assertEquals(1, issues.size)
        val i = issues[0]
        assertEquals(122, i.issueId)
        assertEquals(2026, i.year)
        assertEquals(41, i.week)
        assertEquals(20, i.videoCount)
        assertEquals(1_700_000_000L, i.endDate)
        assertEquals("BV1xx", i.videoBvid)
        assertEquals(1, i.boardId)
        assertEquals("周榜", i.boardName)
    }

    @Test
    fun issues_readsCamelCase() {
        val issues = Parsers.parseIssues(
            """[{"issueId":9,"issueYear":2025,"issueWeek":3,"videoCount":5,"endDate":99,"videoBvid":"BV9"}]""",
            boardId = 2,
            boardName = "年榜",
        )
        assertEquals(1, issues.size)
        val i = issues[0]
        assertEquals(9, i.issueId)
        assertEquals(2025, i.year)
        assertEquals(3, i.week)
        assertEquals(5, i.videoCount)
        assertEquals(99L, i.endDate)
        assertEquals("BV9", i.videoBvid)
    }

    @Test
    fun issues_blankVideoBvidBecomesNull() {
        // 空串要归一成 null，否则界面会拿空串去拼 URL
        val issues = Parsers.parseIssues(
            """[{"issue_id":1,"video_bvid":""},{"issue_id":2}]""",
            boardId = 1,
            boardName = "周榜",
        )
        assertNull(issues[0].videoBvid)
        assertNull(issues[1].videoBvid)
    }

    @Test
    fun issues_entriesStartEmpty() {
        // 期列表里没有 rankings，解析时不该编造条目
        val issues = Parsers.parseIssues(
            """[{"issue_id":1,"year":2026,"week":1}]""",
            boardId = 1,
            boardName = "周榜",
        )
        assertTrue(issues[0].entries.isEmpty())
    }

    @Test
    fun issues_nullFieldDoesNotBecomeZeroString() {
        // Android 的 optString 会把 JSON null 读成字符串 "null"，必须被挡住
        val issues = Parsers.parseIssues(
            """[{"issue_id":5,"video_bvid":null}]""",
            boardId = 1,
            boardName = "周榜",
        )
        assertNull(issues[0].videoBvid)
        assertEquals(5, issues[0].issueId)
    }

    @Test
    fun issues_emptyArrayIsFine() {
        assertTrue(Parsers.parseIssues("[]", 1, "周榜").isEmpty())
    }

    @Test
    fun issues_labelAndPeriodAreReadable() {
        val issues = Parsers.parseIssues(
            """[{"issue_id":122,"year":2026,"week":41}]""",
            boardId = 1,
            boardName = "周榜",
        )
        val i = issues[0]
        assertTrue(i.label.contains("122"))
        assertTrue(i.period.contains("2026"))
        assertTrue(i.period.contains("41"))
    }
}
