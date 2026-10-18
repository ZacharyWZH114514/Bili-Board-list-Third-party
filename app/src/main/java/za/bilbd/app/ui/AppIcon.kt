package za.bilbd.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.bilbd.app.R

/**
 * 一套图标配色。`alias` 是清单里对应的 activity-alias 简化名，
 * 切换真实启动图标时用它定位组件。
 */
data class IconPalette(
    val id: String,
    val label: String,
    val startColorRes: Int,
    val endColorRes: Int,
    val alias: String,
)

object IconPalettes {
    val all = listOf(
        IconPalette("default", "紫粉", R.color.ic_launcher_background_start, R.color.ic_launcher_background_end, "IconDefault"),
        IconPalette("blue", "海蓝", R.color.ic_launcher_blue_start, R.color.ic_launcher_blue_end, "IconBlue"),
        IconPalette("teal", "青绿", R.color.ic_launcher_teal_start, R.color.ic_launcher_teal_end, "IconTeal"),
        IconPalette("amber", "琥珀", R.color.ic_launcher_amber_start, R.color.ic_launcher_amber_end, "IconAmber"),
        IconPalette("crimson", "绯红", R.color.ic_launcher_crimson_start, R.color.ic_launcher_crimson_end, "IconCrimson"),
        IconPalette("mono", "墨灰", R.color.ic_launcher_mono_start, R.color.ic_launcher_mono_end, "IconMono"),
    )

    fun byId(id: String?): IconPalette = all.firstOrNull { it.id == id } ?: all.first()
}

/**
 * 应用图标：与启动图标使用同一套矢量资源（渐变底 + 均衡器柱状条），
 * 因此在任意尺寸下都清晰，且不会与应用列表里的图标不一致。
 */
@Composable
fun AppIconTile(
    palette: IconPalette,
    size: Dp,
    corner: Dp = size * 0.24f,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(
                        colorResource(palette.startColorRes),
                        colorResource(palette.endColorRes),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(1.15f),
        )
    }
}

/** 默认配色的图标（侧边导航栏等处使用） */
@Composable
fun AppIconTile(
    size: Dp,
    corner: Dp = size * 0.24f,
    modifier: Modifier = Modifier,
) = AppIconTile(IconPalettes.all.first(), size, corner, modifier)
