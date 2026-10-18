package za.bilbd.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 HTTP 封装。
 *
 * 刻意用 JDK 自带的 HttpURLConnection 而不是 OkHttp：
 * 应用只在 Coil 的传递依赖里间接拿到 OkHttp，能否在编译期引用它取决于依赖的
 * scope 声明，不值得为此冒险；这里只有两次请求，够用。
 */
object Http {

    /** 一次请求的结果：HTTP 状态码 + 响应体 */
    data class Response(val code: Int, val body: String)

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000,
    ): Response? = request("GET", url, headers, null, timeoutMs)

    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Int = 120_000,
    ): Response? = request("POST", url, headers, body, timeoutMs)

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int,
    ): Response? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(s.reader(Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            Response(code, text)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
