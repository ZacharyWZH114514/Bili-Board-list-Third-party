package za.bilbd.app.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import za.bilbd.app.MainActivity
import za.bilbd.app.R
import za.bilbd.app.data.WidgetRow
import za.bilbd.app.data.WidgetSnapshot
import za.bilbd.app.data.WidgetStore
import androidx.glance.layout.Box

/**
 * 桌面小组件：把「周榜最新一期」钉在桌面上。
 *
 * 数据来自 [WidgetStore]（App 里算好的一小段 JSON），小组件自己不联网、不解析快照 ——
 * 桌面上的东西一旦卡住，用户感受比 App 卡还糟。
 *
 * 尺寸自适应：格子能从 4×2 拉到 4×4 甚至更高，所以榜单条目用**可滚动列表**渲染，
 * 能放几条就画几条，剩下的滑一下就出来 —— 而不是写死三行、底下留一大片空白。
 * （Android 12 以下的桌面不支持滚动组件，退回按格子高度算行数的固定布局。）
 */
class ChartWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetStore.load(context)
        val manualRows = WidgetStore.rows(context)
        provideContent {
            GlanceTheme {
                WidgetBody(context, data, manualRows)
            }
        }
    }
}

/** 品牌色，和 App 里一致 */
private val Ink = Color(0xFF4A2A5C)
private val Muted = Color(0xFF7C6788)
private val Accent = Color(0xFFC2185B)

@Composable
private fun WidgetBody(context: Context, data: WidgetSnapshot?, manualRows: Int) {
    // 当前格子的真实大小（dp）
    val size = LocalSize.current
    // 条数按「可用高度 ÷ 单行高度」算，不要写死档位：
    // 写死档位必然有某个尺寸差半行，最后一条被底边切掉半截
    // （4×2 上第三条文字显示不全就是这么来的）
    val heightDp = size.height.value
    // 用户在设置里锁定了条数就用它（部分桌面不上报格子尺寸，自动判断会一直偏小）
    val autoRows = WidgetLayout.rowsFor(heightDp)
    val visibleRows = if (manualRows > 0) manualRows else autoRows
    // 排版档位全部由**最终条数**决定，跟算条数用的行高保持一致，不会算出 3 条却按 46dp 画
    val roomy = WidgetLayout.isRoomy(visibleRows)
    val dense = WidgetLayout.isDense(visibleRows)
    val ultra = WidgetLayout.isUltra(visibleRows)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // 半透明渐变打底，让桌面壁纸透上来
            .background(ImageProvider(R.drawable.widget_bg))
            // 点哪里都打开 App；具体到某一首的深链留到以后
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        // 顶部玻璃高光（贴在圆角里），叠在底色之上、内容之下
        Spacer(
            GlanceModifier
                .fillMaxWidth()
                .height(if (roomy) 42.dp else 34.dp)
                .background(ImageProvider(R.drawable.widget_sheen))
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = if (roomy) 16.dp else 13.dp),
        ) {
        if (data == null) {
            EmptyState()
            return@Column
        }

        Header(data, roomy)
        Spacer(GlanceModifier.height(if (roomy) 8.dp else 6.dp))

        val rows = data.rows
        // 只把放得下的那几条画出来：条数完全跟随格子高度，底部不再放「还有 N 首」之类的提示
        rows.take(visibleRows).forEach { row -> WidgetRowItem(context, row, roomy, dense, ultra) }
            // 剩下的高度用弹性空白吃掉，内容不会全挤在上面
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun Header(data: WidgetSnapshot, roomy: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.boardName,
            style = TextStyle(
                color = ColorProvider(Ink),
                fontSize = if (roomy) 15.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = data.issueLabel,
            style = TextStyle(color = ColorProvider(Accent), fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
    Spacer(GlanceModifier.height(2.dp))
    Text(
        text = data.dateText,
        style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp),
    )
}

@Composable
private fun WidgetRowItem(
    context: Context,
    row: WidgetRow,
    roomy: Boolean,
    dense: Boolean,
    ultra: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .padding(vertical = if (ultra) 0.dp else if (roomy) 2.dp else 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 名次：前三用品牌色加粗，其余用灰色
        Text(
            text = row.rank.toString(),
            style = TextStyle(
                color = ColorProvider(if (row.rank <= 3) Accent else Muted),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.width(20.dp),
        )
        // 封面：App 里已经提前抓好小图放进 cacheDir，这里只读文件，绝不联网
        val cover = WidgetCovers.load(context, row.bvid)
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .size(when {
                        ultra -> 22.dp
                        roomy -> 30.dp
                        else -> 26.dp
                    })
                    .cornerRadius(9.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = row.title,
                style = TextStyle(
                    color = ColorProvider(Ink),
                    fontSize = if (ultra) 10.sp else if (roomy) 12.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            // 第二行：中文译名 · P主。没有译名的曲子只显示 P主，不留个「 · 」在前面
            val sub = listOf(row.titleCn, row.artist)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = TextStyle(
                        color = ColorProvider(Muted),
                        fontSize = if (ultra) 9.sp else 10.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = row.viewsText,
            style = TextStyle(color = ColorProvider(Muted), fontSize = 10.sp),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Vocaloid List",
            style = TextStyle(color = ColorProvider(Ink), fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "打开一次 App，这里就会显示周榜",
            style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp),
        )
    }
}

/** 系统广播的接收入口（Glance 帮我们把 RemoteViews 的活儿都干了） */
class ChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChartWidget()

    /**
     * 用户拉动格子改变大小时，内容必须跟着重画 —— 否则行数还是按旧尺寸算的，
     * 拉长了也只是底下多一片空白。Glance 默认会处理，但实测在 AOSP 桌面上不一定触发，
     * 这里显式再推一次，代价很小。
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { glanceAppWidget.updateAll(context) }
            pending.finish()
        }
    }
}

/** 在 App 里刷新小组件 */
object WidgetRefresher {
    suspend fun refresh(context: Context, snapshot: WidgetSnapshot) {
        WidgetStore.save(context, snapshot)
        // 先把封面抓到本地缓存，再刷新 —— 否则小组件第一帧会是空的
        runCatching { WidgetCovers.ensure(context, snapshot) }
        runCatching { ChartWidget().updateAll(context) }
    }
}
