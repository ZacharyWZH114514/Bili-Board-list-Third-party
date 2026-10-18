package za.bilbd.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * biliboard.uk 公开 API 客户端。
 *
 * 这里刻意返回**原始 JSON 字符串**：更新流程需要把线上拿到的原始数据原样落盘，
 * 下次启动再交给 Parsers 解析，避免为了持久化再写一套序列化。
 *
 *   /api/public/boards                                        榜单目录
 *   /api/public/boards/{boardId}/issues                        期列表
 *   /api/public/boards/{boardId}/issues/{issueId}/rankings     某期逐曲排名
 *   /api/public/songs?page=&per_page=                          全站曲库（按发布时间从新到旧）
 *   /api/provider/biliimg/bvid/{bvid}                          封面图（302 到 B 站图床）
 */
class BoardApi(private val base: String = BoardSource.BASE) {

    class ApiException(message: String) : IOException(message)

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", BoardSource.UA)
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw ApiException("HTTP $code（$path）")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    suspend fun boardsRaw(): String = get("/api/public/boards")

    suspend fun issuesRaw(boardId: Int): String = get("/api/public/boards/$boardId/issues")

    suspend fun rankingsRaw(boardId: Int, issueId: Int): String =
        get("/api/public/boards/$boardId/issues/$issueId/rankings")

    suspend fun songsPageRaw(page: Int, perPage: Int = 100): String =
        get("/api/public/songs?page=$page&per_page=$perPage")
}
