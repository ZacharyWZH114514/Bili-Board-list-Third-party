package za.bilbd.app.data

import org.json.JSONArray
import org.json.JSONObject

/** AI 分析结果 */
sealed interface AiResult {
    data class Ok(val text: String, val model: String) : AiResult
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
    ): AiResult = chatWithHistory(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        messages = listOf(
            ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt),
            ChatMessage(ChatMessage.ROLE_USER, userPrompt),
        ),
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
    ): AiResult {
        if (apiKey.isBlank()) return AiResult.Err("还没有填 API Key，请到「设置 → AI 分析」里填写")
        val url = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val payload = JSONObject().apply {
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
        }.toString()

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

        val json = runCatching { JSONObject(res.body) }.getOrNull()
            ?: return AiResult.Err("返回内容无法解析")
        val text = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        if (text.isBlank()) return AiResult.Err("模型没有返回内容")
        return AiResult.Ok(text.trim(), json.optString("model", model))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"

        /** 可选模型 */
        val MODELS = listOf("deepseek-chat", "deepseek-reasoner")
    }
}
