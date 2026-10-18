package za.bilbd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import za.bilbd.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.bilbd.app.data.RankEntry
import za.bilbd.app.data.SongHit

@Composable
fun LoadingPane(text: String = stringResource(R.string.widget_loading), modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MessagePane(
    title: String,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassSurface(
            dark = isDarkGlass(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** 榜单中的一行：名次 + 封面 + 曲名 + P主 + 播放 + 名次变化（玻璃卡片） */
@Composable
fun RankEntryRow(
    entry: RankEntry,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(entry.rank, 30.dp)
            Spacer(Modifier.width(8.dp))
            CoverImage(entry.coverUrl, 52.dp, corner = 12.dp, dark = dark)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!entry.titleCn.isNullOrBlank()) {
                    Text(
                        text = entry.titleCn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = entry.producers.joinToString(" / ") { it.name }.ifEmpty { stringResource(R.string.unknown_producer) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                MovementBadge(entry.movement)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatCount(entry.views),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 搜索结果 / 收藏 中的一行（玻璃卡片） */
@Composable
fun HitRow(
    hit: SongHit,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** B 站补到的封面；biliboard 图床没有这张图时回退使用 */
    coverUrl: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                hit.coverUrl.takeIf { it.isNotBlank() },
                50.dp,
                corner = 12.dp,
                dark = dark,
                fallbackUrl = coverUrl?.takeIf { it.isNotBlank() },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!hit.titleCn.isNullOrBlank()) {
                    Text(
                        text = hit.titleCn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${hit.producerText} · ${hit.vocalistText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (trailing != null) trailing() else HitBadge(hit)
        }
    }
}

@Composable
private fun HitBadge(hit: SongHit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
        when {
            hit.bestRank > 0 -> {
                Text(
                    text = stringResource(R.string.hit_best_rank, hit.bestRank),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hit.bestRank <= 3) Accent.gold else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.hit_chart_weeks, hit.chartCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            hit.chartCount > 0 -> Text(
                text = stringResource(R.string.hit_chart_weeks, hit.chartCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Text(
                text = stringResource(R.string.hit_not_charted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 数值强调块（玻璃小卡片） */
@Composable
fun ValuePill(label: String, value: String, dark: Boolean, modifier: Modifier = Modifier) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BoxedBars(values: List<Float>, dark: Boolean, modifier: Modifier = Modifier) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        MiniBars(values, Modifier.fillMaxWidth(), barHeight = 40.dp)
    }
}

/** 通用的玻璃信息块 */
@Composable
fun GlassInfoBlock(
    dark: Boolean,
    title: String? = null,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty() && title == null) return
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
        }
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** 分割线（在玻璃卡片之间用留白替代原来的细线） */
@Composable
fun CardSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(0.dp))
}
