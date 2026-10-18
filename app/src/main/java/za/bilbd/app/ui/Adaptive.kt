package za.bilbd.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口宽度分级（对齐 Material 3 的 window size class 断点）：
 *   Compact  < 600dp   手机
 *   Medium   600–839dp 小尺寸平板 / 折叠屏展开态 / 手机横屏
 *   Expanded ≥ 840dp   平板横屏、桌面模式
 */
enum class WindowWidth { Compact, Medium, Expanded }

/** 是否是宽屏（平板级别），宽屏改用侧边导航栏 */
val WindowWidth.isWide: Boolean get() = this != WindowWidth.Compact

/** 是否是大屏（平板横屏），可以放多栏 */
val WindowWidth.isExpanded: Boolean get() = this == WindowWidth.Expanded

/**
 * 内容最大宽度。宽屏下把内容居中并限制在可读宽度内，
 * 避免一行从屏幕这头拉到那头。null 表示不限制（手机）。
 */

/** 列表类页面（期数 / 榜单 / 搜索 / 收藏）：保持单栏阅读宽度 */
val WindowWidth.listMaxWidth: Dp?
    get() = when (this) {
        WindowWidth.Compact -> null
        WindowWidth.Medium -> 700.dp
        WindowWidth.Expanded -> 780.dp
    }

/** 首页：宽屏下榜单卡片要横排，所以给得更宽 */
val WindowWidth.homeMaxWidth: Dp?
    get() = when (this) {
        WindowWidth.Compact -> null
        WindowWidth.Medium -> 760.dp
        WindowWidth.Expanded -> 1240.dp
    }

/** 曲目详情：宽屏下分两栏 */
val WindowWidth.detailMaxWidth: Dp?
    get() = when (this) {
        WindowWidth.Compact -> null
        WindowWidth.Medium -> 820.dp
        WindowWidth.Expanded -> 1080.dp
    }

/** 关于页：宽屏下左右两栏 */
val WindowWidth.aboutMaxWidth: Dp?
    get() = when (this) {
        WindowWidth.Compact -> null
        WindowWidth.Medium -> 780.dp
        WindowWidth.Expanded -> 1180.dp
    }

fun windowWidthOf(maxWidth: Dp): WindowWidth = when {
    maxWidth >= 840.dp -> WindowWidth.Expanded
    maxWidth >= 600.dp -> WindowWidth.Medium
    else -> WindowWidth.Compact
}
