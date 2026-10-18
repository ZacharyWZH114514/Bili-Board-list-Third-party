package za.bilbd.app.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AI 结果卡片的界面测试。
 *
 * 最重要的一条是 [doneState_rendersInsideLazyColumn_withoutCrash]：
 * 这张卡片放在 LazyColumn 的 item 里，而 LazyColumn 给 item 的主轴约束是**无限高**，
 * 一旦里面出现可滚动组件（verticalScroll / LazyColumn）就会抛异常。
 * 当初「AI 分析刚出结果就闪退」正是这个原因——只读代码看不出来，
 * 是用户装到手机上才发现的。现在它在 JVM 上就能拦住。
 *
 * 注意：Compose 测试里 `setContent` 每个用例只能调用一次，
 * 所以这里每个状态各自一个测试，而不是在一个测试里循环。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AiResultCardTest {

    @get:Rule
    val compose = createComposeRule()

    /** 长文本：结果越长，越容易触发「卡片被约束撑爆」那类问题 */
    private val longResult = "这首歌讲的是失去之后仍要往前走。" +
        "歌词里反复出现的「不要回头」既是自我安慰也是束缚，" +
        "副歌把情绪推到顶点之后用一段近乎耳语的低音收尾，反差很大。"

    /** 按真实用法渲染：卡片是 LazyColumn 的一个 item，后面还有别的 item */
    private fun render(
        state: AiUiState,
        ready: Boolean = true,
        onRun: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        compose.setContent {
            BiliBoardTheme(darkTheme = true) {
                LazyColumn {
                    item {
                        AiResultCard(
                            title = "AI 解读这首歌",
                            icon = Icons.Filled.Star,
                            state = state,
                            ready = ready,
                            idleHint = "先用豆包搜索联网查资料，再让模型写解读。",
                            runLabel = "解读这首歌",
                            loadingLabel = "正在联网查资料并写解读…",
                            missingKeyLabel = "去设置 API Key",
                            onRun = onRun,
                            onOpenSettings = onOpenSettings,
                            onClear = onClear,
                        )
                    }
                    item { Text("列表后续内容") }
                }
            }
        }
    }

    private fun assertCardAndFollowingItemRendered() {
        compose.onNodeWithText("AI 解读这首歌").assertIsDisplayed()
        compose.onNodeWithText("列表后续内容").assertExists()
    }

    // ---------- 闪退回归（核心） ----------

    @Test
    fun doneState_rendersInsideLazyColumn_withoutCrash() {
        render(AiUiState.Done(longResult, "deepseek-chat"))
        assertCardAndFollowingItemRendered()
    }

    @Test
    fun idleState_rendersInsideLazyColumn_withoutCrash() {
        render(AiUiState.Idle)
        compose.onNodeWithText("AI 解读这首歌").assertIsDisplayed()
    }

    @Test
    fun loadingState_rendersInsideLazyColumn_withoutCrash() {
        render(AiUiState.Loading)
        compose.onNodeWithText("AI 解读这首歌").assertIsDisplayed()
    }

    @Test
    fun failedState_rendersInsideLazyColumn_withoutCrash() {
        render(AiUiState.Failed("API Key 无效或已失效"))
        compose.onNodeWithText("AI 解读这首歌").assertIsDisplayed()
    }

    @Test
    fun veryLongResult_rendersInsideLazyColumn() {
        // 极端长度：模型没有字数上限时可能吐出很长的内容
        render(AiUiState.Done(longResult.repeat(20), "deepseek-chat"))
        compose.onNodeWithText("AI 解读这首歌").assertIsDisplayed()
    }

    // ---------- 各状态的内容 ----------

    @Test
    fun doneState_showsResultAndModelFootnote() {
        render(AiUiState.Done("这是解读结果", "deepseek-reasoner"))
        compose.onNodeWithText("这是解读结果").assertExists()
        compose.onNodeWithText("由 deepseek-reasoner 生成 · 内容仅供参考").assertExists()
    }

    @Test
    fun loadingState_showsLoadingLabel() {
        render(AiUiState.Loading)
        compose.onNodeWithText("正在联网查资料并写解读…").assertIsDisplayed()
    }

    @Test
    fun idleState_ready_showsRunButtonAndHint() {
        render(AiUiState.Idle, ready = true)
        compose.onNodeWithText("先用豆包搜索联网查资料，再让模型写解读。").assertExists()
        compose.onNodeWithText("解读这首歌").assertIsDisplayed()
    }

    @Test
    fun idleState_notReady_showsSettingsLabel() {
        render(AiUiState.Idle, ready = false)
        compose.onNodeWithText("去设置 API Key").assertIsDisplayed()
    }

    @Test
    fun failedState_ready_showsMessageAndRetry() {
        render(AiUiState.Failed("网络错误"), ready = true)
        compose.onNodeWithText("网络错误").assertExists()
        compose.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun failedState_notReady_showsSettingsLabel() {
        render(AiUiState.Failed("还没有填 Key"), ready = false)
        compose.onNodeWithText("还没有填 Key").assertExists()
        compose.onNodeWithText("去设置 API Key").assertIsDisplayed()
    }

    // ---------- 交互 ----------

    @Test
    fun runButton_invokesOnRun_whenReady() {
        var clicked = 0
        render(AiUiState.Idle, ready = true, onRun = { clicked++ })
        compose.onNodeWithText("解读这首歌").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun runButton_goesToSettings_whenKeyMissing() {
        // 没有 Key 时按钮不该空转，应把人送去设置页
        var opened = 0
        var ran = 0
        render(AiUiState.Idle, ready = false, onRun = { ran++ }, onOpenSettings = { opened++ })
        compose.onNodeWithText("去设置 API Key").performClick()
        assertEquals("没有 Key 时不该触发解读", 0, ran)
        assertEquals("应跳转设置页", 1, opened)
    }

    @Test
    fun retryButton_invokesOnRun_whenFailed() {
        var clicked = 0
        render(AiUiState.Failed("网络错误"), ready = true, onRun = { clicked++ })
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun clearButton_shownAndClickable_whenDone() {
        var cleared = 0
        render(AiUiState.Done("结果", "m"), onClear = { cleared++ })
        compose.onNodeWithContentDescription("清除结果").assertExists()
        compose.onNodeWithContentDescription("清除结果").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun clearButton_hidden_whenNotDone() {
        // 没有结果时不该出现「清除」
        render(AiUiState.Idle)
        compose.onNodeWithContentDescription("清除结果").assertDoesNotExist()
    }

    @Test
    fun copyButton_putsResultOnClipboard() {
        // 生成了 400 字解读却拿不出去是没用的，所以复制是一条真实需求
        render(AiUiState.Done("这首歌讲的是失去之后仍要往前走。", "deepseek-chat"))
        compose.onNodeWithText("复制").performClick()

        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val copied = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        org.junit.Assert.assertEquals("这首歌讲的是失去之后仍要往前走。", copied)
    }

    @Test
    fun copyButton_hidden_whenNotDone() {
        render(AiUiState.Idle)
        compose.onNodeWithText("复制").assertDoesNotExist()
    }
}
