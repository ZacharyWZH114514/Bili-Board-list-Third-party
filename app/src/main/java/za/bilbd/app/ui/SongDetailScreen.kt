package za.bilbd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.BiliVideo
import za.bilbd.app.data.Song
import za.bilbd.app.data.SongHit

/**
 * 曲目详情。
 *
 * 整页是一个 LazyColumn，每块信息一个 item：封面 → 指标 → 操作 → 创作信息 →
 * 最近一次上榜数据 → 播放量走势 → 上榜历史 → 数据来源。
 * 手机与平板走同一套结构，只是内边距与最大宽度不同（宽度由 AppRoot 统一限制）。
 */
@Composable
fun SongDetailScreen(
    bvid: String,
    song: Song?,
    fallbackHit: SongHit?,
    isFavorite: Boolean,
    dark: Boolean,
    window: WindowWidth,
    onToggleFavorite: () -> Unit,
    /** B 站接口补到的信息；榜单之外的曲子靠它拿到封面与播放量 */
    bili: BiliVideo? = null,
    /** 「AI 解读这首歌」的状态 */
    insight: AiUiState = AiUiState.Idle,
    insightReady: Boolean = false,
    onExplain: () -> Unit = {},
    onClearInsight: () -> Unit = {},
    /** AI 追问状态（解读出结果后才有资料可依据） */
    chat: AiChatState = AiChatState(),
    onOpenAiSettings: () -> Unit = {},
    /** 点 P主 名字进入 P主页（可关注） */
    onOpenProducer: (String) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current

    // 封面优先用曲库/曲库命中的地址；biliboard 图床没有这张图时回退到 B 站
    val coverUrl = song?.coverUrl?.takeIf { it.isNotBlank() }
        ?: fallbackHit?.coverUrl?.takeIf { it.isNotBlank() }

    // 整页就是一个 LazyColumn，每块信息是一个 item。
    //
    // 之前是「外层 verticalScroll + 里面自己拼 Column/Box 包块」：块与块会不会重叠，
    // 完全取决于容器有没有包对（Box 会把子项叠在同一位置），出错后从代码上很难一眼看出来。
    // 交给 LazyColumn 之后，item 之间由列表保证不重叠，而且天然可滑动、还是懒加载。
    //
    // 这里刻意不再套 cascade 进场动画：这一页已经被反馈过排版异常，
    // 先让它只依赖列表这一种布局机制，把变量减到最少。
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = if (window.isWide) 16.dp else 14.dp,
            vertical = 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 平板（Expanded）走两栏：左边封面、右边指标与操作，省掉一路往下滚。
        // 只把**头部这几块**并排，其余仍然是同一条 LazyColumn ——
        // 这样「整页只有一种布局机制」这个约束还成立，不会退回以前那种叠层写法。
        if (window.isExpanded) {
            item(key = "hero") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.weight(1f)) {
                        CoverSection(
                            bvid = bvid,
                            song = song,
                            fallbackHit = fallbackHit,
                            dark = dark,
                            coverUrl = coverUrl,
                            biliCoverUrl = bili?.coverUrl,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        PillsSection(
                            song = song,
                            fallbackHit = fallbackHit,
                            dark = dark,
                            bili = bili,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionsSection(
                            dark = dark,
                            bvid = bvid,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            uriHandler = uriHandler,
                        )
                    }
                }
            }
        } else {
            item(key = "cover") {
                CoverSection(
                    bvid = bvid,
                    song = song,
                    fallbackHit = fallbackHit,
                    dark = dark,
                    coverUrl = coverUrl,
                    biliCoverUrl = bili?.coverUrl,
                )
            }

            item(key = "pills") {
                PillsSection(song = song, fallbackHit = fallbackHit, dark = dark, bili = bili)
            }

            item(key = "actions") {
                ActionsSection(
                    dark = dark,
                    bvid = bvid,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    uriHandler = uriHandler,
                )
            }
        }

        // 每首歌都能让 AI 解读一遍「这首歌在讲什么」
        item(key = "insight") {
            AiResultCard(
                title = "AI 解读这首歌",
                icon = Icons.Filled.Star,
                state = insight,
                ready = insightReady,
                idleHint = if (insightReady) {
                    "先用豆包搜索联网查这首歌的歌词与背景资料，再让模型写成一段中文解读。" +
                        "每次解读会消耗一次搜索 + 一次模型调用。"
                } else {
                    "需要你自己的 DeepSeek API Key（联网查资料还需要豆包搜索的 Key）。Key 只保存在本机。"
                },
                runLabel = "解读这首歌",
                loadingLabel = "正在联网查资料并写解读…",
                missingKeyLabel = "去设置 API Key",
                onRun = onExplain,
                onOpenSettings = onOpenAiSettings,
                onClear = onClearInsight,
                chat = chat,
            )
        }

        if (song != null) {
            item(key = "credits") { CreditCard(song, dark, onOpenProducer) }
            if (song.latest != null) item(key = "latest") { LatestStatsCard(song, dark) }
            // 排名走势才是榜单的核心：播放量只说明火不火，名次说明在榜上排到过哪
            if (song.history.size >= 2) item(key = "rankTrend") { RankTrendCard(song, dark) }
            if (song.history.size >= 2) item(key = "trend") { TrendCard(song, dark) }
            item(key = "history") { HistoryCard(song, dark) }
        } else {
            if (fallbackHit != null) {
                item(key = "fallback") { FallbackSection(fallbackHit, dark, bili) }
            }
            if (bili != null) {
                item(key = "bili") { BiliOnlySection(bili, dark) }
            }
        }

        item(key = "footer") {
            Text(
                text = "曲目信息与榜单数据来自 biliboard.uk",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 各区块
// ---------------------------------------------------------------------------

/** 封面 + 曲名 */
@Composable
private fun CoverSection(
    bvid: String,
    song: Song?,
    fallbackHit: SongHit?,
    dark: Boolean,
    coverUrl: String?,
    biliCoverUrl: String?,
) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(12.dp),
        // 这里曾经带 elevation = 8.dp：阴影画在半透明玻璃层后面会透出来把面板压脏，
        // 而且全应用只有这一处用阴影（其它地方在性能优化时都去掉了），删掉更一致。
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverImage(
            coverUrl,
            156.dp,
            corner = 18.dp,
            dark = dark,
            fallbackUrl = biliCoverUrl,
            // 详情页大图按封面真实比例显示：B 站来的封面多是 16:9，
            // 硬裁成正方形会把两侧切掉。列表缩略图不开，免得行高参差。
            adaptive = true,
        )
            Spacer(Modifier.height(12.dp))
            Text(
                text = song?.title ?: fallbackHit?.title ?: bvid,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val titleCn = song?.titleCn ?: fallbackHit?.titleCn
            if (!titleCn.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = titleCn,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = bvid,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 最高排名 / 上榜期数 / 得分；榜单之外的曲子改用 B 站播放量 */
@Composable
private fun PillsSection(song: Song?, fallbackHit: SongHit?, dark: Boolean, bili: BiliVideo?) {
    if (song != null) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValuePill("最高排名", "#${song.bestRank}", dark, Modifier.weight(1f))
            ValuePill("上榜期数", "${song.onBoardCount}", dark, Modifier.weight(1f))
            ValuePill("最新得分", formatScore(song.latest?.score ?: 0L), dark, Modifier.weight(1f))
        }
    } else if (fallbackHit != null) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValuePill("上榜期数", "${fallbackHit.chartCount}", dark, Modifier.weight(1f))
            ValuePill(
                "B站播放",
                if (bili != null && bili.play > 0) formatCount(bili.play) else "查询中…",
                dark,
                Modifier.weight(1f),
            )
            ValuePill("来源", "全站曲库", dark, Modifier.weight(1f))
        }
    }
}

