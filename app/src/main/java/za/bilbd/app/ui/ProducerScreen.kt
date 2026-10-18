package za.bilbd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.CatalogSong

/**
 * P主 主页。
 *
 * 全部数据来自本地曲库（14000+ 首里带 P主 与投稿时间），**不需要联网**，
 * 所以点进来立刻就出内容。
 */
@Composable
fun ProducerScreen(
    name: String,
    songs: List<CatalogSong>,
    isFollowing: Boolean,
    catalogLoading: Boolean,
    dark: Boolean,
    onToggleFollow: () -> Unit,
    onOpenSong: (String) -> Unit,
    /** P主 头像；null 表示没拿到（搬运号、接口失败等），显示占位而不是猜一个 */
    avatarUrl: String? = null,
    onLoadAvatar: () -> Unit = {},
) {
    val charted = songs.count { it.chartCount > 0 }

    // 进页面时拉一次头像；失败就一直是占位，不影响其它内容
    LaunchedEffect(name) { onLoadAvatar() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "header") {
            GlassSurface(
                dark = dark,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 头像复用 CoverImage：corner 取边长一半即正圆，
                    // 拿不到图时它会显示统一的占位符，不会露出破图
                    CoverImage(
                        url = avatarUrl,
                        size = 56.dp,
                        corner = 28.dp,
                        dark = dark,
                        contentDescription = name,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            songs.isNotEmpty() -> "曲库收录 ${songs.size} 首 · 其中 $charted 首上过榜"
                            catalogLoading -> "正在读取本地曲库…"
                            else -> "曲库里没有这位 P主 的其它曲目"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    }  // 内层 Column（名字与统计）
                }
                Spacer(Modifier.height(12.dp))
                FollowButton(
                    isFollowing = isFollowing,
                    onClick = onToggleFollow,
                )
            }
        }

        if (songs.isEmpty() && !catalogLoading) {
            item(key = "empty") {
                GlassSurface(
                    dark = dark,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = "本地曲库里没有这位 P主 的其它作品。曲库只包含在 biliboard 有记录的曲目，" +
                            "不代表这位 P主 只写过这一首。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(songs, key = { _, s -> s.bvid }) { index, song ->
            // 曲目多时不做级联动画，否则要滑很久才全部出现
            ProducerSongRow(
                song = song,
                dark = dark,
                onClick = { onOpenSong(song.bvid) },
                modifier = if (songs.size <= 20) Modifier.cascade(index + 1) else Modifier,
            )
        }
    }
}

/** 关注 / 已关注 按钮 */
@Composable
private fun FollowButton(isFollowing: Boolean, onClick: () -> Unit) {
    GlassSurface(
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isFollowing) "已关注 · 点这里取消" else "关注这位 P主",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** P主 页 / 关注流里的一行曲目 */
@Composable
fun ProducerSongRow(
    song: CatalogSong,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 关注流里一首歌可能来自不同 P主，需要把 P主 显示出来 */
    showProducer: Boolean = false,
) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                song.titleCn?.takeIf { it.isNotBlank() && it != song.title }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        if (showProducer) {
                            append(song.producerText).append(" · ")
                        }
                        append(song.vocalistText)
                        if (song.pubtime > 0) append(" · ").append(formatDate(song.pubtime))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (song.chartCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "上榜 ${song.chartCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
