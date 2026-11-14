package za.bilbd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.ChatMessage
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip

/**
 * AI 结果卡片，榜单分析（按「期」）与单曲解读（按「曲」）共用。
 *
 * 两点是踩过坑之后定下来的：
 *  1. **不接收 dark 参数** —— 明暗一律由 GlassSurface 自己从主题推导，
 *     调用方没有机会写错。（历史上写死 dark 导致过三次配色错误。）
 *  2. **结果文本不加 verticalScroll** —— 这张卡是放在 LazyColumn 的 item 里的，
 *     而 LazyColumn 给 item 的主轴约束是无限高，嵌套可滚动组件会直接抛异常。
 *     结果本来就不长，让它自然撑开、由外层列表滚动即可。
 */
/** 引用最多列几条，免得把卡片撑太长 */
private const val MAX_CITATIONS = 5

/** AI 追问区的状态打包，避免每个页面传一堆参数 */
data class AiChatState(
    /** 分析出结果后才启用 */
    val enabled: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val asking: Boolean = false,
    val placeholder: String = "接着问点什么…",
    val onAsk: (String) -> Unit = {},
    val onClear: () -> Unit = {},
)
@Composable
fun AiResultCard(
    title: String,
    icon: ImageVector,
    state: AiUiState,
    /** 模型侧是否已配置（没配置时按钮变成「去设置」） */
    ready: Boolean,
    idleHint: String,
    runLabel: String,
    loadingLabel: String,
    missingKeyLabel: String,
    onRun: () -> Unit,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /** 追问状态。默认不启用 —— 没分析出结果就没有资料可依据 */
    chat: AiChatState = AiChatState(),
) {

    val context = LocalContext.current
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            SectionTitle(title, Modifier.weight(1f))
            if (state is AiUiState.Done) {
                // 生成了一段解读却拿不出去是没用的——给个复制入口
                TextButton(
                    onClick = { copyToClipboard(context, title, state.text) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text("复制", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "清除结果",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when (state) {
            is AiUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is AiUiState.Done -> {
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                // 展示的是**请求的模型**（设置里选的那个）。中转站/网关可能无视它、
                // 自己路由到别的模型，这时把服务端实际用的那个也标出来，
                // 免得出现「设置是 pro、这里却写 flash」这种对不上的情况。
                val served = state.servedModel
                Text(
                    text = buildString {
                        append("由 ${state.model} 生成")
                        if (served.isNotBlank() && served != state.model) {
                            append("（服务端实际返回：$served）")
                        }
                        append(" · 内容仅供参考")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 联网搜索引用：DeepSeek 自带搜索时由服务端给出，点一下可以打开原文
                if (state.citations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "参考来源 ${state.citations.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.citations.take(MAX_CITATIONS).forEach { c ->
                        val uri = LocalUriHandler.current
                        Text(
                            text = "· " + (c.title.ifBlank { c.url }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { runCatching { uri.openUri(c.url) } }
                                .padding(vertical = 3.dp),
                        )
                    }
                }
            }

            is AiUiState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                AiButton(
                    label = if (ready) "重试" else missingKeyLabel,
                    icon = Icons.Filled.Refresh,
                    onClick = if (ready) onRun else onOpenSettings,
                )
            }

            AiUiState.Idle -> {
                Text(
                    text = idleHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                AiButton(
                    label = if (ready) runLabel else missingKeyLabel,
                    icon = Icons.Filled.Star,
                    onClick = if (ready) onRun else onOpenSettings,
                )
            }
        }

        // 追问区：只有分析出结果之后才出现（那时才有资料可依据）
        if (chat.enabled) {
            Spacer(Modifier.height(14.dp))
            AiChatSection(
                messages = chat.messages,
                asking = chat.asking,
                dark = isDarkGlass(),
                placeholder = chat.placeholder,
                onAsk = chat.onAsk,
                onClear = chat.onClear,
            )
        }
    }
}

/** 把生成的内容复制到剪贴板 */
private fun copyToClipboard(context: android.content.Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }
    // Android 13 起系统自己会弹「已复制」提示，不用再叠一个 Toast
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast
            .makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}

@Composable
private fun AiButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
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
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
