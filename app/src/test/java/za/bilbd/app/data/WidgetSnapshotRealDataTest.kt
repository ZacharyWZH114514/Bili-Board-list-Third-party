package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 拿**真实的内置快照**跑一遍小组件数据拼装。
 *
 * 加这个测试是因为一个只有真机才能发现的 bug：`Issue.labelDate` 是**秒**，
 * 4.4 的小组件按毫秒格式化，桌面上那行日期显示成了 `1970-01-22`。
 * 用手搓的假数据测不出来 —— 假数据里的时间戳是我自己填的，填什么都自洽。
 */
class WidgetSnapshotRealDataTest {

    private fun readAsset(name: String): String {
        // 单测直接读源码目录里的 assets，和 APK 里打包的是同一份文件
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name"),
        )
        val f = candidates.firstOrNull { it.exists() }
        assertNotNull("找不到 assets/$name（工作目录=${File(".").absolutePath}）", f)
        return f!!.readText(Charsets.UTF_8)
    }

    @Test
    fun realSnapshotProducesSaneWidgetData() {
        val snapshot = Parsers.parseSnapshot(readAsset("biliboard_snapshot.json"))
        val w = WidgetSnapshot.build(snapshot, dateOffsetDays = 0)
        assertNotNull("内置快照里应该能取到周榜最新一期", w)
        w!!

        // 1) 日期必须是 2020 年代，不是 1970 —— 这正是 4.4 的翻车点
        assertTrue("日期格式不对：${w.dateText}", Regex("""^\d{4}-\d{2}-\d{2}$""").matches(w.dateText))
        val year = w.dateText.substring(0, 4).toInt()
        assertTrue("日期跑到了 ${w.dateText}（时间戳单位搞错了？）", year >= 2020)

        // 2) 期号和前三名
        assertTrue("期号看着不对：${w.issueLabel}", w.issueLabel.contains("期"))
        assertEquals(WidgetSnapshot.MAX_ROWS, w.rows.size)
        assertEquals(1, w.rows.first().rank)
        assertTrue("曲名不该为空", w.rows.first().title.isNotBlank())
        assertTrue("播放量不该为空", w.rows.first().viewsText.isNotBlank())

        // 3) 日期偏移要真的平移，而且跨月也不能串
        val plus3 = WidgetSnapshot.build(snapshot, dateOffsetDays = 3)!!.dateText
        assertTrue("偏移后日期没变：${w.dateText} / $plus3", plus3 != w.dateText)
    }
}
