package za.bilbd.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeek 请求体与返回解析。
 *
 * 这两块以前只能靠真机点一次「解读这首歌」来看，网络一抖就什么都测不到。
 * 抽成纯函数之后，联网搜索这种「多一个 tools 字段、引用在另一个字段里」的改动
 * 可以用单测钉住，不用真的花 token 去验。
 */
class DeepSeekApiTest {

    private val messages = listOf(
        ChatMessage(ChatMessage.ROLE_SYSTEM, "系统提示"),
        ChatMessage(ChatMessage.ROLE_USER, "用户提问"),
    )

    // ---------- 请求体 ----------

    @Test
    fun payload_hasNoToolsWhenSearchIsOff() {
        val json = JSONObject(DeepSeekApi.buildChatPayload("deepseek-flash", messages, webSearch = false))
        assertEquals("deepseek-flash", json.getString("model"))
        assertFalse("没开联网时不该带 tools", json.has("tools"))
        assertEquals(2, json.getJSONArray("messages").length())
    }

    @Test
    fun payload_declaresWebSearchToolWhenOn() {
        val json = JSONObject(DeepSeekApi.buildChatPayload("deepseek-flash", messages, webSearch = true))
        val tools = json.getJSONArray("tools")
        assertEquals(1, tools.length())
        assertEquals("deepseek_web_search", tools.getJSONObject(0).getString("type"))
        // 文档明确说 tool_choice 不能强制这些工具，带了会 400
        assertFalse("不能给 server tool 设 tool_choice", json.has("tool_choice"))
    }

    // ---------- 模型名迁移 ----------

    @Test
    fun legacyModelNamesMigrateToFlash() {
        // 2026-07-24 起旧名字停用；老配置里存的就是这些
        assertEquals("deepseek-flash", DeepSeekApi.migrateModel("deepseek-chat"))
        assertEquals("deepseek-flash", DeepSeekApi.migrateModel("deepseek-reasoner"))
        assertEquals("deepseek-flash", DeepSeekApi.migrateModel("deepseek-v4-flash"))
    }

    @Test
    fun currentModelNamesAreLeftAlone() {
        assertEquals("deepseek-flash", DeepSeekApi.migrateModel("deepseek-flash"))
        assertEquals("deepseek-v4-pro", DeepSeekApi.migrateModel("deepseek-v4-pro"))
        assertEquals("gpt-4o-mini", DeepSeekApi.migrateModel("gpt-4o-mini"))
    }

    @Test
    fun defaultModelIsTheOneThatSupportsWebSearch() {
        assertEquals("deepseek-flash", DeepSeekApi.MODELS.first())
    }

    // ---------- 返回解析 ----------

    /** 用 JSONObject 拼返回体，别手写 JSON——多一个括号就只有测试挂、看不出为什么 */
    private fun body(
        content: String,
        citations: List<Pair<String, String>> = emptyList(),
        model: String = "deepseek-flash",
    ): String {
        val message = JSONObject().apply {
            put("role", "assistant")
            put("content", content)
            if (citations.isNotEmpty()) {
                put(
                    "annotations",
                    org.json.JSONArray().apply {
                        citations.forEach { (url, title) ->
                            put(
                                JSONObject().put("type", "url_citation").put(
                                    "url_citation",
                                    JSONObject().put("url", url).put("title", title),
                                )
                            )
                        }
                    },
                )
            }
        }
        return JSONObject()
            .put("id", "x")
            .put("model", model)
            .put(
                "choices",
                org.json.JSONArray().put(JSONObject().put("index", 0).put("message", message)),
            )
            .toString()
    }

    @Test
    fun parse_readsTextAndCitations() {
        val r = DeepSeekApi.parseChatResponse(
            body(
                "答案正文",
                citations = listOf(
                    "https://www.example.com/a" to "来源甲",
                    "https://www.example.com/a" to "重复的同一条",
                    "https://b.test/post" to "",
                ),
            ),
            requestedModel = "deepseek-flash",
        )
        assertTrue(r is AiResult.Ok)
        r as AiResult.Ok
        assertEquals("答案正文", r.text)
        assertEquals("deepseek-flash", r.model)
        // 同一个 URL 只留一条
        assertEquals(2, r.citations.size)
        assertEquals("来源甲", r.citations[0].title)
        assertEquals("example.com", r.citations[0].site)
        // 标题为空时用 URL 顶上，界面上不会出现空行
        assertEquals("https://b.test/post", r.citations[1].title)
        assertEquals("b.test", r.citations[1].site)
    }

    @Test
    fun parse_withoutAnnotationsHasNoCitations() {
        val r = DeepSeekApi.parseChatResponse(body("答案"), requestedModel = "deepseek-flash")
        assertTrue(r is AiResult.Ok)
        assertTrue((r as AiResult.Ok).citations.isEmpty())
    }

    @Test
    fun parse_blankContentIsAnError() {
        val r = DeepSeekApi.parseChatResponse(body("   "), requestedModel = "deepseek-flash")
        assertTrue(r is AiResult.Err)
        assertTrue((r as AiResult.Err).message.contains("没有返回内容"))
    }

    @Test
    fun parse_reportsServedModelSeparatelyFromRequested() {
        // 中转站可能无视请求的模型，两者要分开记，界面才能提示
        val r = DeepSeekApi.parseChatResponse(
            body("答案", model = "deepseek-flash"),
            requestedModel = "deepseek-v4-pro",
        ) as AiResult.Ok
        assertEquals("deepseek-v4-pro", r.model)
        assertEquals("deepseek-flash", r.servedModel)
    }

    // ---------- 搜索开关 ----------

    @Test
    fun searchReady_followsTheChosenMode() {
        val base = AiConfig(keys = mapOf("deepseek" to "sk-x"))
        assertTrue("DeepSeek 内置搜索复用模型 Key", base.copy(searchMode = SearchMode.DeepSeek).searchReady)
        assertFalse(base.copy(searchMode = SearchMode.Off).searchReady)
        // 豆包要靠单独那把 Key
        assertFalse(base.copy(searchMode = SearchMode.Doubao).searchReady)
        assertTrue(
            base.copy(searchMode = SearchMode.Doubao, doubaoApiKey = "k").searchReady
        )
    }
}
