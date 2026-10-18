package za.bilbd.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** 收藏（本地持久化，按 bvid 存） */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private fun load(): Set<String> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptySet()
        val arr = JSONArray(raw)
        val out = LinkedHashSet<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        out
    }.getOrDefault(emptySet())

    private fun save(ids: Set<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun isFavorite(bvid: String): Boolean = _ids.value.contains(bvid)

    /** 返回操作后是否已收藏 */
    fun toggle(bvid: String): Boolean {
        val cur = _ids.value
        val next = if (cur.contains(bvid)) cur - bvid else cur + bvid
        _ids.value = next
        save(next)
        return next.contains(bvid)
    }

    fun clear() {
        _ids.value = emptySet()
        save(emptySet())
    }

    /**
     * 并集导入（用于从备份文件恢复）。
     *
     * 特意用并集而不是覆盖：导入时若覆盖，用户先导入旧备份再导入新备份就会丢数据。
     * 并集最多是「多出来」，不会「少下去」。
     *
     * @return 实际新增的数量
     */
    fun merge(ids: Collection<String>): Int {
        val cur = _ids.value
        val next = LinkedHashSet<String>(cur)
        var added = 0
        for (id in ids) {
            if (id.isNotBlank() && next.add(id)) added++
        }
        if (added > 0) {
            _ids.value = next
            save(next)
        }
        return added
    }

    private companion object {
        const val PREFS = "biliboard_favorites"
        const val KEY = "bvid_list"
    }
}
