package za.bilbd.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import za.bilbd.app.data.ChartPoint
import za.bilbd.app.data.Credit
import za.bilbd.app.data.Song

/**
 * 曲目详情页的布局测试。
 *
 * 这一页被反馈过「排版错乱、内容重叠」。当时的原因是外层用 `Box` 把各区块叠在了同一位置——
 * **Box 会把子项堆叠**，而代码上看不出任何异常，只能靠眼睛。
 *
 * 这里用节点坐标直接断言：各区块必须**自上而下依次排列、互不重叠**。
 * 现在整页是单个 LazyColumn，天然不会重叠；这条测试把这个约束固定下来，
 * 以后谁再把结构改回叠层，测试会立刻失败。
 *
 * 用高一点的窗口（h2400dp）让更多区块进入组合范围。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class SongDetailLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private fun point(issueId: Int, rank: Int) = ChartPoint(
        boardId = 1,
        boardName = "周榜",
        issueId = issueId,
        year = 2026,
        week = issueId - 80,
        rank = rank,
        views = 1_000_000L * issueId,
        likes = 10_000L,
        coins = 5_000L,
        favorites = 3_000L,
        score = 900_000L + issueId,
    )

    private val song = Song(
        bvid = "BV1UGa961Ejt",
        title = "えねみぃ",
        titleCn = "敌人",
        producers = listOf(Credit("ピノキオピー", null)),
        vocalists = listOf(Credit("初音ミク", null)),
        pubtime = 1_700_000_000L,
        firstRecordedAt = null,
        history = listOf(point(121, 3), point(122, 1)),
    )

    private fun renderDetail(dark: Boolean, window: WindowWidth = WindowWidth.Compact) {
        compose.setContent {
            BiliBoardTheme(darkTheme = dark) {
                SongDetailScreen(
                    bvid = song.bvid,
                    song = song,
                    fallbackHit = null,
                    isFavorite = false,
                    dark = dark,
                    window = window,
                    onToggleFavorite = {},
                    bili = null,
                    insight = AiUiState.Idle,
                    insightReady = true,
                )
            }
        }
    }

    /** 页面上的区块标题，顺序即预期自上而下的顺序 */
    private val sectionTitles = listOf("AI 解读这首歌", "创作信息", "最近一次上榜数据", "播放量走势（按上榜期数）")

    @Test
    fun sections_appearTopToBottom_withoutOverlap() {
        renderDetail(dark = true)

        val bounds = sectionTitles.mapNotNull { title ->
            val node = compose.onNodeWithText(title)
            // 未被组合（还在视口外）的区块跳过，不代表出错
            runCatching { node.getUnclippedBoundsInRoot() }.getOrNull()?.let { title to it }
        }

        assertTrue(
            "至少应有 3 个区块被渲染出来，实际只有 ${bounds.size} 个：${bounds.map { it.first }}",
            bounds.size >= 3,
        )

        // 1) 顺序必须与预期一致
        val presentTitles = bounds.map { it.first }
        val expectedOrder = sectionTitles.filter { it in presentTitles }
        assertTrue(
            "区块顺序不对：$presentTitles，预期 $expectedOrder",
            presentTitles == expectedOrder,
        )

        // 2) 相邻区块不能重叠（这是「排版错乱」的直接判据）
        for (i in 0 until bounds.size - 1) {
            val (titleA, a) = bounds[i]
            val (titleB, b) = bounds[i + 1]
            assertTrue(
                "「$titleA」底部 ${a.bottom} 压到了「$titleB」顶部 ${b.top}",
                a.bottom <= b.top + 0.5.dp,
            )
        }
    }

    @Test
    fun sectionTitlesAreAllPresent() {
        // 区块标题本身不该缺失（少一块就是内容丢了）
        renderDetail(dark = true)
        for (title in sectionTitles) {
            compose.onNodeWithText(title).assertExists()
        }
    }

    @Test
    fun rendersInDarkTheme() {
        renderDetail(dark = true)
        compose.onNodeWithText("创作信息").assertExists()
    }

    @Test
    fun rendersInLightTheme() {
        renderDetail(dark = false)
        compose.onNodeWithText("创作信息").assertExists()
    }

    /**
     * 平板（Expanded）走两栏：封面在左、指标与操作在右。
     *
     * 判据用**横向位置**：封面的曲名必须整个落在「最高排名」左侧。
     * 单栏时这两个是上下关系，这条断言会失败 —— 所以它能真正区分两种布局，
     * 而不是「反正都能渲染」。
     */
    @Test
    fun expanded_coverAndStatsAreSideBySide() {
        renderDetail(dark = true, window = WindowWidth.Expanded)

        val coverTitle = compose.onNodeWithText("えねみぃ").getUnclippedBoundsInRoot()
        val firstStat = compose.onNodeWithText("最高排名").getUnclippedBoundsInRoot()

        assertTrue(
            "平板下封面应在左、指标在右（封面右边界 ${coverTitle.right}，" +
                "指标左边界 ${firstStat.left}）",
            coverTitle.right <= firstStat.left + 0.5.dp,
        )
    }

    /** 与上一条互为对照：单栏时封面与指标必须是上下关系 */
    @Test
    fun compact_coverAndStatsAreStacked() {
        renderDetail(dark = true, window = WindowWidth.Compact)

        val coverTitle = compose.onNodeWithText("えねみぃ").getUnclippedBoundsInRoot()
        val firstStat = compose.onNodeWithText("最高排名").getUnclippedBoundsInRoot()

        assertTrue(
            "手机单栏下指标应在封面下方（封面底 ${coverTitle.bottom}，指标顶 ${firstStat.top}）",
            coverTitle.bottom <= firstStat.top + 0.5.dp,
        )
    }

    /** 排名走势两种布局下都要在 */
    @Test
    fun rankTrendCard_isPresent() {
        renderDetail(dark = true, window = WindowWidth.Compact)
        compose.onNodeWithText("排名走势").assertExists()
    }

    /**
     * 负向对照：证明「并排」判据不是永远通过的。
     * 故意用 Box 把两块叠在同一位置，这个判据应当判定为**不**并排。
     */
    @Test
    fun sideBySidePredicate_canActuallyFail() {
        compose.setContent {
            BiliBoardTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.Text("甲", modifier = Modifier.testTag("p-a"))
                    androidx.compose.material3.Text("乙", modifier = Modifier.testTag("p-b"))
                }
            }
        }
        val a = compose.onNodeWithTag("p-a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("p-b").getUnclippedBoundsInRoot()
        assertTrue(
            "叠放的两块不该被判为并排（a=$a b=$b）",
            a.right > b.left,
        )
    }

    @Test
    fun songTitleAndMetaAreShown() {
        renderDetail(dark = true)
        compose.onNodeWithText("えねみぃ").assertExists()
        compose.onNodeWithText("敌人").assertExists()
        compose.onNodeWithText("ピノキオピー").assertExists()
        compose.onNodeWithText("初音ミク").assertExists()
    }

    /**
     * 负向对照：证明上面那条「不重叠」判据不是永远通过的。
     *
     * 这里故意用 `Box` 叠两个 Text —— 这正是当初详情页排版错乱的结构
     * （Box 会把子项堆在同一个位置）。如果判据本身写错了，这条会失败，
     * 上面那条断言也就失去了意义。
     */
    @Test
    fun overlapCheck_canActuallyDetectOverlap() {
        compose.setContent {
            BiliBoardTheme(darkTheme = true) {
                Box {
                    Text("上面这块", modifier = Modifier.testTag("probe-a"))
                    Text("叠在同一位置的另一块", modifier = Modifier.testTag("probe-b"))
                }
            }
        }

        val a = compose.onNodeWithTag("probe-a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("probe-b").getUnclippedBoundsInRoot()

        val overlaps = a.top < b.bottom && b.top < a.bottom
        assertTrue(
            "Box 叠放的两块应当被判为重叠，否则说明重叠判据失效（a=$a b=$b）",
            overlaps,
        )
    }
}
