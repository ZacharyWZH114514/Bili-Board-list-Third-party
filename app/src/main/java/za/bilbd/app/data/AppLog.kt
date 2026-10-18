package za.bilbd.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志。
 *
 * 目的只有一个：出问题时用户能把一段**可读的记录**导出来，不必连电脑抓 logcat。
 * 所以刻意保持轻量——内存环形缓冲 + 落一个文本文件，不做分级、不做异步队列、
 * 不引入第三方日志库。几十 KB 的东西没必要上重装备。
 *
 * **绝不记录 API Key**：调用方负责不要把密钥拼进 message。
 * 这里只记「哪个服务商、哪个模型、成功还是失败」，够定位问题，又不泄露凭据。
 */
object AppLog {

    /** 内存里保留多少行；导出时也只会导出这些 */
    private const val MAX_MEMORY_LINES = 600

    /** 文件本身最多留多少行，防止无限增长 */
    private const val MAX_FILE_LINES = 2000

    private const val FILE_NAME = "app.log"

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private var sink: File? = null
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 挂上落盘目录，并把上次运行留下的日志读回来。
     * 这样「闪退 / 被系统杀掉」之后用户还能导出上一次的记录——正是最需要日志的时候。
     */
    @Synchronized
    fun attach(context: Context) {
        if (sink != null) return
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        sink = file
        val previous = runCatching {
            if (file.exists()) {
                file.readLines().filter { it.isNotBlank() }.takeLast(MAX_MEMORY_LINES)
            } else {
                emptyList()
            }
        }.getOrNull().orEmpty()
        if (previous.isNotEmpty()) {
            _lines.value = previous + "—— 以上是上次运行的记录 ——"
        }
    }

    @Synchronized
    fun add(tag: String, message: String) {
        val line = "${timeFormat.format(Date())} [$tag] $message"
        _lines.value = (_lines.value + line).takeLast(MAX_MEMORY_LINES)
        val file = sink ?: return
        runCatching {
            file.appendText(line + "\n")
            // 文件超长就整体裁一次，避免每次写都做统计
            if (file.length() > 256 * 1024) {
                val kept = file.readLines().takeLast(MAX_FILE_LINES)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** 导出用的纯文本 */
    /**
     * 导出用的纯文本。
     *
     * 空日志要返回空串而不是一个换行 —— `joinToString` 的 postfix 对空集合也会追加，
     * 直接写会导出一个只有空行的文件。
     */
    fun exportText(): String {
        val snapshot = _lines.value
        return if (snapshot.isEmpty()) "" else snapshot.joinToString(separator = "\n", postfix = "\n")
    }

    /** 建议的文件名，带时间戳，多次导出不会互相覆盖 */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "vocaloid-list-log-$stamp.txt"
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        runCatching { sink?.writeText("") }
    }

    /** 仅供测试：重置挂载状态与缓冲 */
    @Synchronized
    internal fun resetForTest() {
        _lines.value = emptyList()
        sink = null
    }
}
