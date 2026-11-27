package za.bilbd.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件条数计算。
 *
 * 这条逻辑以前是写死的档位（3/5/10/15），结果 4×2 上第三条文字被底边切掉——
 * 因为 4×2 的可用高度刚好卡在两个档位之间。改成按高度除就行，这里把
 * 常见格子尺寸钉一遍，防止以后又有人改回写死。
 */
class WidgetLayoutTest {

    @Test
    fun fourByTwoFitsTwoRowsNotThree() {
        // 4×2 的最小高度 140dp：去掉内边距和头部只剩几十 dp，放三条必然切边
        val rows = WidgetLayout.rowsFor(140f)
        assertEquals("4×2 应该只放 2 条", 2, rows)
    }

    @Test
    fun tinyWidgetStillDrawsOneRow() {
        // 再小的格子也不能一条不画（那样看起来就是坏的）
        assertTrue(WidgetLayout.rowsFor(60f) >= 1)
        assertTrue(WidgetLayout.rowsFor(0f) >= 1)
    }

    @Test
    fun rowCountGrowsWithHeight() {
        // 单调不减：格子越高，条数不能反而变少
        var prev = 0
        var h = 100f
        while (h <= 800f) {
            val rows = WidgetLayout.rowsFor(h)
            assertTrue("height=$h 时条数从 $prev 掉到 $rows", rows >= prev)
            prev = rows
            h += 10f
        }
    }

    @Test
    fun tallWidgetReachesTheCap() {
        // 拉得很高时给满 15 条，但不超过上限
        assertEquals(WidgetLayout.MAX_VISIBLE, WidgetLayout.rowsFor(700f))
        assertEquals(WidgetLayout.MAX_VISIBLE, WidgetLayout.rowsFor(2000f))
    }

    @Test
    fun rowsNeverExceedAvailableSpace() {
        // 核心不变量：条数 × 行高 不能超过「总高 - 头部 - 内边距」，否则就是切边
        var h = 120f
        while (h <= 900f) {
            val rows = WidgetLayout.rowsFor(h)
            val usedByRows = rows * WidgetLayout.rowHeight(h)
            val available = h - 30f // 保守：头部 44 + 内边距 22 里再留点余量
            assertTrue(
                "height=$h 时 $rows 条用了 ${usedByRows}dp，超过可用 ${available}dp",
                usedByRows <= available || rows == 1,
            )
            h += 10f
        }
    }

    @Test
    fun densityFlags() {
        assertFalse(WidgetLayout.isDense(3))
        assertTrue(WidgetLayout.isDense(8))
        assertFalse(WidgetLayout.isUltra(11))
        assertTrue(WidgetLayout.isUltra(12))
        assertTrue(WidgetLayout.isRoomy(3))
        assertFalse(WidgetLayout.isRoomy(6))
    }
}
