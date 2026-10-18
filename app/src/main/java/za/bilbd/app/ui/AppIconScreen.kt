package za.bilbd.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 自定义图标用的位图边长（太大可能顶到 Binder 事务上限） */
private const val ICON_PX = 288

/**
 * 应用图标设置。
 *
 * - 预设配色：切换真正的启动器图标（activity-alias）
 * - 自定义图片：受系统限制无法替换启动器图标，改为在桌面创建一个用该图的入口
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconScreen(dark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentId by AppIconState.currentId.collectAsStateWithLifecycle()
    val current = IconPalettes.byId(currentId)

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasCustom by remember { mutableStateOf(AppIconSwitcher.hasCustomIcon(context)) }
    val pinSupported = remember { AppIconSwitcher.isPinSupported(context) }

    val appLabel = remember {
        runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault("Vocaloid List")
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        message = null
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadSquareBitmap(context, uri) }
            val ok = if (bitmap != null) {
                AppIconSwitcher.pinCustomIcon(context, bitmap, appLabel)
            } else false
            busy = false
            hasCustom = AppIconSwitcher.hasCustomIcon(context)
            message = when {
                !ok -> "创建失败：桌面不支持固定快捷方式，或图片过大"
                else -> "已发送请求。若桌面弹出确认框请点「添加」；否则稍等片刻即可看到新图标。"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---------------- 当前图标 ----------------
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(18.dp),
            modifier = Modifier.cascade(0).fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconTile(palette = current, size = 96.dp, corner = 24.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "当前图标：${current.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------- 自定义图片 ----------------
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(1).fillMaxWidth(),
        ) {
            SectionTitle("自定义图片")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android 不允许第三方应用把启动图标换成用户自选的图片" +
                    "（图标只能是打包进 APK 的资源）。所以这里改为：用你选的图片在桌面创建一个入口，" +
                    "点它一样进入本应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "建议选主体居中的方形图；桌面会按图标形状裁掉四周一小圈。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!pinSupported) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前桌面不支持创建固定快捷方式，换一个桌面（如 Pixel Launcher）再试。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconActionButton(
                    dark = dark,
                    icon = Icons.Filled.Add,
                    label = "选择图片",
                    enabled = pinSupported && !busy,
                    prominent = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
                if (hasCustom) {
                    Spacer(Modifier.width(8.dp))
                    IconActionButton(
                        dark = dark,
                        icon = Icons.Filled.Delete,
                        label = "移除",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppIconSwitcher.removeCustomIcon(context)
                            hasCustom = false
                            message = "已移除桌面上的自定义图标。"
                        },
                    )
                }
            }

            if (busy) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在处理图片…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ---------------- 预设配色 ----------------
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.cascade(2).fillMaxWidth(),
        ) {
            SectionTitle("预设配色")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这几个切换的是真正的启动图标，选完稍等片刻桌面就会更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconPalettes.all.forEachIndexed { index, palette ->
                    PaletteChoice(
                        palette = palette,
                        selected = palette.id == currentId,
                        dark = dark,
                        modifier = Modifier.cascade(index, span = 8),
                        onClick = {
                            val ok = AppIconSwitcher.applyPalette(context, palette)
                            message = if (ok) {
                                "已切换到「${palette.label}」。桌面图标可能需要几秒才刷新；" +
                                    "若应用自动关闭了一次属正常现象，重新打开即可。"
                            } else {
                                "切换失败，请重试。"
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "图标与「关于」页中的应用图标使用同一套矢量资源。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PaletteChoice(
    palette: IconPalette,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 整项（含下方文字）都可点，避免只有图标块响应
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            GlassSurface(
                // 跟着主题走；写死 dark = true 会让浅色模式下这块呈深色
                dark = dark,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.padding(6.dp)) {
                    AppIconTile(palette = palette, size = 54.dp, corner = 14.dp)
                }
            }
            if (selected) {
                Box(
                    Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconActionButton(
    dark: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        prominent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp),
        onClick = if (enabled) onClick else null,
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
        }
    }
}

/** 把选中的图片解码成正方形位图（Coil 负责降采样与 EXIF 旋转） */
private suspend fun loadSquareBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(ICON_PX, ICON_PX)
        .scale(Scale.FILL)
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    return (result as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
}
