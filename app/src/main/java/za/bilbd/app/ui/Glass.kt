package za.bilbd.app.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// 液态玻璃（Liquid Glass）视觉层
//
// 背景是一层缓慢流动的多色柔光；每团光斑都是一个独立的绘制图层，
// 动画只改变图层的平移矩阵（在 graphicsLayer 里做延迟状态读取），
// 因此不会每帧重新光栅化渐变——这是为了在软件渲染的模拟器上也能跑顺。
//
// 所有面板用「半透明填充 + 高光描边 + 顶部镜面反光」叠在柔光之上，
// 透出背景颜色，形成通透的玻璃质感。
// ---------------------------------------------------------------------------

private const val TAU = (2 * PI).toFloat()

/**
 * 玻璃面板填充：把「半透明底色」和「顶部镜面反光」合成同一个渐变，
 * 每张卡片少画一层（全屏滚动时省下大量像素混合）。
 */
@Composable
fun glassFill(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.30f),
        0.16f to Color.White.copy(alpha = 0.13f),
        0.55f to Color.White.copy(alpha = 0.06f),
        1f to Color.White.copy(alpha = 0.11f),
    )
} else {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.92f),
        0.16f to Color.White.copy(alpha = 0.72f),
        0.55f to Color.White.copy(alpha = 0.50f),
        1f to Color.White.copy(alpha = 0.66f),
    )
}

/** 玻璃边缘：上缘亮、下缘淡，模拟折射高光 */
@Composable
fun glassBorderBrush(dark: Boolean): Brush {
    val hi = if (dark) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 1f)
    val mid = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.40f)
    val lo = if (dark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.80f)
    return Brush.linearGradient(colors = listOf(hi, mid, lo), start = Offset.Zero, end = Offset.Infinite)
}

/** 顶部镜面反光（已并入 glassFill，保留给需要单独叠一层的场景） */
@Composable
fun glassSheen(dark: Boolean): Brush = Brush.verticalGradient(
    colors = listOf(
        if (dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.65f),
        Color.Transparent,
    ),
    startY = 0f,
    endY = 460f,
)

/**
 * 玻璃面板：半透明填充 + 高光描边 + 顶部镜面反光，可选点击（带按压缩放）。
 * 刻意不对内容做模糊，否则文字也会糊掉；玻璃感来自背景柔光透出来。
 *
 * [dark] 默认就是 `isDarkGlass()`，**绝大多数调用点不需要传**。
 * 明暗必须由主题决定——这里被写死过三次（dark = false / dark = true），
 * 每次都表现为「颜色和背景不融合」。构建期有 guardGlassTheme 任务兜底，
 * 调用点写 `dark = true/false` 字面量会直接构建失败。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    dark: Boolean = isDarkGlass(),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 欠阻尼弹簧：按下压得快，松手会轻微回弹一下，玻璃才有「弹性」
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = Motion.pressSpring(),
        label = "glass-press",
    )

    var m = modifier
    if (onClick != null) m = m.scale(scale)
    if (elevation > 0.dp) m = m.shadow(elevation, shape, clip = false)
    m = m
        .clip(shape)
        .background(glassFill(dark), shape)
        .border(1.dp, glassBorderBrush(dark), shape)
    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }

    // 玻璃面板不是 Material 的 Surface，必须自己提供内容色，
    // 否则未显式指定颜色的 Text 会落到默认黑色，在深色玻璃上几乎看不清。
    CompositionLocalProvider(LocalContentColor provides glassContentColor(dark)) {
        Column(modifier = m.padding(contentPadding), content = content)
    }
}

/** 条栏（顶栏 / 底栏）用的玻璃底 */
@Composable
fun Modifier.glassBar(dark: Boolean, shape: Shape = RoundedCornerShape(0.dp)): Modifier =
    this
        .background(glassFill(dark), shape)
        .border(1.dp, glassBorderBrush(dark), shape)

/** 默认柔光配色（深色 / 浅色） */
private val DARK_BLOBS = listOf(Color(0xFFFF2D7F), Color(0xFF6E45FF), Color(0xFF00C2D6), Color(0xFFFF9E1B))
private val LIGHT_BLOBS = listOf(Color(0xFFFF7FC0), Color(0xFF9E86FF), Color(0xFF6FD8E8), Color(0xFFFFC978))

/** 曲目详情页的换色过渡时长 */
private const val ACCENT_BLEND_MS = 720

/** 由封面主题色派生出四团柔光的颜色 */
private fun accentBlobColors(accent: CoverAccent, dark: Boolean): List<Color> {
    val p = accent.primary
    val s = accent.secondary
    val colors = listOf(p, s, lerp(p, s, 0.55f), lerp(s, p, 0.35f))
    // 浅色主题下柔光要更淡，否则压得底色发闷
    return if (dark) colors else colors.map { lerp(it, Color.White, 0.22f) }
}

/**
 * 全屏流动柔光背景。
 * 四团彩色柔光缓慢漂移，叠加成流动的「液态」底色；
 * 上下暗角直接并入底色渐变，省掉一整层全屏混合。
 *
 * [accent] 非空时（曲目详情页），整块背景会平滑过渡到该封面抽出的主题色。
 */
