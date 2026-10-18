package za.bilbd.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 级联进场动画的回归测试。
 *
 * 背景：这里曾经把 `((progress * span) - index).coerceIn(0f, 1f)` 直接当 alpha 用，
 * span = 12 时 index ≥ 12 会算出负数并被 clamp 成 0，于是**第 12 项之后永远是透明的**。
 * 表现是「搜索命中 300 条，界面只显示 12 条」，而且不报错、不崩溃，极难从代码上看出来。
 *
 * 所以这里最重要的一条是 [fullProgress_everyIndexIsVisible]。
 */
class MotionTest {

    private val eps = 1e-5f

    @Test
    fun fullProgress_everyIndexIsVisible() {
        // 这是核心断言：动画跑完时，不管有多少项都必须完全不透明
        for (index in 0 until 80) {
            val v = introCascadeProgress(progress = 1f, index = index, span = 12)
            assertEquals("index=$index 在 progress=1 时应为 1", 1f, v, eps)
        }
    }

    @Test
    fun fullProgress_visibleWithSmallSpanToo() {
        for (span in listOf(1, 2, 5, 12, 20)) {
            for (index in 0 until 40) {
                assertEquals(
                    "span=$span index=$index",
                    1f,
                    introCascadeProgress(1f, index, span),
                    eps,
                )
            }
        }
    }

    @Test
    fun zeroProgress_nothingVisible() {
        // 动画刚开始时**每一项**都必须是透明的。
        // 曾经用 `if (index >= span) 1f` 修「永远不可见」，结果这些项第一帧就全亮了，
        // 比前面的项出现得还早——顺序反了。这条断言把那个错法钉死。
        for (index in 0 until 80) {
            assertEquals(
                "index=$index 在 progress=0 时应完全透明",
                0f,
                introCascadeProgress(0f, index, span = 12),
                eps,
            )
        }
    }

    @Test
    fun itemsBeyondSpan_appearWithLastWindowSlot() {
        // 超出窗口的项应与「窗口内最后一项」同步，不早也不晚
        val span = 12
        for (p in listOf(0.25f, 0.5f, 0.75f, 0.9166f, 1f)) {
            val last = introCascadeProgress(p, span - 1, span)
            for (index in span until span + 30) {
                assertEquals(
                    "progress=$p index=$index 应跟随第 ${span - 1} 项",
                    last,
                    introCascadeProgress(p, index, span),
                    eps,
                )
            }
        }
    }

    @Test
    fun midProgress_isStaggered() {
        // progress = 0.5、span = 12 → 已经轮到第 6 项，前几项就位、后面的还没出现
        assertEquals(1f, introCascadeProgress(0.5f, 0, 12), eps)
        assertEquals(0f, introCascadeProgress(0.5f, 10, 12), eps)
        // 边界：正好轮到的这一项从 0 开始
        assertEquals(0f, introCascadeProgress(0.5f, 6, 12), eps)
    }

    @Test
    fun monotonicInProgress() {
        // 同一个 index，progress 变大时透明度不能变小
        for (index in 0 until 16) {
            var previous = -1f
            var p = 0f
            while (p <= 1f) {
                val v = introCascadeProgress(p, index, span = 12)
                assertTrue(
                    "index=$index progress=$p 出现回退：$previous -> $v",
                    v >= previous - eps,
                )
                previous = v
                p += 0.02f
            }
        }
    }

    @Test
    fun outputAlwaysInUnitRange() {
        for (index in 0 until 30) {
            var p = -1f
            while (p <= 2f) {
                val v = introCascadeProgress(p, index, span = 12)
                assertTrue("progress=$p index=$index 越界：$v", v in 0f..1f)
                p += 0.05f
            }
        }
    }

    @Test
    fun nonPositiveSpan_meansNoAnimation() {
        // span <= 0 视为「不做错峰」，直接全部就位
        assertEquals(1f, introCascadeProgress(0f, 0, span = 0), eps)
        assertEquals(1f, introCascadeProgress(0f, 5, span = -3), eps)
    }
}