/** B 站侧数据（播放/点赞/投币/收藏、UP主、投稿时间）。上榜曲与未上榜曲都显示 —— 这些数值曲库本身没有 */
@Composable
private fun BiliOnlySection(bili: BiliVideo, dark: Boolean) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("B站数据", Modifier.weight(1f))
            Text(
                text = "来自 B 站接口",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValuePill("播放", formatCount(bili.play), dark, Modifier.weight(1f))
            ValuePill("点赞", formatCount(bili.like), dark, Modifier.weight(1f))
            ValuePill("投币", formatCount(bili.coin), dark, Modifier.weight(1f))
            ValuePill("收藏", formatCount(bili.favorite), dark, Modifier.weight(1f))
        }
        if (bili.author.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "UP主：${bili.author}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bili.pubdateMs > 0) {
            Text(
                text = "投稿时间：${formatDate(bili.pubdateMs / 1000)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 收藏 / 打开 B 站 / 打开 biliboard */
@Composable
private fun ActionsSection(
    dark: Boolean,
    bvid: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    uriHandler: UriHandler,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassActionButton(
            dark = dark,
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = if (isFavorite) "已收藏" else "收藏",
            accent = isFavorite,
            onClick = onToggleFavorite,
            modifier = Modifier.weight(1f),
        )
        GlassActionButton(
            dark = dark,
            icon = Icons.Filled.PlayArrow,
            label = "B站观看",
            onClick = { runCatching { uriHandler.openUri("https://www.bilibili.com/video/$bvid") } },
            modifier = Modifier.weight(1f),
        )
        GlassActionButton(
            dark = dark,
            icon = Icons.Filled.Share,
            label = "biliboard",
            onClick = { runCatching { uriHandler.openUri("https://biliboard.uk/") } },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 未上榜曲目的兜底信息 */
@Composable
private fun FallbackSection(fallbackHit: SongHit, dark: Boolean, bili: BiliVideo?) {
    GlassInfoBlock(
        dark = dark,
        title = "曲目信息（来自全站曲库）",
        lines = listOfNotNull(
            "P主：${fallbackHit.producerText}",
            "歌手：${fallbackHit.vocalistText}",
            bili?.takeIf { it.author.isNotBlank() }?.let { "B站UP主：${it.author}" },
            bili?.takeIf { it.play > 0 }?.let { "B站播放：${formatCount(it.play)}" },
            "该曲目尚未进入周榜，因此没有排名与得分数据。",
        ),
    )
}

/** 创作信息 */
@Composable
private fun CreditCard(song: Song, dark: Boolean, onOpenProducer: (String) -> Unit) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionTitle("创作信息")
        Spacer(Modifier.height(10.dp))
        CreditLine("P主", song.producers, Modifier.fillMaxWidth(), onClickName = onOpenProducer)
        Spacer(Modifier.height(8.dp))
        CreditLine("歌手", song.vocalists, Modifier.fillMaxWidth())
        val infoLines = listOfNotNull(
            song.history.firstOrNull()?.let {
                "首次上榜：${it.year} 年第 ${it.week} 周（第 ${it.issueId} 期）"
            },
            song.pubtime.takeIf { it > 0 }?.let { "B站投稿时间：${formatDate(it)}" },
            song.firstRecordedAt?.takeIf { it > 0 && it != song.pubtime }
                ?.let { "首次被收录：${formatDate(it)}" },
        )
        if (infoLines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            infoLines.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 最近一次上榜数据 */
@Composable
private fun LatestStatsCard(song: Song, dark: Boolean) {
    val latest = song.latest ?: return
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionTitle("最近一次上榜数据")
        Spacer(Modifier.height(12.dp))
        StatsRow(
            views = latest.views,
            likes = latest.likes,
            coins = latest.coins,
            favorites = latest.favorites,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${latest.boardName} 第 ${latest.issueId} 期 · 第 ${latest.rank} 名 · 得分 ${formatScore(latest.score)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 播放量走势 */
@Composable
private fun TrendCard(song: Song, dark: Boolean) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionTitle("播放量走势（按上榜期数）")
        Spacer(Modifier.height(10.dp))
        BoxedBars(song.history.map { it.views.toFloat() }, dark)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "第 ${song.history.first().issueId} 期 → 第 ${song.history.last().issueId} 期",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 排名走势。
 *
 * 名次是**越小越好**，而柱状图是越高越好，所以这里把值取反（maxRank - rank + 1）：
 * 柱子越高代表名次越靠前。直接拿 rank 当柱高会画反。
 */
@Composable
private fun RankTrendCard(song: Song, dark: Boolean) {
    val points = song.history.sortedBy { it.issueId }
    val maxRank = points.maxOf { it.rank }.coerceAtLeast(1)
    val best = points.minOf { it.rank }
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionTitle("排名走势")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "柱子越高名次越好 · 最好成绩第 $best 名",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        BoxedBars(points.map { (maxRank - it.rank + 1).toFloat() }, dark)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "第 ${points.first().issueId} 期（第 ${points.first().rank} 名）" +
                " → 第 ${points.last().issueId} 期（第 ${points.last().rank} 名）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 上榜历史 */
@Composable
private fun HistoryCard(song: Song, dark: Boolean) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionTitle("上榜历史（${song.history.size} 期）")
        Spacer(Modifier.height(8.dp))
        song.history.sortedByDescending { it.issueId }.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = p.boardName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(74.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "第 ${p.issueId} 期",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(70.dp),
                )
                Text(
                    text = "${p.year}W${p.week}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(62.dp),
                )
                Text(
                    text = "#${p.rank}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (p.rank <= 3) Accent.gold else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp),
                )
                Text(
                    text = formatCount(p.views),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 玻璃质感的操作按钮 */
@Composable
private fun GlassActionButton(
    dark: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
