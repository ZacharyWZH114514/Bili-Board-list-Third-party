package za.bilbd.app.data

/**
 * AI 服务商预设。
 *
 * 这些服务都兼容 OpenAI 的 `/chat/completions` 格式，所以差别只在 **Base URL 和模型名**，
 * 请求代码一行都不用改 —— 选预设时把这两样填好即可。
 *
 * 「自定义」留空，让用户自己填地址和模型（本地 Ollama、公司内网网关、任何兼容服务）。
 */
data class AiProvider(
    val id: String,
    val label: String,
    /** 会拼上 /chat/completions；自定义时为空，由用户填 */
    val baseUrl: String,
    val models: List<String>,
    /** 申请 Key 的入口；没有就不显示链接 */
    val keyUrl: String,
    /** 模型选择说明 */
    val hint: String,
) {
    val isCustom: Boolean get() = id == ID_CUSTOM

    companion object {
        const val ID_CUSTOM = "custom"
    }
}

object AiProviders {

    val ALL = listOf(
        AiProvider(
            id = "deepseek",
            label = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            models = listOf("deepseek-chat", "deepseek-reasoner"),
            keyUrl = "https://platform.deepseek.com/",
            hint = "deepseek-chat 更快更省；deepseek-reasoner 会先推理再回答，更慢但分析更细。",
        ),
        AiProvider(
            id = "openai",
            label = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            models = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
            keyUrl = "https://platform.openai.com/api-keys",
            hint = "gpt-4o-mini 便宜够用；gpt-4o / gpt-4.1-mini 写得更细但更贵。",
        ),
        AiProvider(
            id = "qwen",
            label = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            models = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
            keyUrl = "https://bailian.console.aliyun.com/",
            hint = "用的是阿里云百炼的「OpenAI 兼容」端点。qwen-turbo 最便宜，qwen-max 最强。",
        ),
        AiProvider(
            id = AiProvider.ID_CUSTOM,
            label = "自定义",
            baseUrl = "",
            models = emptyList(),
            keyUrl = "",
            hint = "填任何兼容 OpenAI /chat/completions 的地址，例如本地 Ollama 或其他中转服务。",
        ),
    )

    fun byId(id: String): AiProvider = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /**
     * 按已有的 Base URL 反查是哪个预设。
     * 认不出来就当自定义 —— 用户之前手填过地址时不该被强行归到某个服务商。
     */
    fun of(baseUrl: String): AiProvider {
        val u = baseUrl.trim().trimEnd('/')
        if (u.isEmpty()) return byId(AiProvider.ID_CUSTOM)
        return ALL.firstOrNull {
            it.baseUrl.isNotBlank() && it.baseUrl.trimEnd('/') == u
        } ?: byId(AiProvider.ID_CUSTOM)
    }
}
