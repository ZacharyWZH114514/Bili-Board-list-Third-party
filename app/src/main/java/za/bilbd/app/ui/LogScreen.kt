package za.bilbd.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.AppLog

/**
 * 日志页。
 *
 * 只做两件事：看最近发生了什么、把它导成 txt。
 * 导出走系统的「新建文档」选择器（SAF），所以不需要任何存储权限，
 * 用户自己决定存哪儿。
 */
@Composable
fun LogScreen(
    dark: Boolean,
    lines: List<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 导出内容在点按钮那一刻定下来，避免回调里再读一次状态
    val pending = remember { mutableStateOf("") }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pending.value.toByteArray(Charsets.UTF_8))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionTitle("导出")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "把当前这段记录存成 txt。会带上上次运行的日志，" +
                    "所以闪退之后也能导出。不含任何 API Key。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // 刻意不用 Row + weight：weight 传进自定义组件后会被按错误的轴向解释，
            // 结果按钮被撑成一大块空白（实机踩到的），竖排全宽最稳
            LogButton(
                label = "导出为 txt（${lines.size} 行）",
                primary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    pending.value = if (lines.isEmpty()) "（没有日志）\n" else AppLog.exportText()
                    runCatching { createDoc.launch(AppLog.suggestedFileName()) }
                },
            )
            Spacer(Modifier.height(8.dp))
            LogButton(
                label = "清空",
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClear,
            )
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = "还没有日志。用一会儿再回来看看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 日志行不放进 LazyColumn：外层已经是可滚动 Column，
                // 嵌一个会滚动的列表会抛无限高约束异常（这个坑踩过）
                Column {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun LogButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (primary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}