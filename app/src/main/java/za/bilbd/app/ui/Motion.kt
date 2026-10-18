package za.bilbd.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 动效。
 *
 * 曲线用的是 Material 3 的 emphasized / standard 缓动（三次贝塞尔），
 * 都是「慢起快中慢收」的非线性曲线：进场用 decelerate（起步快、收尾缓），
 * 出场用 accelerate（起步缓、离场快），这样位移看起来才有惯性。
 */
object Motion {

    // ---------------- 缓动曲线 ----------------

    /** 标准：一般属性变化 */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 标准减速：进场 */
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    /** 标准加速：出场 */
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    /** 强调减速：大块内容进场（最有「重量感」的一条） */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 强调加速：大块内容出场 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** 强调标准：颜色、透明度这类不适合回弹的属性 */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 有回弹的按压反馈（欠阻尼弹簧） */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = 0.42f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 内容入场用的柔和弹簧 */
    fun <T> enterSpring() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ---------------- 时长 ----------------

    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 350
    const val DURATION_LONG = 500

    /** 页面切换 */
    const val SCREEN_ENTER = 460
    const val SCREEN_EXIT = 340
}

/**
 * 列表「级联进场」的驱动值：0 → 1 走一条强调减速曲线，整屏只跑一次。
 *
 * 之所以用一个统一驱动而不是每个 item 各自动画：
 *  - 只跑一次，滚动回来不会重复播放
 *  - 每个 item 由同一个进度推导自己的局部进度，天然错峰
 */
@Composable
fun rememberIntroProgress(durationMillis: Int = 720): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis, easing = Motion.EmphasizedDecelerate))
    }
    return anim.asState()
}

/**
 * 级联进场的**纯数学部分**，抽出来是为了能单测。
 *
 * 这里踩过两次坑，两种错法都记在下面：
 *  1. 最初写成 `((progress * span) - index).coerceIn(0f, 1f)` 当 alpha。
 *     span = 12 时 index ≥ 12 算出负数、被 clamp 成 0，于是那些项**永远不可见** ——
 *     表现是「搜索命中 300 条，界面只显示 12 条」，不报错、不崩溃，极难看出来。
 *  2. 改成 `if (index >= span) 1f` 修好之后，那些项又变成**第一帧就全亮**，
 *     比前面的项出现得还早，顺序反了。
 *
 * 现在的做法：超出窗口的项**跟着窗口内最后一项一起出现**（把 index 夹到 span-1）。
 * 这样 progress = 0 时全部为 0、progress = 1 时全部为 1，中间既不错序也不缺失。
 *
 * @return 该项的局部进度，0 = 还没出现，1 = 完全就位
 */
fun introCascadeProgress(progress: Float, index: Int, span: Int = 12): Float {
    if (span <= 0) return 1f
    val slot = index.coerceIn(0, span - 1)
    val local = ((progress * span) - slot).coerceIn(0f, 1f)
    // smoothstep：去掉首尾的生硬感
    return local * local * (3f - 2f * local)
}

/**
 * 把一个统一的 [progress] 摊成逐项错峰的出现效果。
 *
 * 注意：状态只在 graphicsLayer 里读取，所以每帧只更新图层属性，不触发重组。
 */
fun Modifier.introCascade(
    progress: State<Float>,
    index: Int,
    span: Int = 12,
    travelPx: Float = 42f,
): Modifier = graphicsLayer {
    val eased = introCascadeProgress(progress.value, index, span)
    alpha = eased
    translationY = (1f - eased) * travelPx
}

/**
 * 单个元素的进场：淡入 + 轻微上移 + 从略小放大到 1，用欠阻尼弹簧收尾。
 * 与 [introCascade] 的区别是它自带独立动画，适合整块内容（例如详情页封面）。
 */
fun Modifier.enterWithSpring(
    progress: State<Float>,
    travelPx: Float = 28f,
    fromScale: Float = 0.94f,
): Modifier = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    translationY = (1f - p) * travelPx
    val s = fromScale + (1f - fromScale) * p
    scaleX = s
    scaleY = s
}

/** 详情页整块内容入场所用的驱动值（弹簧，带一点点过冲） */
@Composable
fun rememberEnterProgress(): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.snapTo(0f)
        anim.animateTo(1f, Motion.enterSpring())
    }
    return anim.asState()
}

/**
 * 当前页面的级联驱动值。由 AppRoot 在每次页面进场时提供一份，
 * 列表只要写 `Modifier.cascade(index)` 就能拿到错峰进场，不用各自建动画。
 */
val LocalIntroProgress = staticCompositionLocalOf<State<Float>?> { null }

/** 列表项的错峰进场；没有提供驱动值时（例如非页面场景）原样返回，不做动画。 */
@Composable
fun Modifier.cascade(index: Int, span: Int = 12, travelPx: Float = 42f): Modifier {
    val progress = LocalIntroProgress.current ?: return this
    return this.introCascade(progress, index, span, travelPx)
}
