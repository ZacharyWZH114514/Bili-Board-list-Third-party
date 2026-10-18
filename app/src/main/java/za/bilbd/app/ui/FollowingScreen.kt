package za.bilbd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.CatalogSong

/**
 * 「关注」页。
 *
 * 两部分：上面是关注的 P主 列表（可取消关注），下面是他们最新投稿的合并流。
 * 全部来自本地曲库，**不联网**。
 *
 * 一首歌可能有多位 P主，而用户可能都关注了 —— 合并流按 bvid 去重，不会重复出现。
 */
@Composable
fun FollowingScreen(
    names: Set<String>,
    feed: List<CatalogSong>,
    songCountOf: (String) -> Int,
    recommendations: List<Pair<String, Int>>,
    catalogLoading: Boolean,
    dark: Boolean,
    onOpenProducer: (String) -> Unit,
    onOpenSong: (String) -> Unit,
    onUnfollow: (String) -> Unit,
) {
    if (names.isEmpty()) {
        FollowEmptyState(
            recommendations = recommendations,
            catalogLoading = catalogLoading,
            dark = dark,
            onOpenProducer = onOpenProducer,
        )
        return
    }

    val sortedNames = names.sorted()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "producers") {
            GlassSurface(
                dark = dark,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        text = "关注的 P主（${names.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    sortedNames.forEach { name ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onOpenProducer(name) }
                                    .padding(vertical = 9.dp),
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "曲库收录 ${songCountOf(name)} 首",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onUnfollow(name) }) { Text("取消关注") }
                        }
                    }
                }
            }
        }

        item(key = "feedHeader") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                Text(
                    text = "最新投稿",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (feed.isEmpty()) {
                        if (catalogLoading) "正在读取本地曲库…" else "这些 P主 在曲库里还没有别的曲目"
                    } else {
                        "共 ${feed.size} 首，按投稿时间从新到旧"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(feed, key = { _, s -> s.bvid }) { index, song ->
            ProducerSongRow(
                song = song,
                dark = dark,
                onClick = { onOpenSong(song.bvid) },
                modifier = if (feed.size <= 20) Modifier.cascade(index + 1) else Modifier,
                showProducer = true,
            )
        }
    }
}

/** 还没有关注任何人时：说明怎么关注 + 给几个投稿多的 P主 作为起点 */
@Composable
private fun FollowEmptyState(
    recommendations: List<Pair<String, Int>>,
    catalogLoading: Boolean,
    dark: Boolean,
    onOpenProducer: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "hint") {
            GlassSurface(
                dark = dark,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        text = "还没有关注任何 P主",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "打开任意一首曲子的详情页，点「创作信息」里的 P主 名字进入 P主页，" +
                            "在那里点「关注」。关注后这里会汇总他们的全部曲目，按投稿时间排好。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            item(key = "recHeader") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    Text(
                        text = "从这些人开始",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "本地曲库里投稿最多的 P主（点进去可关注）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(recommendations, key = { _, r -> r.first }) { index, (name, count) ->
                GlassSurface(
                    dark = dark,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(14.dp),
                    onClick = { onOpenProducer(name) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .cascade(index + 1),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$count 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (catalogLoading) {
            item(key = "loading") {
                MessagePane(title = "正在读取本地曲库…", detail = "首次打开需要解析一次，之后就有缓存了。")
            }
        }
    }
}
