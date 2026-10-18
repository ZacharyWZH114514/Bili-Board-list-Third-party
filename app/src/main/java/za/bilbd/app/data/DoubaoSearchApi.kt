package za.bilbd.app.data

import org.json.JSONObject

/** 一条网页搜索结果 */
data class WebSource(
    val title: String,
    val url: String,
    val site: String,
    val summary: String,
)

/** 搜索结果或错误 */
sealed interface SearchOutcome {
    data class Ok(val sources: List<WebSource>) : SearchOutcome
    data class Err(val message: String) : SearchOutcome
}

/**
 * 豆包搜索（Custom 版）。
 *
 * 用来给「这首歌在讲什么」提供依据：模型自己并不知道某首术曲的歌词与背景，
 * 先联网搜到歌词/乐评/百科，再让模型基于这些材料做解读，比让它凭空发挥可靠得多。
 *
 * 接口要点（实测确认）：
 *  - `POST https://open.feedcoopapi.com/search_api/web_search`，`Authorization: Bearer <key>`
 *  - 请求体：`{ Query, SearchType: "web", Count, NeedSummary: true }`
 *  - `Query` 超过 100 字符会被服务端截断，本地先截
 *  - **错误可能是 HTTP 200 + `ResponseMetadata.Error`**，不能只看状态码
 *  - `Summary`（500~1000 字）比 `Snippet`（约 200 字）更适合喂给模型
 */
class DoubaoSearchApi {

    suspend fun search(apiKey: String, query: String, count: Int = 6): SearchOutcome {
        if (apiKey.isBlank()) return SearchOutcome.Err("还没有填豆包搜索的 API Key")
        val q = query.trim().take(MAX_QUERY)
        if (q.isEmpty()) return SearchOutcome.Err("搜索词为空")

        val payload = JSONObject().apply {
            put("Query", q)
            put("SearchType", "web")
            put("Count", count.coerceIn(1, 50))
            put("NeedSummary", true)
        }.toString()

        val res = Http.postJson(
            url = ENDPOINT,
            headers = mapOf(
                "Authorization" to "Bearer ${apiKey.trim()}",
                "Accept" to "application/json",
            ),
            body = payload,
            timeoutMs = 20_000,
        ) ?: return SearchOutcome.Err("搜索请求失败：连不上 $ENDPOINT")

        if (res.code !in 200..299) {
            return SearchOutcome.Err("搜索失败：HTTP ${res.code}")
        }

        val json = runCatching { JSONObject(res.body) }.getOrNull()
            ?: return SearchOutcome.Err("搜索返回内容无法解析")

        // 业务错误藏在 200 的 body 里
        json.optJSONObject("ResponseMetadata")?.optJSONObject("Error")?.let { err ->
            val code = err.optString("Code").ifBlank { err.opt("CodeN")?.toString().orEmpty() }
            val message = err.optString("Message")
            if (code.isNotBlank() || message.isNotBlank()) {
                return SearchOutcome.Err("豆包搜索报错：${listOf(code, message).filter { it.isNotBlank() }.joinToString(" ")}")
            }
        }

        val arr = json.optJSONObject("Result")?.optJSONArray("WebResults")
            ?: return SearchOutcome.Ok(emptyList())

        val sources = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val url = item.optString("Url")
                if (url.isBlank()) continue
                val summary = sequenceOf("Summary", "Content", "Snippet")
                    .map { item.optString(it) }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                add(
                    WebSource(
                        title = item.optString("Title").ifBlank { url },
                        url = url,
                        site = item.optString("SiteName"),
                        summary = summary.replace(Regex("\\s+"), " ").trim(),
                    )
                )
            }
        }
        return SearchOutcome.Ok(sources)
    }

    private companion object {
        const val ENDPOINT = "https://open.feedcoopapi.com/search_api/web_search"
        const val MAX_QUERY = 100
    }
}
