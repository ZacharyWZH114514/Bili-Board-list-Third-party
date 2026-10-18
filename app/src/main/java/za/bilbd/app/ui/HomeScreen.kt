package za.bilbd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.Board
import za.bilbd.app.data.BoardRepository
import za.bilbd.app.data.RepoState
import za.bilbd.app.data.SettingsStore

@Composable
fun HomeScreen(
    state: RepoState,
    dark: Boolean,
    window: WindowWidth,
    onOpenIssues: (Int) -> Unit,
    onOpenSong: (String) -> Unit,
    onUpdate: () -> Unit,
) {
    val snapshot = state.snapshot
    when {
        snapshot == null && state.loadError != null ->
            MessagePane("榜单数据载入失败", state.loadError)

        snapshot == null -> LoadingPane("正在载入榜单数据…")

        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { UpdateCard(state, dark, onUpdate, Modifier.cascade(0)) }

            if (window.isExpanded) {
                // 平板横屏：三个榜单卡片横排，一屏看全
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        snapshot.boards.forEachIndexed { index, board ->
                            Box(Modifier.weight(1f).cascade(index + 1)) {
                                BoardCard(
                                    board = board,
                                    dark = dark,
                                    onOpenIssues = onOpenIssues,
                                    onOpenSong = onOpenSong,
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(snapshot.boards, key = { _, b -> b.id }) { index, board ->
                    BoardCard(
                        board = board,
                        dark = dark,
                        onOpenIssues = onOpenIssues,
                        onOpenSong = onOpenSong,
                        modifier = Modifier.cascade(index + 1),
                    )
                }
            }

            item { Box(Modifier.cascade(4)) { SourceFooter() } }
        }
    }
}

/** 数据来源与更新状态卡片 */
@Composable
private fun UpdateCard(
    state: RepoState,
    dark: Boolean,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snap = state.snapshot ?: return
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Biliboard 术力口周榜",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "数据来源 biliboard.uk · " +
                        if (state.autoUpdateEnabled) {
                            "${SettingsStore.describe(state.updateIntervalDays)}自动更新一次"
                        } else {
                            "仅手动更新（设置里可改周期）"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.updating) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onUpdate) {
                    Icon(Icons.Filled.Refresh, contentDescription = "立即更新数据")
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        val catalogText = state.catalog?.let { "曲库 ${it.songs.size} 首" } ?: "曲库载入中…"
        Text(
            text = "${snap.boards.size} 个榜单 · ${snap.issueCount} 期 · ${snap.entryCount} 条排名 · " +
                "${snap.songList.size} 首上榜曲 · $catalogText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        if (state.updating) {
            Text(
                text = "正在从 biliboard.uk 更新…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "上次更新 ${BoardRepository.formatTime(state.lastUpdateAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    !state.autoUpdateEnabled -> "自动更新已关闭 · 可在设置里改"
                    state.isDue -> "已到更新周期 · 下次启动将自动更新"
                    else -> "下次自动更新 ${BoardRepository.formatTime(state.nextUpdateAt)}（" +
                        "${BoardRepository.formatRelative(state.nextUpdateAt)}）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.liveIssueCount > 0 || state.liveSongCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("已应用增量：")
                    if (state.liveIssueCount > 0) append("${state.liveIssueCount} 期榜单")
                    if (state.liveIssueCount > 0 && state.liveSongCount > 0) append(" · ")
                    if (state.liveSongCount > 0) append("${state.liveSongCount} 首曲目")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!state.lastError.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "上次更新失败：${state.lastError}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!state.lastMessage.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.lastMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "内置基线快照 ${state.bundledAt.let { BoardRepository.formatTime(it) }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoardCard(
    board: Board,
    dark: Boolean,
    onOpenIssues: (Int) -> Unit,
    onOpenSong: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = board.latest
    val champ = latest?.champion
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
        onClick = { onOpenIssues(board.id) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = board.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "共 ${board.issues.size} 期",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (latest == null) {
            Spacer(Modifier.height(8.dp))
            Text("暂无数据", style = MaterialTheme.typography.bodySmall)
            return@GlassSurface
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "最新 · ${latest.label} · ${latest.period}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = champ != null) {
                champ?.let { onOpenSong(it.bvid) }
            },
        ) {
            CoverImage(champ?.coverUrl, 56.dp, corner = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = champ?.title ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!champ?.titleCn.isNullOrBlank()) {
                    Text(
                        text = champ.titleCn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = buildString {
                        append(champ?.producers?.joinToString(" / ") { it.name } ?: "—")
                        champ?.let { append(" · 播放 ").append(formatCount(it.views)) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SourceFooter() {
    Text(
        text = "数据版权归 Biliboard 术力口周榜（biliboard.uk）所有，本应用仅作数据展示。\n" +
            "曲目封面通过 biliboard.uk 的图片接口加载。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 6.dp, end = 6.dp),
    )
}