@Composable
fun GlassBackdrop(
    dark: Boolean = isDarkGlass(),
    accent: CoverAccent? = null,
    modifier: Modifier = Modifier,
) {
    val phase = rememberSlowPhase()
    val base = if (dark) {
        Brush.verticalGradient(
            0f to Color(0xFF05040A),
            0.12f to Color(0xFF0A0812),
            0.45f to Color(0xFF150F26),
            0.88f to Color(0xFF0A0812),
            1f to Color(0xFF04030A),
        )
    } else {
        Brush.verticalGradient(
            0f to Color(0xFFFDFAFF),
            0.12f to Color(0xFFF6F1FB),
            0.45f to Color(0xFFFFF4FA),
            0.88f to Color(0xFFEFF4FC),
            1f to Color(0xFFF5F2FA),
        )
    }

    // 主题色接入：四团柔光各自过渡到封面主色 / 次色。
    // 颜色不适合用会过冲的弹簧（中途会出现脏色），所以走强调标准曲线。
    val fallback = if (dark) DARK_BLOBS else LIGHT_BLOBS
    val themed = accent?.let { accentBlobColors(it, dark) }
    val anim = tween<Color>(durationMillis = ACCENT_BLEND_MS, easing = Motion.Emphasized)
    val c0 by animateColorAsState(themed?.get(0) ?: fallback[0], anim, label = "blob0")
    val c1 by animateColorAsState(themed?.get(1) ?: fallback[1], anim, label = "blob1")
    val c2 by animateColorAsState(themed?.get(2) ?: fallback[2], anim, label = "blob2")
    val c3 by animateColorAsState(themed?.get(3) ?: fallback[3], anim, label = "blob3")
    // 再叠一层很淡的主色「水洗」，让整屏都染上这首歌的颜色
    val washColor by animateColorAsState(
        accent?.primary?.copy(alpha = if (dark) 0.24f else 0.28f) ?: Color.Transparent,
        tween(durationMillis = ACCENT_BLEND_MS, easing = Motion.EmphasizedDecelerate),
        label = "wash",
    )

    BoxWithConstraints(modifier.fillMaxSize().background(base)) {
        val w = maxWidth
        val h = maxHeight
        val unit = if (w < h) w else h

        // speed 各不相同 -> 四团光的相位互相错开，轨迹不会整齐地一起复位
        Blob(c0, unit * 1.46f, w * 0.18f, h * 0.13f, phase, 0f, w * 0.10f, h * 0.05f, if (dark) 0.88f else 0.62f, 1.00f)
        Blob(c1, unit * 1.62f, w * 0.90f, h * 0.30f, phase, 1.6f, w * 0.09f, h * 0.09f, if (dark) 0.84f else 0.58f, 0.83f)
        Blob(c2, unit * 1.26f, w * 0.26f, h * 0.76f, phase, 3.1f, w * 0.12f, h * 0.08f, if (dark) 0.66f else 0.48f, 1.19f)
        Blob(c3, unit * 1.10f, w * 0.92f, h * 0.88f, phase, 4.4f, w * 0.10f, h * 0.06f, if (dark) 0.58f else 0.45f, 0.91f)

        if (washColor.alpha > 0.001f) {
            Box(Modifier.fillMaxSize().background(washColor))
        }
    }
}

/**
 * 缓慢变化的相位，每 [stepMs] 更新一次而不是每帧。
 * 步长原来取 400ms，配合李萨如轨迹能看出明显的「一跳一跳」；
 * 现在缩到 120ms，位移连续得多，而静止时的重绘次数仍远低于逐帧。
 */
@Composable
private fun rememberSlowPhase(periodMs: Long = 44_000L, stepMs: Long = 120L): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs, stepMs) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            delay(stepMs)
            val elapsed = SystemClock.uptimeMillis() - start
            state.floatValue = (elapsed % periodMs) / periodMs.toFloat()
        }
    }
    return state
}

/**
 * 一团柔光。
 * 关键点：phase 以 State 形式传入，只在 graphicsLayer 的 lambda 内读取，
 * 这样动画只会触发图层矩阵更新，不会重组或重绘渐变。
 */
@Composable
private fun Blob(
    color: Color,
    diameter: Dp,
    left: Dp,
    top: Dp,
    phase: State<Float>,
    phaseOffset: Float,
    driftX: Dp,
    driftY: Dp,
    alpha: Float,
    speed: Float,
) {
    val brush = remember(color, alpha) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha),
                0.45f to color.copy(alpha = alpha * 0.55f),
                0.75f to color.copy(alpha = alpha * 0.18f),
                1f to Color.Transparent,
            )
        )
    }
    Box(
        Modifier
            .graphicsLayer {
                val a = phase.value * TAU * speed + phaseOffset
                // X / Y 用不同频率 -> 走李萨如曲线，轨迹不闭合、不会周期性「归位」
                translationX = cos(a) * driftX.toPx()
                translationY = sin(a * 0.73f) * driftY.toPx()
            }
            .offset(x = left - diameter / 2, y = top - diameter / 2)
            .size(diameter)
            .background(brush)
    )
}

/** 深浅色判断（跟随主题背景亮度） */
@Composable
fun isDarkGlass(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    return (0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue) < 0.5f
}

/** 玻璃面板上的主要文字色（深色玻璃用浅字，浅色玻璃用深字） */
@Composable
fun glassContentColor(dark: Boolean): Color =
    if (dark) Color(0xFFF4EFFA) else Color(0xFF191520)

/** 玻璃面板上的次要文字色 */
@Composable
fun glassMutedColor(dark: Boolean): Color =
    if (dark) Color(0xFFC8C0D8) else Color(0xFF5C5666)
