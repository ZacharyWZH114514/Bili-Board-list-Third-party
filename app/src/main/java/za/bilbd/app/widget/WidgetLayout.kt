package za.bilbd.app.widget

/**
 * 小组件「能放几条」的计算。
 *
 * 抽成纯函数是因为这块没法在桌面上反复试：拉一下格子、看一眼、再改数字，
 * 一轮就是几分钟，各家桌面报的尺寸还不一样。
 *
 * 核心思路：**只按一个行高算条数**，别做「小格子用紧凑行、大格子突然换宽松行」
 * 这种分段——分段处条数会反向减少，而且渲染用的行高和算条数用的行高一旦不一致，
 * 最后一行就被底边切掉半截（4×2 上第三条文字显示不全就是这么来的）。
 *
 * 所以行高只有两档，且是**单调**的：300dp 以上收紧到 30dp，以下 34dp。
 * 34 → 30 只会让条数变多，不会变少。
 */
object WidgetLayout {

    /** 上下内边距之和 */
    private const val VERTICAL_PADDING = 22f

    /** 头部（榜名 + 期号 + 日期）大致占的高度 */
    private const val HEADER = 50f

    /** 能显示的最大条数 */
    const val MAX_VISIBLE = 15

    /**
     * 单行高度（dp）。渲染时必须严格按这个值排版：
     *   34dp = 封面 30 + 上下 2×2
     *   30dp = 封面 22 + 上下 2×0…（配合 10/9sp 的两行文字）
     */
    fun rowHeight(heightDp: Float): Float = if (heightDp >= 300f) 30f else 34f

    /**
     * 这个高度放得下几条。
     *
     * 至少 1 条：格子再小也该画出第一条，而不是一片空白。
     */
    fun rowsFor(heightDp: Float): Int {
        if (heightDp <= 0f) return 1
        val usable = (heightDp - VERTICAL_PADDING - HEADER).coerceAtLeast(0f)
        return (usable / rowHeight(heightDp)).toInt().coerceIn(1, MAX_VISIBLE)
    }

    /** 条数少说明有富余，封面可以给大一点 */
    fun isRoomy(rows: Int): Boolean = rows <= 4

    /** 8 条以上收紧，12 条以上再瘦一圈 */
    fun isDense(rows: Int): Boolean = rows >= 8

    fun isUltra(rows: Int): Boolean = rows >= 12
}
