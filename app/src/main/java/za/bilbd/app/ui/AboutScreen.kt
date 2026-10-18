package za.bilbd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 关于页。
 * 手机（竖屏）：上下两半屏——上半屏图标 / 名称 / 作者，下半屏应用说明与数据来源。
 * 平板（宽屏）：左右两栏——左栏同上半屏，右栏同下半屏，避免横向空间浪费。
 */
@Composable
fun AboutScreen(
    dark: Boolean,
    window: WindowWidth,
    onOpenAppIcon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val iconId by AppIconState.currentId.collectAsStateWithLifecycle()

    if (window.isWide) {
        Row(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                IdentityColumn(
                    dark = dark,
                    versionName = versionName,
                    palette = IconPalettes.byId(iconId),
                    onOpenAppIcon = onOpenAppIcon,
                )
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(glassBorderBrush(dark)))
            Box(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                InfoColumn(dark = dark)
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IdentityColumn(
                    dark = dark,
                    versionName = versionName,
                    palette = IconPalettes.byId(iconId),
                    onOpenAppIcon = onOpenAppIcon,
                )
            }
            // 中缝：一道玻璃高光
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(glassBorderBrush(dark))
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                InfoColumn(dark = dark)
            }
        }
    }
}

/** 图标 + 名称 + 作者 */
@Composable
private fun IdentityColumn(
    dark: Boolean,
    versionName: String,
    palette: IconPalette,
    onOpenAppIcon: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        // 点图标即可进入图标设置
        Box(
            Modifier.clickable(onClick = onOpenAppIcon),
            contentAlignment = Alignment.Center,
        ) {
            AppIconTile(palette = palette, size = 100.dp, corner = 24.dp)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Vocaloid List",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Biliboard 术力口榜 Android 第三方客户端",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text(
                text = "作者",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Zachary & 大肥鱼",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(10.dp))
        ChangeIconButton(dark = dark, label = "更改图标", onClick = onOpenAppIcon)

        if (versionName.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "版本 $versionName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangeIconButton(dark: Boolean, label: String, onClick: () -> Unit) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Create,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 应用说明 + 数据来源 */
@Composable
private fun InfoColumn(dark: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionTitle("应用说明")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "用 biliboard.uk 的公开数据做的术曲榜单客户端：按期翻榜单、看每首曲子的排名与" +
                    "播放走势、搜索全站曲库，也能收藏喜欢的曲子。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FeatureLine(Icons.Filled.Star, "三个榜单：周榜 / 传说曲周榜 / 年榜")
            FeatureLine(Icons.Filled.Search, "离线检索全站曲库，支持别名")
            FeatureLine(Icons.Filled.Favorite, "本地收藏")
            FeatureLine(Icons.Filled.Share, "收藏可导出成文件备份")
            FeatureLine(Icons.Filled.Star, "手机 / 平板自适应布局")
        }

        LicensesCard(dark = dark)

        GlassSurface(
            dark = dark,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionTitle("数据来源")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "biliboard.uk",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "榜单与曲目数据均来自 Biliboard 术力口周榜。数据版权归原站所有，" +
                    "本应用为第三方客户端，与 Biliboard 官方无关，仅作学习与展示用途。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val uriHandler = LocalUriHandler.current
            GlassActionRow(
                dark = dark,
                label = "打开 biliboard.uk",
                icon = Icons.Filled.Share,
                onClick = { runCatching { uriHandler.openUri("https://biliboard.uk/") } },
            )
        }
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GlassActionRow(
    dark: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
