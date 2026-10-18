package za.bilbd.app.data

import org.json.JSONArray
import org.json.JSONObject

/** 导出/导入时的一条收藏 */
data class FavoriteEntry(
    val bvid: String,
    val title: String? = null,
    val titleCn: String? = null,
    val producer: String? = null,
    val vocalist: String? = null,
)

/** 导入结果 */
sealed interface ImportResult {
    data class Ok(val entries: List<FavoriteEntry>) : ImportResult
    data class Err(val message: String) : ImportResult
}

/**
 * 收藏的导出 / 导入格式。
 *
 * 为什么需要它：收藏只存在本机 SharedPreferences 里，换机、重装、改包名都会丢——
 * 改包名那次就真实发生过。有了导出文件，至少能自己备份和迁移。
 *
 * 特意做成**纯函数**（不碰 Context、不碰文件），这样能直接单测格式的兼容性与容错。
 *
 * 格式设计取舍：
 *  · 存 bvid 是为了能精确还原（唯一键）。
 *  · 同时存曲名/P主是为了让人能直接看懂这个文件——只存一串 BV 号的文件，
 *    用户打开只会觉得是一堆乱码。
 *  · 导入时**只认 bvid**，曲名等仅作展示；这样即使曲库更新了也不会导入失败。
 */
object FavoritesCodec {

    /** 当前格式版本。将来改了结构就加 1，导入时据此兼容旧文件。 */
    const val FORMAT_VERSION = 1

    private const val KEY_APP = "app"
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_COUNT = "count"
    private const val KEY_FAVORITES = "favorites"
    private const val APP_TAG = "Vocaloid List"

    fun encode(entries: List<FavoriteEntry>, exportedAtMillis: Long): String {
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("bvid", e.bvid)
            e.title?.let { o.put("title", it) }
            e.titleCn?.let { o.put("titleCn", it) }
            e.producer?.let { o.put("producer", it) }
            e.vocalist?.let { o.put("vocalist", it) }
            arr.put(o)
        }
        return JSONObject().apply {
            put(KEY_APP, APP_TAG)
            put(KEY_VERSION, FORMAT_VERSION)
            put(KEY_EXPORTED_AT, exportedAtMillis)
            put(KEY_COUNT, entries.size)
            put(KEY_FAVORITES, arr)
        }.toString(2)
    }

    /**
     * 解析导出文件。
     *
     * 容错策略：**尽量导入能认出来的部分**，而不是遇到一条坏数据就整个失败。
     * 用户手里的备份文件可能是手改过的、或者来自不同版本，全盘拒绝的体验很差。
     * 但如果是完全认不出来的文件，就明确报错，不要假装成功。
     *
     * 支持两种结构：带 `favorites` 数组的对象（标准导出格式），
     * 以及直接就是一个数组（手写的极简备份，元素可以是对象或纯 bvid 字符串）。
     */
    fun decode(json: String): ImportResult {
        val text = json.trim()
        if (text.isEmpty()) return ImportResult.Err("文件是空的")

        // 先试数组。
        // 顺序很关键：JSONObject(...) 对数组输入会抛异常，
        // 如果先试对象并在失败时直接返回错误，纯数组的备份文件就永远读不进来。
        runCatching { JSONArray(text) }.getOrNull()?.let { return fromArray(it) }

        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return ImportResult.Err("这不是有效的 JSON 文件")

        val version = root.optInt(KEY_VERSION, 0)
        if (version > FORMAT_VERSION) {
            return ImportResult.Err(
                "文件来自更新的版本（v$version），当前只支持到 v$FORMAT_VERSION，请升级应用"
            )
        }

        val arr = root.optJSONArray(KEY_FAVORITES)
            ?: return ImportResult.Err("文件里没有找到收藏列表")
        return fromArray(arr)
    }

    private fun fromArray(arr: JSONArray): ImportResult {
        val out = ArrayList<FavoriteEntry>(arr.length())
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val entry = when (val item = arr.opt(i)) {
                is JSONObject -> fromObject(item)
                is String -> FavoriteEntry(item.trim()) // 也接受 ["BV1xx","BV2xx"] 这种极简写法
                else -> null
            } ?: continue
            if (entry.bvid.isBlank()) continue
            if (seen.add(entry.bvid)) out.add(entry)
        }

        // 一条都解析不出来时要说清楚，而不是静默返回空列表让用户以为导入成功
        if (out.isEmpty() && arr.length() > 0) {
            return ImportResult.Err("文件里有 ${arr.length()} 条记录，但没有一条是可识别的 bvid")
        }
        return ImportResult.Ok(out)
    }

    private fun fromObject(o: JSONObject): FavoriteEntry? {
        val bvid = o.optString("bvid", "").trim()
        if (bvid.isEmpty()) return null
        fun str(key: String): String? =
            o.optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }
        return FavoriteEntry(
            bvid = bvid,
            title = str("title"),
            titleCn = str("titleCn"),
            producer = str("producer"),
            vocalist = str("vocalist"),
        )
    }
}
