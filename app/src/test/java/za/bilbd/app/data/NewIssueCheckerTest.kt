package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「有没有新一期」的判定。
 *
 * 这块判错的两种后果都很难在真机上第一时间发现：
 *   · 判太松 → 每次打开都弹同一条通知（用户会以为坏了）
 *   · 判太严 → 新一期来了却不提醒（用户以为功能没做）
 * 所以把「取最新一期」和「该不该通知」都拆成纯函数钉住。
 */
class NewIssueCheckerTest {

    private fun issues(vararg ids: Int): String = buildString {
        append("[")
        ids.forEachIndexed { i, id ->
            if (i > 0) append(",")
            append("""{"issue_id":$id,"year":2026,"week":${40 + id % 7},"video_pubdate":${1_700_000_000 + id}}""")
        }
        append("]")
    }

    // ---------- 取最新一期 ----------

    @Test
    fun newest_picksMaxIdNotFirstElement() {
        // 线上数组不保证有序，不能只看 [0]
        val ref = NewIssueChecker.newest(issues(120, 127, 125))
        assertEquals(127, ref?.issueId)
    }

    @Test
    fun newest_readsYearWeekAndDate() {
        val ref = NewIssueChecker.newest(issues(127))
        assertEquals(127, ref?.issueId)
        assertEquals(2026, ref?.year)
        assertEquals(1_700_000_127L, ref?.date)
    }

    @Test
    fun newest_fallsBackToEndDate() {
        val json = """[{"issue_id":9,"year":2026,"week":3,"end_date":1690000000}]"""
        assertEquals(1_690_000_000L, NewIssueChecker.newest(json)?.date)
    }

    @Test
    fun newest_isNullForBadInput() {
        assertNull(NewIssueChecker.newest("not json"))
        assertNull(NewIssueChecker.newest("[]"))
        // issue_id 缺失/为 0 的条目直接跳过，不能当成「第 0 期」
        assertNull(NewIssueChecker.newest("""[{"year":2026}]"""))
    }

    // ---------- 该不该通知 ----------

    @Test
    fun firstRunNeverNotifies() {
        // 刚打开开关：只记住当前期号，不弹「你看第 127 期」
        val ref = NewIssueChecker.IssueRef(127, 2026, 46, 0)
        assertFalse(NewIssueChecker.shouldNotify(ref, lastNotified = 0))
    }

    @Test
    fun newerIssueNotifies() {
        val ref = NewIssueChecker.IssueRef(128, 2026, 47, 0)
        assertTrue(NewIssueChecker.shouldNotify(ref, lastNotified = 127))
    }

    @Test
    fun sameIssueDoesNotNotifyAgain() {
        val ref = NewIssueChecker.IssueRef(127, 2026, 46, 0)
        assertFalse(NewIssueChecker.shouldNotify(ref, lastNotified = 127))
    }

    @Test
    fun olderIssueNeverNotifies() {
        // 服务端回滚或数据异常时不该倒着提醒
        val ref = NewIssueChecker.IssueRef(125, 2026, 44, 0)
        assertFalse(NewIssueChecker.shouldNotify(ref, lastNotified = 127))
    }

    @Test
    fun nullIssueDoesNotNotify() {
        assertFalse(NewIssueChecker.shouldNotify(null, lastNotified = 127))
    }
}
