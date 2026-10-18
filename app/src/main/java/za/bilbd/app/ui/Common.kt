package za.bilbd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import za.bilbd.app.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import za.bilbd.app.data.Credit
import za.bilbd.app.data.Movement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------------------
// 格式化
// ---------------------------------------------------------------------------

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

fun formatDate(epochSeconds: Long): String =
    if (epochSeconds <= 0) "—" else dateFmt.format(Date(epochSeconds * 1000))

/**
 * 大数字的紧凑写法。
 *
 * 这不是「翻译」问题而是**记数习惯**问题：中文按 万 / 亿 进位，英文按 K / M / B。
 * 所以按当前语言走两套进位，而不是把「万」翻成 "ten thousand"。
 * 用 Locale.getDefault() 是因为这个函数不在 Composable 作用域里 ——
 * 语言切换会重建 Activity，重建后 Locale 已更新。
 */
fun formatCount(n: Long): String {
    if (n <= 0) return "0"
    val chinese = java.util.Locale.getDefault().language == "zh"
    return if (chinese) {
        when {
            n >= 100_000_000 -> trimZero(n / 100_000_000.0) + "亿"
            n >= 10_000 -> trimZero(n / 10_000.0) + "万"
            else -> n.toString()
        }
    } else {
        when {
            n >= 1_000_000_000 -> trimZero(n / 1_000_000_000.0) + "B"
            n >= 1_000_000 -> trimZero(n / 1_000_000.0) + "M"
            n >= 1_000 -> trimZero(n / 1_000.0) + "K"
            else -> n.toString()
        }
    }
}

private fun trimZero(v: Double): String {
    val s = String.format(Locale.CHINA, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

fun formatScore(n: Long): String = if (n <= 0) "—" else String.format(Locale.CHINA, "%,d", n)

// ---------------------------------------------------------------------------
// 通用组件
// ---------------------------------------------------------------------------

/** 自适应封面：宽度 = size × 该系数 */
private const val ADAPTIVE_WIDTH_FACTOR = 1.85f

/** 自适应时允许的最瘦 / 最扁比例，超出就夹住，免得版面被极端图撑坏 */
private const val MIN_ADAPTIVE_ASPECT = 0.8f
private const val MAX_ADAPTIVE_ASPECT = 1.9f

@Composable
fun CoverImage(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    contentDescription: String? = null,
    dark: Boolean = isDarkGlass(),
    /** 主地址加载失败时的备用地址（用于 biliboard 图床没有该曲目、回退到 B 站封面） */
    fallbackUrl: String? = null,
    /**
     * 按封面**真实宽高比**自适应，而不是一律裁成正方形。
     *
     * 大图（曲目详情的封面）建议开：B 站来的封面多半是 16:9，
     * 硬裁成正方形会切掉两侧；按原比例显示才完整。
     * 列表缩略图**不建议开** —— 每行高度不一致会让列表显得参差。
     *
     * [adaptive] 为真时，宽度取 [size] × [ADAPTIVE_WIDTH_FACTOR]，高度由图片比例决定。
     */
    adaptive: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    // 占位色刻意不用 colorScheme.surfaceVariant：那是一块不透明的灰紫，
    // 压在彩色玻璃背景上会显得「颜色不对」。改成跟着玻璃走的低透明度填充。
    val placeholder = if (dark) Color(0x33FFFFFF) else Color(0x1A000000)

    var useFallback by remember(url, fallbackUrl) { mutableStateOf(false) }
    val effective = if (useFallback) fallbackUrl else url

    // 图片真实宽高比。加载成功前先按 1:1，避免布局从别的比例「跳」一下。
    var aspect by remember(effective) { mutableStateOf<Float?>(null) }
    // 夹在合理区间：太扁的（全景图）或太瘦的（长条图）都会把版面撑坏
    val ratio = (aspect ?: 1f).coerceIn(MIN_ADAPTIVE_ASPECT, MAX_ADAPTIVE_ASPECT)

    val boxModifier = if (adaptive) {
        modifier.width(size * ADAPTIVE_WIDTH_FACTOR).aspectRatio(ratio)
    } else {
        modifier.size(size)
    }

    Box(
        modifier = boxModifier
            .clip(shape)
            .background(placeholder),
        contentAlignment = Alignment.Center,
    ) {
        if (effective.isNullOrBlank()) {
            Text("♪", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (size.value / 2.6f).sp)
        } else {
            AsyncImage(
                model = effective,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    // 读出图片真实尺寸，盒子才按它的比例定形
                    val img = state.result.image
                    if (img.height > 0) aspect = img.width.toFloat() / img.height
                },
                onError = {
                    // 主地址挂了就换备用地址再试一次；两个都挂才显示占位
                    if (!useFallback && !fallbackUrl.isNullOrBlank()) useFallback = true
                },
                modifier = if (adaptive) Modifier.matchParentSize() else Modifier.size(size),
            )
        }
    }
}

/** 名次徽标：前三名用金银铜 */
@Composable
fun RankBadge(rank: Int, size: Dp = 34.dp) {
    val color = when (rank) {
        1 -> Accent.gold
        2 -> Accent.silver
        3 -> Accent.bronze
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.4f).sp,
        )
    }
}

@Composable
fun MovementBadge(movement: Movement, compact: Boolean = false) {
    val (text, color) = when (movement) {
        is Movement.New -> "NEW" to Accent.`new`
        is Movement.Same -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        is Movement.Up -> "▲${movement.delta}" to Accent.up
        is Movement.Down -> "▼${movement.delta}" to Accent.down
    }
    Text(
        text = text,
        color = color,
        fontSize = if (compact) 11.sp else 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 统计条：播放 / 点赞 / 投币 / 收藏 */
@Composable
fun StatsRow(
    views: Long,
    likes: Long,
    coins: Long,
    favorites: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatCell(stringResource(R.string.stat_views), formatCount(views))
        StatCell(stringResource(R.string.stat_likes), formatCount(likes))
        StatCell(stringResource(R.string.stat_coins), formatCount(coins))
        StatCell(stringResource(R.string.stat_favorites), formatCount(favorites))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** P主 / 歌手 一行。传 [onClickName] 后每个名字可点（例如进入 P主页） */
@Composable
fun CreditLine(
    label: String,
    credits: List<Credit>,
    modifier: Modifier = Modifier,
    onClickName: ((String) -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.padding(start = 8.dp)) {
            if (credits.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                credits.forEach { c ->
                    Text(
                        text = c.name,
                        style = MaterialTheme.typography.bodyMedium,
                        // 可点的名字用主色 + 下划线，让人知道这里能进去
                        color = if (onClickName != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (onClickName != null) {
                            androidx.compose.ui.text.style.TextDecoration.Underline
                        } else {
                            null
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onClickName != null) {
                            Modifier.clickable { onClickName.invoke(c.name) }
                        } else {
                            Modifier
                        },
                    )
                    if (c.extraAliases.isNotEmpty()) {
                        Text(
                            text = c.extraAliases.joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/** 迷你条形图：把一组数值画成柱状 */
@Composable
fun MiniBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 34.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.isEmpty()) return
    val max = values.maxOrNull()?.takeIf { abs(it) > 0.0001f } ?: 1f
    Row(
        modifier = modifier.height(barHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { v ->
            val frac = (v / max).coerceIn(0.06f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(barHeight * frac)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.75f))
            )
        }
    }
}
