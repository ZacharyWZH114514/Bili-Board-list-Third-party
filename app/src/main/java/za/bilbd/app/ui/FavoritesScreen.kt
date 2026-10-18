package za.bilbd.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.bilbd.app.data.BiliVideo
import za.bilbd.app.data.FavoritesCodec
import za.bilbd.app.data.ImportResult
import za.bilbd.app.data.SongHit

/**
 * 收藏页。
 *
 * 支持导出/导入：收藏原本只存在本机 SharedPreferences 里，换机、重装、改包名都会丢
 * （改包名那次真实发生过）。现在可以自己备份、迁移。
 *
 * 注意**没有收藏时也必须能导入** —— 重装之后正是「零收藏 + 有一份备份文件」的场景，
 * 所以导出/导入入口放在头部，不受空列表影响。
 */
@Composable
fun FavoritesScreen(
    favoriteIds: Set<String>,
    lookup: (String) -> SongHit?,
    dark: Boolean,
    onOpenSong: (String) -> Unit,
    onClear: () -> Unit,
    biliVideos: Map<String, BiliVideo> = emptyMap(),
    onNeedBili: (String) -> Unit = {},
    /** 导出时写进文件的内容，由上层提供（需要曲库信息才能带上曲名/P主） */
    buildExport: () -> String = { "" },
    /** 导入解析结果交给上层去合并并提示 */
    onImportResult: (ImportResult) -> Unit = {},
    /** 文件读写失败时的提示 */
    onIoError: (String) -> Unit = {},
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(buildExport().toByteArray(Charsets.UTF_8))
            } ?: error("无法写入所选位置")
        }.onFailure { onIoError("导出失败：${it.message ?: it.javaClass.simpleName}") }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取所选文件")
        }.getOrElse {
            onIoError("读取失败：${it.message ?: it.javaClass.simpleName}")
            return@rememberLauncherForActivityResult
        }
        onImportResult(FavoritesCodec.decode(text))
    }

    Column(Modifier.fillMaxWidth()) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (favoriteIds.isEmpty()) "还没有收藏" else "已收藏 ${favoriteIds.size} 首",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (favoriteIds.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("清空") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    ) { Text("导入") }
                    TextButton(
                        onClick = { exportLauncher.launch("Vocaloid List 收藏.json") },
                        enabled = favoriteIds.isNotEmpty(),
                    ) { Text("导出") }

                    Text(
                        text = "导出成 JSON 文件，换机或重装后用「导入」找回",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }
        }

        if (favoriteIds.isEmpty()) {
            MessagePane(
                title = "还没有收藏",
                detail = "在榜单或搜索结果里点开一首曲子，用「收藏」按钮把它收进来。" +
                    "已有备份文件的话，点上面的「导入」就能恢复。",
            )
            return@Column
        }

        val hits: List<SongHit?> = favoriteIds.map { lookup(it) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            itemsIndexed(hits, key = { _, hit -> hit?.bvid ?: "null" }) { index, hit ->
                if (hit == null) {
                    MissingRow(dark, Modifier.cascade(index))
                } else {
                    val biliCover = biliVideos[hit.bvid]?.coverUrl
                    LaunchedEffect(hit.bvid) {
                        if (hit.chartCount == 0 && biliCover.isNullOrBlank()) onNeedBili(hit.bvid)
                    }
                    HitRow(
                        hit = hit,
                        dark = dark,
                        onClick = { onOpenSong(hit.bvid) },
                        modifier = Modifier.cascade(index),
                        coverUrl = biliCover,
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "查看详情",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingRow(dark: Boolean, modifier: Modifier = Modifier) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Text(
            text = "该曲目暂无法解析（可能已从曲库移除）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
