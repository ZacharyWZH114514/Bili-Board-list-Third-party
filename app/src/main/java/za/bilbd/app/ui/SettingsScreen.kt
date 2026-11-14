package za.bilbd.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import za.bilbd.app.R
import za.bilbd.app.data.AiConfig
import za.bilbd.app.data.BoardRepository
import za.bilbd.app.data.RepoState
import za.bilbd.app.data.SettingsStore
import za.bilbd.app.data.ThemeMode
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.core.content.ContextCompat
import androidx.compose.material3.TextButton
import za.bilbd.app.data.KeepAliveService
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import za.bilbd.app.data.AppLog

/**
 * 设置。
 * 四项：关于 / 更改图标 / 自定义更新时间 / AI 分析。
 */
@Composable
fun SettingsScreen(
    dark: Boolean,
    state: RepoState,
    aiConfig: AiConfig,
    onOpenAbout: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onOpenUpdateInterval: () -> Unit,
    onOpenAi: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReplayOnboarding: () -> Unit,
    dateOffsetDays: Int = 0,
    onDateOffsetChange: (Int) -> Unit = {},
    newIssueAlert: Boolean = false,
    onNewIssueAlertChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconId by AppIconState.currentId.collectAsStateWithLifecycle()

    // 说明：这里刻意**不显示**「已开启/未开启」。
    // 判断无障碍服务是否启用这件事，在 Redmi / Android 14 上三条路都不可靠：
    //   Settings.Secure 读出来是 null、AccessibilityManager 返回空列表、
    //   自己进程里的连接标记又会被进程重启抹掉（实测连续踩了三版）。
    // 与其显示一个可能说谎的状态，不如给一个按钮，让用户去系统页面看（那里才是准的）。

    // Android 13+ 发通知要运行时权限。开关点开时顺手要一次，拒绝就不开开关
    val hasNotifyPermission = rememberNotificationPermission(context)
    val notifyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onNewIssueAlertChange(granted) }
    val needNotifyPermission = !hasNotifyPermission
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .barsInset()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.cascade(0).fillMaxWidth(),
        ) {
            SettingRow(
                icon = Icons.Filled.Info,
                title = "关于",
                subtitle = buildString {
                    append("Vocaloid List")
                    if (versionName.isNotEmpty()) append(" · 版本 $versionName")
                },
                onClick = onOpenAbout,
            )
            RowDivider(dark)
            SettingRow(
                icon = Icons.Filled.Create,
                title = "更改图标",
                subtitle = "当前：${IconPalettes.byId(iconId).label}",
                onClick = onOpenAppIcon,
            )
            RowDivider(dark)
            SettingRow(
                icon = Icons.Filled.DateRange,
                title = "自定义更新时间",
                subtitle = intervalSummary(state),
                onClick = onOpenUpdateInterval,
            )
            RowDivider(dark)
            SettingRow(
                icon = Icons.Filled.Star,
                title = "AI 分析",
                subtitle = if (aiConfig.ready) {
                    "已配置 · ${aiConfig.model}"
                } else {
                    "未配置 · 填自己的 DeepSeek API Key"
                },
                onClick = onOpenAi,
            )
            RowDivider(dark)
            SettingRow(
                icon = Icons.Filled.List,
                title = "日志",
                subtitle = "查看运行记录，可导出为 txt",
                onClick = onOpenLog,
            )
            RowDivider(dark)
            SettingRow(
                icon = Icons.Filled.Info,
                title = "重看新手引导",
                subtitle = "第一次打开时的那几页说明",
                onClick = onReplayOnboarding,
            )
        }

        // 外观：之前只能跟随系统，想晚上用深色就得去改系统设置
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(1).fillMaxWidth(),
        ) {
            SectionTitle("外观")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeChip(
                        label = mode.label,
                        selected = mode == themeMode,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeModeChange(mode) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "玻璃的明暗、文字与卡片配色都会跟着切换。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }


        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(2).fillMaxWidth(),
        ) {
            SectionTitle("周榜日期偏移")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "期列表里的日期取的是榜单视频的发布时间（和网站一致）。" +
                    "如果某几期看起来还是差几天，可以整体往前／往后挪（单位：天）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsStore.DATE_OFFSET_PRESETS.forEach { d ->
                    ThemeChip(
                        // 只放数字：7 个格子挤在手机上时「不偏移」会被压成两行
                        label = if (d > 0) "+$d" else "$d",
                        selected = d == dateOffsetDays,
                        modifier = Modifier.weight(1f),
                        onClick = { onDateOffsetChange(d) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (dateOffsetDays == 0) "当前：不偏移（与网站一致）"
                else "当前：全部日期 ${if (dateOffsetDays > 0) "+" else ""}$dateOffsetDays 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 新一期提醒：后台由 WorkManager 定时看一眼，有新一轮就发通知
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(3).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("新一期提醒")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "后台每 6 小时看一眼 biliboard，有新一期就发一条通知。" +
                            "用的是系统定时任务，不需要无障碍服务，也不会常驻后台。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (needNotifyPermission) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "还需要允许通知：点开开关时系统会问一次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = newIssueAlert,
                    onCheckedChange = { on ->
                        if (!on) {
                            onNewIssueAlertChange(false)
                        } else if (needNotifyPermission) {
                            notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onNewIssueAlertChange(true)
                        }
                    },
                )
            }
        }

        // 可选的保活：无障碍服务（默认关闭，需要用户自己去系统设置里开）
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(4).fillMaxWidth(),
        ) {
            SectionTitle("后台常驻（可选）")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "上一条的定时任务用的是系统调度，已经够用。如果你发现提醒经常迟到，" +
                    "可以再把「后台常驻」打开——它靠一个无障碍服务让系统少清掉本应用。\n" +
                    "这个服务不读屏幕、不操作别的应用，但需要你手动去系统「无障碍」里授权；" +
                    "系统仍然可能杀掉本应用，只是概率变小。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开系统「无障碍」设置") }
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(5).fillMaxWidth(),
        ) {
            Text(
                text = "数据以内置快照为基线，只在到周期时才联网做增量更新；" +
                    "设成「仅手动更新」后，本应用不会再自动联网，只在你点「立即更新」时才请求一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** 设置项副标题：把当前更新周期说清楚 */
private fun intervalSummary(state: RepoState): String = when {
    !state.autoUpdateEnabled -> "仅手动更新"
    state.lastUpdateAt <= 0 -> SettingsStore.describe(state.updateIntervalDays)
    else -> "${SettingsStore.describe(state.updateIntervalDays)} · 下次 " +
        BoardRepository.formatRelative(state.nextUpdateAt)
}

/**
 * 自定义更新时间：选周期，另外保留「立即更新」。
 */
@Composable
fun UpdateIntervalScreen(
    dark: Boolean,
    state: RepoState,
    onSelect: (Int) -> Unit,
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .barsInset()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.cascade(0).fillMaxWidth(),
        ) {
            SettingsStore.PRESETS.forEachIndexed { index, days ->
                if (index > 0) RowDivider(dark)
                IntervalOption(
                    label = intervalLabel(days),
                    hint = intervalHint(days),
                    selected = state.updateIntervalDays == days,
                    onClick = { onSelect(days) },
                )
            }
        }

        // 当前状态 + 手动更新
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(1).fillMaxWidth(),
        ) {
            SectionTitle("当前状态")
            Spacer(Modifier.height(6.dp))
            StatusLine("上次更新", BoardRepository.formatTime(state.lastUpdateAt))
            if (state.autoUpdateEnabled) {
                StatusLine(
                    "下次自动更新",
                    if (state.nextUpdateAt <= 0) "—"
                    else "${BoardRepository.formatTime(state.nextUpdateAt)}" +
                        "（${BoardRepository.formatRelative(state.nextUpdateAt)}）",
                )
            } else {
                StatusLine("下次自动更新", "已关闭（仅手动）")
            }
            StatusLine("增量覆盖", "${state.liveIssueCount} 期 / ${state.liveSongCount} 首")

            Spacer(Modifier.height(12.dp))
            GlassSurface(
                dark = dark,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                onClick = if (state.updating) null else onUpdateNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = if (state.updating) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (state.updating) "正在更新…" else "立即更新一次",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.updating) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            state.lastMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.lastError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(2).fillMaxWidth(),
        ) {
            Text(
                text = "缩短周期只会让增量更新更频繁，不会重新下载整份数据；" +
                    "内置基线快照始终可用，所以离线也不影响浏览。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 列表行本身不再铺一层玻璃：外层卡片已经是玻璃面板，再叠一层会「玻璃叠玻璃」
 * 变白（深色模式下尤其明显）。这里只做可点击 + 内边距，
 * 文字颜色由外层 GlassSurface 提供的 LocalContentColor 决定。
 */
@Composable
private fun IntervalOption(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun intervalLabel(days: Int): String = when (days) {
    0 -> "仅手动更新"
    -1 -> "每天 18:00"
    1 -> "每天"
    else -> "每 $days 天"
}

private fun intervalHint(days: Int): String = when (days) {
    0 -> "不自动联网，只在点「立即更新」时请求一次"
    -1 -> "18:00 之后第一次打开时自动更新"
    1 -> "最及时，但每天都会联网"
    3 -> "默认，兼顾时效与流量"
    7 -> "每周一次"
    14 -> "每两周一次"
    else -> "一个月一次"
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 外观档位。与 IntervalOption 同样不叠玻璃：外层卡片已经是玻璃面板，
 * 这里只用一个半透明底色区分选中态，避免「玻璃叠玻璃」在深色下变白。
 */
@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}


/** Android 13 以下默认有通知权限；13+ 要运行时申请 */
@Composable
private fun rememberNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    // 与 IntervalOption 同理：外层卡片已经是玻璃，这里不再叠一层
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 列表内的分隔线（比整条 1dp 更淡） */
@Composable
private fun RowDivider(dark: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 47.dp, end = 16.dp)
            .height(1.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(glassBorderBrush(dark))
        )
    }
}
