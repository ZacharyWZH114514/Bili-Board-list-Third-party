package za.bilbd.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 用 B 站接口补齐榜单之外的信息。
 *
 * 触发场景：打开任意曲目详情页。曲库自己没有 B 站侧的播放/点赞/投币/收藏，
 * 一次 `view` 请求同时拿到封面、播放量与 UP主 信息，结果落盘缓存，之后不再重复请求。
 */
class BiliEnricher(context: Context) {

    private val appContext = context.applicationContext
    private val api = BiliApi()
    private val file = File(appContext.filesDir, "bili_cache.json")

    /** 两次请求之间的最小间隔，避免被 B 站风控 */
    private val gapMs = 260L
    private val maxCacheEntries = 4000

    private val _videos = MutableStateFlow<Map<String, BiliVideo>>(emptyMap())
    val videos: StateFlow<Map<String, BiliVideo>> = _videos.asStateFlow()

    /** 正在请求或已尝试过（失败也记上，避免同一次会话里反复打同一个失败的 bvid） */
    private val attempted = HashSet<String>()
    private val lock = Mutex()
    private var lastRequestAt = 0L
    private var loaded = false

    private suspend fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            loaded = true
            val text = withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) file.readText() else null }.getOrNull()
            }
            if (text.isNullOrBlank()) return
            val map = runCatching {
                val root = JSONObject(text)
                buildMap {
                    root.keys().forEach { key ->
                        val o = root.optJSONObject(key) ?: return@forEach
                        put(
                            key,
                            BiliVideo(
                                bvid = key,
                                title = o.optString("title"),
                                coverUrl = o.optString("pic"),
                                play = o.optLong("play"),
                                like = o.optLong("like"),
                                coin = o.optLong("coin"),
                                favorite = o.optLong("fav"),
                                author = o.optString("author"),
                                face = o.optString("face"),
                                ownerMid = o.optLong("mid"),
                                pubdateMs = o.optLong("pub"),
                                fetchedAt = o.optLong("at"),
                            ),
                        )
                    }
                }
            }.getOrNull() ?: return
            _videos.value = map
            attempted.addAll(map.keys)
        }
    }

    /** 已有缓存则直接返回；否则请求一次（带间隔限速） */
    suspend fun ensure(bvid: String): BiliVideo? {
        if (bvid.isBlank()) return null
        ensureLoaded()
        _videos.value[bvid]?.let { return it }

        lock.withLock {
            _videos.value[bvid]?.let { return it }
            if (!attempted.add(bvid)) return null // 本会话已经试过且失败
            val wait = gapMs - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }

        val video = api.video(bvid) ?: return null
        _videos.value = _videos.value + (bvid to video)
        persist()
        return video
    }

    private suspend fun persist() {
        val snapshot = _videos.value
        // 超出上限时丢掉最早抓的那些
        val trimmed = if (snapshot.size > maxCacheEntries) {
            snapshot.entries.sortedByDescending { it.value.fetchedAt }.take(maxCacheEntries).associate { it.key to it.value }
        } else snapshot
        _videos.value = trimmed
        withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject()
                trimmed.forEach { (key, v) ->
                    root.put(
                        key,
                        JSONObject().apply {
                            put("title", v.title)
                            put("pic", v.coverUrl)
                            put("play", v.play)
                            put("like", v.like)
                            put("coin", v.coin)
                            put("fav", v.favorite)
                            put("author", v.author)
                            put("face", v.face)
                            put("mid", v.ownerMid)
                            put("pub", v.pubdateMs)
                            put("at", v.fetchedAt)
                        },
                    )
                }
                file.writeText(root.toString())
            }
        }
    }

    /**
     * 取 P主 头像。
     *
     * **不能靠名字硬匹配**：曲库里显示的是 `chinozo`，B 站账号却是 `ChinozoX_X` ——
     * 名字毫无关系。所以改用「投稿者一致性」判断：一个账号稳定投稿这位 P主 的多首作品，
     * 那个账号就是他的（名字怎么起都无所谓）；几首曲子分散在不同账号则多半是搬运，
     * 宁可返回 null 也不挂错人。
     *
     * @param bvids 这位 P主 名下的曲子；传得多判断更准（内部最多取 4 首）
     */
    suspend fun producerAvatar(producerName: String, bvids: List<String>): String? {
        val videos = bvids.filter { it.isNotBlank() }.distinct().take(MAX_AVATAR_SAMPLES)
            .mapNotNull { runCatching { ensure(it) }.getOrNull() }
        if (videos.isEmpty()) return null

        // 名字能对上的就采信，这是最确定的情况
        videos.firstOrNull { BiliApi.sameAccount(producerName, it.author) && it.face.isNotBlank() }
            ?.let { return it.face }

        // 否则看有没有一个账号占严格多数
        val idx = dominantIndex(videos.map { it.ownerMid })
        if (idx < 0) return null
        // 但已知的搬运/代发账号不算 —— 它们投稿量稳定，投票会一致选中它们，
        // 把搬运号的头像挂到 P主 头上就完全错了
        if (videos[idx].ownerMid in RELAY_MIDS) return null
        return videos[idx].face.takeIf { it.isNotBlank() }
    }

    companion object {
        /** 头像只取前几首做投票，避免为了一个头像打太多请求 */
        private const val MAX_AVATAR_SAMPLES = 4

        /**
         * 已知的搬运 / 代发账号（mid）——出现就**不显示头像**。
         *
         * 这类账号大量代发不同 P主 的作品，而且投稿稳定，
         * 「投稿者一致性」投票会一致地选中它，结果就是把搬运号的头像
         * 挂到了根本没有官方 B 站号的 P主 头上。
         *
         * 靠用户提供 + 人工维护，因为无法从数据本身可靠地判断谁是搬运。
         */
        internal val RELAY_MIDS = setOf(
            2705870L, // 苏维埃冰棺中的伊利亚（代发/搬运，签名自述「歌词和代发委托都是无偿」）
            4676168L, // 精神安定剤（签名自述「视频转载搬运工」，覆盖多个"P主"）
            488817090L, // 百合咲奈奈（用户确认是代发；实测同时出现在 ワンオポ 与 Chinozo 名下）
        )

        /**
         * 找出占**严格多数**的投稿者下标；没有就返回 -1。
         *
         * 抽出成纯函数是为了能测：这里判断错了会挂上搬运号的头像。
         * 只有一首样本时算严格多数（1×2 > 1），这样单曲 P主 也能有头像。
         */
        internal fun dominantIndex(owners: List<Long>): Int {
            if (owners.isEmpty()) return -1
            // 分母只算「拿到了 mid 的样本」：mid=0 表示没取到，不该稀释投票，
            // 否则「1 个明确 + 1 个未知」会过不了多数，白白丢掉头像
            val known = owners.filter { it != 0L }
            if (known.isEmpty()) return -1
            val counts = known.groupingBy { it }.eachCount()
            val (bestOwner, bestCount) = counts.maxByOrNull { it.value } ?: return -1
            if (bestCount * 2 <= known.size) return -1
            return owners.indexOf(bestOwner)
        }
    }

    /** 缓存条数（设置页展示用） */
    fun cachedCount(): Int = _videos.value.size
}
