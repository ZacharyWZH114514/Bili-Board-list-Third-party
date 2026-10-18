package za.bilbd.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 关注的 P主（本地持久化，按名字存）。
 *
 * 关注的是**名字原文**，不做别名归一：曲库里的别名是给搜索用的，
 * 用来做关注键会把不同写法混成一个人，反而不好解释「我到底关注了谁」。
 */
class FollowStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _names = MutableStateFlow(load())
    val names: StateFlow<Set<String>> = _names.asStateFlow()

    private fun load(): Set<String> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptySet()
        val arr = JSONArray(raw)
        val out = LinkedHashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        out
    }.getOrDefault(emptySet())

    private fun save(names: Set<String>) {
        val arr = JSONArray()
        names.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun isFollowing(name: String): Boolean = _names.value.contains(name)

    /** @return 操作后是否处于「已关注」 */
    fun toggle(name: String): Boolean {
        val key = name.trim()
        if (key.isEmpty()) return false
        val cur = _names.value
        val next = if (cur.contains(key)) cur - key else cur + key
        _names.value = next
        save(next)
        return next.contains(key)
    }

    fun unfollow(name: String) {
        val cur = _names.value
        if (!cur.contains(name)) return
        val next = cur - name
        _names.value = next
        save(next)
    }

    fun clear() {
        _names.value = emptySet()
        save(emptySet())
    }

    private companion object {
        const val PREFS = "biliboard_follows"
        const val KEY = "producer_names"
    }
}
