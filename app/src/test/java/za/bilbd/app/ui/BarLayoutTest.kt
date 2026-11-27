package za.bilbd.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 走势图柱子间距。
 *
 * 回归的 bug：上榜 79 次的曲子，79 根柱子配 78 个 4dp 固定间距 = 312dp，
 * 超过卡片宽度，`weight(1f)` 分到负空间、每根柱子宽度为 0，
 * 结果「走势图里一根柱子都没有」。这里把「无论多少根都画得出来」钉死。
 */
class BarLayoutTest {

    @Test
    fun manyPointsShrinkGapsInsteadOfVanishing() {
        // 79 根柱子塞进 300dp 宽：间距必须被压缩到很小，但柱子宽度不能是 0
        val gap = BarLayout.gapFor(availableDp = 300f, count = 79)
        val barWidth = BarLayout.barWidthFor(availableDp = 300f, count = 79, gapDp = gap)
        assertTrue("间距应该被压到 4dp 以内，实际 $gap", gap <= 4f)
        assertTrue("柱子宽度不能为 0（实际 $barWidth）", barWidth >= 1.5f)
    }

    @Test
    fun fewPointsKeepTheNormalGap() {
        // 柱子很少时不该挤在一起：间距保持 4dp
        val gap = BarLayout.gapFor(availableDp = 300f, count = 5)
        assertEquals(4f, gap, 0.001f)
    }

    @Test
    fun gapNeverExceedsTheMax() {
        var count = 2
        while (count <= 200) {
            val gap = BarLayout.gapFor(availableDp = 320f, count = count)
            assertTrue("count=$count 时间距 $gap 超过上限", gap <= 4f)
            assertTrue("count=$count 时间距不该为负", gap >= 0f)
            count++
        }
    }

    @Test
    fun everyBarKeepsAPositiveWidth() {
        // 宽度从 120dp 到 800dp、柱子从 1 到 200 根，逐组合检查
        var available = 120f
        while (available <= 800f) {
            for (count in intArrayOf(1, 2, 5, 10, 19, 40, 79, 120, 200)) {
                val gap = BarLayout.gapFor(available, count)
                val w = BarLayout.barWidthFor(available, count, gap)
                assertTrue("available=$available count=$count 时柱子宽 $w", w > 0.5f)
            }
            available += 20f
        }
    }

    @Test
    fun longSeriesSwitchesToHorizontalScroll() {
        // 79 次上榜在手机卡片里放不下，必须走横向滚动（固定 8dp 柱宽）
        assertTrue(BarLayout.needsScroll(availableDp = 300f, count = 79))
        // 12 根柱子 12×11=132dp，300dp 放得下，不该滚动
        assertFalse(BarLayout.needsScroll(availableDp = 300f, count = 12))
        // 临界值附近：刚好放下/放不下的判断要稳定
        val fits = (300f / (BarLayout.COMFORT_BAR_DP + BarLayout.COMFORT_GAP_DP)).toInt()
        assertFalse(BarLayout.needsScroll(300f, fits))
        assertTrue(BarLayout.needsScroll(300f, fits + 1))
    }

    @Test
    fun singleBarHasNoGap() {
        assertEquals(0f, BarLayout.gapFor(availableDp = 200f, count = 1), 0.001f)
        assertEquals(0f, BarLayout.gapFor(availableDp = 200f, count = 0), 0.001f)
    }
}
