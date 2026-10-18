package za.bilbd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.Issue

@Composable
fun RankingScreen(
    issue: Issue?,
    boardName: String,
    dark: Boolean,
    aiState: AiUiState = AiUiState.Idle,
    aiReady: Boolean = false,
    onOpenSong: (String) -> Unit,
    onAnalyze: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onClearAi: () -> Unit = {},
    /** AI 追问状态（分析出结果后才有资料可依据） */
    chat: AiChatState = AiChatState(),
) {
    if (issue == null) {
        MessagePane("找不到该期榜单")
        return
    }
    val entries = issue.entries.sortedBy { it.rank }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            GlassSurface(
                dark = dark,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.cascade(0).fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = issue.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = issue.period,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$boardName · 收录 ${entries.size} 首 · 统计截止 ${formatDate(issue.endDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    issue.videoBvid?.let { bvid ->
                        Text(
                            text = "视频 $bvid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // AI 分析本期榜单
        item {
            AiResultCard(
                title = "AI 分析本期",
                icon = Icons.Filled.Star,
                state = aiState,
                ready = aiReady,
                idleHint = if (aiReady) {
                    "让模型读一遍这一期的名次、变化与数据，输出一段趋势解读。"
                } else {
                    "需要你自己的 DeepSeek API Key。Key 只保存在本机。"
                },
                runLabel = "分析本期榜单",
                loadingLabel = "正在请求模型分析…",
                missingKeyLabel = "去设置 API Key",
                onRun = onAnalyze,
                onOpenSettings = onOpenAiSettings,
                onClear = onClearAi,
                chat = chat,
                modifier = Modifier.cascade(1),
            )
        }

        itemsIndexed(entries, key = { _, e -> e.bvid }) { index, entry ->
            RankEntryRow(
                entry = entry,
                dark = dark,
                onClick = { onOpenSong(entry.bvid) },
                modifier = Modifier.cascade(index + 2),
            )
        }
    }
}
