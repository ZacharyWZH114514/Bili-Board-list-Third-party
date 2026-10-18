package za.bilbd.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 应用内日志。
 *
 * 这里测的是「导出出去的东西对不对」——用户拿到 txt 时能不能看懂、
 * 会不会因为缓冲无限增长而把内存撑爆、导出文件名会不会互相覆盖。
 */
class AppLogTest {

    @Before
    fun setUp() {
        AppLog.resetForTest()
    }

    @After
    fun tearDown() {
        AppLog.resetForTest()
    }

    @Test
    fun add_keepsTagAndMessage() {
        AppLog.add("AI", "解读完成 provider=deepseek")
        val text = AppLog.exportText()
        assertTrue("要带 tag", text.contains("[AI]"))
        assertTrue("要带内容", text.contains("解读完成 provider=deepseek"))
    }

    @Test
    fun add_includesTimestamp() {
        AppLog.add("更新", "开始增量更新")
        // 形如 10-17 20:58:31
        assertTrue(
            "每行应带时间戳，否则导出后无法判断先后",
            Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2} """).containsMatchIn(AppLog.exportText()),
        )
    }

    @Test
    fun add_keepsInsertionOrder() {
        AppLog.add("A", "第一条")
        AppLog.add("A", "第二条")
        val lines = AppLog.exportText().trim().lines()
        assertTrue(lines[0].endsWith("第一条"))
        assertTrue(lines[1].endsWith("第二条"))
    }

    @Test
    fun buffer_doesNotGrowForever() {
        repeat(700) { AppLog.add("T", "第 $it 条") }
        // 上限 600：最早的应该被挤掉，最新的必须在
        assertEquals(600, AppLog.lines.value.size)
        assertTrue(AppLog.lines.value.last().endsWith("第 699 条"))
        assertTrue("最旧的应已淘汰", AppLog.lines.value.none { it.endsWith("第 0 条") })
    }

    @Test
    fun clear_emptiesTheBuffer() {
        AppLog.add("A", "x")
        AppLog.clear()
        assertTrue(AppLog.lines.value.isEmpty())
        assertEquals("\n".let { "" }, AppLog.exportText().trim())
    }

    @Test
    fun exportText_endsWithNewlineSoFilesLookRight() {
        AppLog.add("A", "x")
        assertTrue("导出的文本应以换行结尾，避免追加时黏在一起", AppLog.exportText().endsWith("\n"))
    }

    @Test
    fun suggestedFileName_isTxtAndUnique() {
        val name = AppLog.suggestedFileName()
        assertTrue("必须是 .txt", name.endsWith(".txt"))
        assertTrue("带上应用名，方便识别", name.startsWith("vocaloid-list-log-"))
        // 同一分钟内两次调用会相同（时间戳只到分钟），但格式要稳定
        assertTrue(Regex("""^vocaloid-list-log-\d{8}-\d{4}\.txt$""").matches(name))
    }

    @Test
    fun emptyLog_exportsNothingHarmful() {
        assertEquals("", AppLog.exportText())
        assertTrue(AppLog.lines.value.isEmpty())
    }
}
