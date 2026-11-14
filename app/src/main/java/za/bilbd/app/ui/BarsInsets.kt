package za.bilbd.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶栏与底栏实际占用的高度。
 *
 * 栏现在是**覆盖层**（内容从玻璃下面滑过去），所以页面不能再靠 Scaffold 的
 * padding 让位，必须自己按这个值加 contentPadding / 内边距，
 * 否则静止时首尾内容会被玻璃盖住。
 *
 * 用 staticCompositionLocalOf：这个值只在栏尺寸变化时变（极少），
 * 不值得为它做细粒度追踪。
 */
data class BarsInsets(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

val LocalBarsInsets = staticCompositionLocalOf { BarsInsets() }

/** 在页面原有的 contentPadding 上叠加栏高（LazyColumn 用） */
@Composable
fun barsPadded(base: PaddingValues = PaddingValues(0.dp)): PaddingValues {
    val insets = LocalBarsInsets.current
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(dir),
        top = base.calculateTopPadding() + insets.top,
        end = base.calculateEndPadding(dir),
        bottom = base.calculateBottomPadding() + insets.bottom,
    )
}

/**
 * 给可滚动内容加栏高内边距（verticalScroll 的 Column 用）。
 *
 * 必须写在 `verticalScroll` **之后**：这样内边距属于滚动内容的一部分，
 * 静止时内容在栏下方，滚动时能滑到栏底下——写在前面就变成缩小视口了。
 */
@Composable
fun Modifier.barsInset(): Modifier {
    val insets = LocalBarsInsets.current
    return this.padding(top = insets.top, bottom = insets.bottom)
}
