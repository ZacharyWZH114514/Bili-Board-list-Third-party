package za.bilbd.app.data

import android.content.Context
import java.io.File

/**
 * 增量更新结果的本地存储。
 *
 * 内置在 APK 里的是「构建时的基线快照」；这里保存的是此后每次更新拿到的增量：
 *   live_snapshot.json  每个榜最新若干期的原始数据（线上原始字段格式，交给 Parsers 解析）
 *   live_catalog.json   曲库里新出现的曲目（原始字段格式）
 *   lastUpdateAt        上次成功更新的时间戳
 *
 * 解析时把基线与增量合并，所以离线也能看到已更新过的内容。
 */
class LiveStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val snapshotFile = File(appContext.filesDir, SNAPSHOT_FILE)
    private val catalogFile = File(appContext.filesDir, CATALOG_FILE)

    var lastUpdateAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE, value).apply()

    var lastUpdateOk: Boolean
        get() = prefs.getBoolean(KEY_LAST_OK, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_OK, value).apply()

    fun readSnapshot(): String? = readFile(snapshotFile)

    fun readCatalog(): String? = readFile(catalogFile)

    fun writeSnapshot(json: String) = writeFile(snapshotFile, json)

    fun writeCatalog(json: String) = writeFile(catalogFile, json)

    /** 清空增量，回到内置基线 */
    fun clear() {
        runCatching { snapshotFile.delete() }
        runCatching { catalogFile.delete() }
        prefs.edit().remove(KEY_LAST_UPDATE).remove(KEY_LAST_OK).apply()
    }

    fun snapshotBytes(): Long = if (snapshotFile.exists()) snapshotFile.length() else 0L

    private fun readFile(f: File): String? =
        runCatching { if (f.exists()) f.readText(Charsets.UTF_8) else null }.getOrNull()

    private fun writeFile(f: File, text: String) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }
    }

    private companion object {
        const val PREFS = "biliboard_update"
        const val KEY_LAST_UPDATE = "last_update_at"
        const val KEY_LAST_OK = "last_update_ok"
        const val SNAPSHOT_FILE = "live_snapshot.json"
        const val CATALOG_FILE = "live_catalog.json"
    }
}
