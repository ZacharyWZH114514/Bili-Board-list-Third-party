package za.bilbd.app.data

import org.json.JSONArray
import org.json.JSONObject

/** AI 分析结果 */
sealed interface AiResult {
    /**
     * @param model 我们**请求**的模型（用户在设置里选的那个）
     * @param servedModel 服务端在响应里自称的模型。中转站/网关经常无视请求里的 model，
     *   返回它自己路由到的那个；以前直接把 servedModel 当展示值，于是出现
     *   「设置填的是 pro，界面上却显示 flash」。两个都留着，界面以请求值为准，不同就提示。
     */
    data class Ok(
        val text: String,
        val model: String,
        val servedModel: String = "",
        /** 联网搜索引用的来源（DeepSeek 自带搜索时才有），只用于展示 */
        val citations: List<WebSource> = emptyList(),
    ) : AiResult

    data class Err(val message: String) : AiResult
}

/** 多轮对话里的一条消息 */
data class ChatMessage(val role: String, val content: String) {
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/**
 * DeepSeek 对话接口。
 *
 * 用户的 API Key 由用户自己在「设置 → AI 分析」里填，应用不内置、不上传任何 Key。
 * 官方兼容 OpenAI 的 /chat/completions 格式，所以这里也顺带支持自定义 Base URL，
 * 方便接 DeepSeek 之外的兼容服务。
 */
class DeepSeekApi {

    /** 单轮：一段系统提示 + 一次用户提问 */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        webSearch: Boolean = false,
    ): AiResult = chatWithHistory(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        messages = listOf(
            ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt),
            ChatMessage(ChatMessage.ROLE_USER, userPrompt),
        ),
        webSearch = webSearch,
    )

    /**
     * 多轮对话。追问功能用它。
     *
     * 关键点：**资料始终作为同一条 system 消息带着**，后面才接往来消息。
     * 这样追问是「基于那份资料接着答」，而不是让模型脱离资料自由发挥 ——
     * 问「这首的作词是谁」时，模型看的仍是同一批检索结果。
     */
    suspend fun chatWithHistory(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        /**
         * 开 DeepSeek 自带的联网搜索（2026-11 的全家桶应用 API）。
         *
         * 做法是在 `tools` 里声明 `deepseek_web_search`，搜索与抓取全在服务端完成，
         * 客户端什么都不用执行；引用会通过 `message.annotations` 回来。
         * 注意：**只有 `deepseek-flash` 支持**；且不能同时设 `tool_choice` 去强制它。
         */
        webSearch: Boolean = false,
    ): AiResult {
        if (apiKey.isBlank()) return AiResult.Err("还没有填 API Key，请到「设置 → AI 分析」里填写")
        val url = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val payload = buildChatPayload(model, messages, webSearch)

        val res = Http.postJson(
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer ${apiKey.trim()}",
                "Accept" to "application/json",
            ),
            body = payload,
        ) ?: return AiResult.Err("请求失败：连不上 $url（检查网络）")

        if (res.code !in 200..299) {
            val detail = runCatching {
                JSONObject(res.body).optJSONObject("error")?.optString("message")
            }.getOrNull()
            val friendly = when (res.code) {
                401 -> "API Key 无效或已失效"
                402 -> "账户余额不足"
                429 -> "请求太频繁，稍后再试"
                else -> "HTTP ${res.code}"
            }
            return AiResult.Err(listOfNotNull(friendly, detail?.takeIf { it.isNotBlank() }).joinToString("："))
        }

        return parseChatResponse(res.body, model)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"

        /**
         * 可选模型。
         *
         * 2026-04 起 `deepseek-chat` / `deepseek-reasoner` 这两个旧名字指向 V4-Flash，
         * 官方公告说 2026-07-24 之后停用；现在实际在用的是 `deepseek-flash`（V4.1-Flash）。
         * 联网搜索只有 `deepseek-flash` 支持，所以它排在第一位当默认。
         */
        val MODELS = listOf("deepseek-flash", "deepseek-v4-pro")

        /** 旧模型名 → 新名字。老配置里存的 `deepseek-chat` 已经调不通了 */
        private val LEGACY_MODELS = mapOf(
            "deepseek-chat" to "deepseek-flash",
            "deepseek-reasoner" to "deepseek-flash",
            "deepseek-v4-flash" to "deepseek-flash",
        )

        internal fun migrateModel(model: String): String = LEGACY_MODELS[model.trim()] ?: model

        /** 构造 /chat/completions 的请求体（抽出来是为了能单测，不用真的发请求） */
        internal fun buildChatPayload(
            model: String,
            messages: List<ChatMessage>,
            webSearch: Boolean,
        ): String = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("temperature", 0.7)
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { m ->
                        put(JSONObject().put("role", m.role).put("content", m.content))
                    }
                },
            )
            if (webSearch) {
                // 服务端工具：搜索与抓取都在 DeepSeek 那边完成，客户端不需要执行任何工具
                put(
                    "tools",
                    JSONArray().put(JSONObject().put("type", "deepseek_web_search")),
                )
            }
        }.toString()

        /**
         * 解析 /chat/completions 的返回。
         *
         * 联网时正文在 `choices[0].message.content`，引用在 `message.annotations`
         * （`type=url_citation`，带 url/title）。引用只用于展示来源，不参与后续追问。
         */
        internal fun parseChatResponse(body: String, requestedModel: String): AiResult {
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return AiResult.Err("返回内容无法解析")
            val message = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
            val text = message?.optString("content").orEmpty()
            if (text.isBlank()) return AiResult.Err("模型没有返回内容")

            val citations = ArrayList<WebSource>()
            val ann = message?.optJSONArray("annotations")
            if (ann != null) {
                for (i in 0 until ann.length()) {
                    val c = ann.optJSONObject(i)?.optJSONObject("url_citation") ?: continue
                    val u = c.optString("url")
                    if (u.isBlank()) continue
                    if (citations.any { it.url == u }) continue
                    val t = c.optString("title").ifBlank { u }
                    citations.add(WebSource(title = t, url = u, site = hostOf(u), summary = ""))
                }
            }

            return AiResult.Ok(
                text = text.trim(),
                model = requestedModel,
                servedModel = json.optString("model", "").trim(),
                citations = citations,
            )
        }

        private fun hostOf(url: String): String =
            runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    }
}
