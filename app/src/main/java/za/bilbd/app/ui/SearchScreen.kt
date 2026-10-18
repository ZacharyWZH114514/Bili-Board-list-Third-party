package za.bilbd.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val HOT_KEYWORDS = listOf(
    "初音ミク", "重音テト", "ピノキオピー", "DECO*27", "ボカロ", "鏡音リン", "可不", "ずんだもん",
)

@Composable
fun SearchScreen(vm: AppViewModel, dark: Boolean, onOpenSong: (String) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val biliMap by vm.biliVideos.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth()) {
        GlassSearchField(
            value = query,
            dark = dark,
            onValueChange = vm::onQueryChange,
            onClear = vm::clearQuery,
        )

        if (query.isBlank()) {
            SearchLanding(state.catalog?.songs?.size, state.catalogLoading, dark, onPick = vm::onQueryChange)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "命中 ${hits.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.catalogLoading) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "载入离线曲库…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            itemsIndexed(hits, key = { _, h -> h.bvid }) { index, hit ->
                // 没上过榜的曲子 biliboard 图床多半没有封面，向 B 站要一张
                val biliCover = biliMap[hit.bvid]?.coverUrl
                LaunchedEffect(hit.bvid) {
                    if (hit.chartCount == 0 && biliCover.isNullOrBlank()) vm.ensureBili(hit.bvid)
                }
                HitRow(
                    hit = hit,
                    dark = dark,
                    onClick = { onOpenSong(hit.bvid) },
                    modifier = Modifier.cascade(index),
                    coverUrl = biliCover,
                )
            }

            if (hits.isEmpty() && !state.catalogLoading) {
                item {
                    GlassSurface(
                        dark = dark,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = "没有匹配的曲目",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "试试曲名、P主或歌手的其它写法，例如「miku」「匹诺曹P」「初音未来」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 玻璃胶囊搜索框 */
@Composable
private fun GlassSearchField(
    value: String,
    dark: Boolean,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 15.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "曲名 / 中文名 / P主 / 歌手 / BV号",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    catalogCount: Int?,
    catalogLoading: Boolean,
    dark: Boolean,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "搜索全站术曲",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (catalogLoading) "正在载入离线曲库…"
                else "离线检索内置的全站曲库（${catalogCount ?: 0} 首），支持曲名、中文名、P主、歌手与别名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "热门搜索",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HOT_KEYWORDS.forEach { kw ->
                SuggestionChip(
                    onClick = { onPick(kw) },
                    label = { Text(kw, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
