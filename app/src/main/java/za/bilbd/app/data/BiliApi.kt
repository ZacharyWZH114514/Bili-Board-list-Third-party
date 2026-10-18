package za.bilbd.app.data

import org.json.JSONObject

/** 从 B 站拿到的投稿信息（用来补榜单里没有的播放量与封面） */
data class BiliVideo(
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val play: Long,
    val like: Long,
    val coin: Long,
    val favorite: Long,
    val author: String,
    /** 投稿者头像（协议相对地址已改写成 https） */
    val face: String,
    val ownerMid: Long,
    val pubdateMs: Long,
    val fetchedAt: Long,
)

/**
 * B 站公开接口。
 *
 * `x/web-interface/view?bvid=` 一个接口就能同时拿到播放量（`stat.view`）和封面（`pic`），
 * 所以「补播放量」和「补封面」共用同一份结果，一次请求解决两件事。
 *
 * 两个坑：
 *  - 必须带 User-Agent 与 Referer，否则 B 站会返回 -412 风控错误
 *  - `pic` 返回的是 http:// 链接，Android 9+ 默认禁止明文流量，要改写成 https
 */
class BiliApi {

    suspend fun video(bvid: String): BiliVideo? {
        if (bvid.isBlank()) return null
        val res = Http.get("$ENDPOINT?bvid=$bvid", HEADERS) ?: return null
        if (res.code !in 200..299) return null
        val json = runCatching { JSONObject(res.body) }.getOrNull() ?: return null
        if (json.optInt("code", -1) != 0) return null
        val data = json.optJSONObject("data") ?: return null
        val stat = data.optJSONObject("stat")
        val owner = data.optJSONObject("owner")
        return BiliVideo(
            bvid = data.optString("bvid", bvid),
            title = data.optString("title"),
            coverUrl = normalizeCover(data.optString("pic")),
            play = stat?.optLong("view", 0L) ?: 0L,
            like = stat?.optLong("like", 0L) ?: 0L,
            coin = stat?.optLong("coin", 0L) ?: 0L,
            favorite = stat?.optLong("favorite", 0L) ?: 0L,
            author = owner?.optString("name").orEmpty(),
            face = normalizeCover(owner?.optString("face")),
            ownerMid = owner?.optLong("mid", 0L) ?: 0L,
            pubdateMs = data.optLong("pubdate", 0L) * 1000L,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 取 P主 头像。
     *
     * 只用 `view` 的 `owner.face`（投稿者头像）——它和播放量、封面共用同一个请求，
     * 稳定、无需登录。**不要**改用 `search/type?search_type=bili_user` 按名字搜：
     * 实测该接口不带 cookie 直接返回 412 风控。
     *
     * 投稿者不一定是 P主 本人（搬运号很常见），所以先核对名字，
     * 对不上就返回 null —— 挂一个陌生人的头像比不挂更糟。
     */
    suspend fun producerAvatar(producerName: String, bvid: String): String? {
        val v = video(bvid) ?: return null
        if (!sameAccount(producerName, v.author)) return null
        return v.face.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val ENDPOINT = "https://api.bilibili.com/x/web-interface/view"

        val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36",
            "Referer" to "https://www.bilibili.com/",
            "Accept" to "application/json",
        )

        /** http:// -> https://，并去掉协议相对写法 */
        fun normalizeCover(raw: String?): String {
            val u = raw?.trim().orEmpty()
            if (u.isEmpty()) return ""
            return when {
                u.startsWith("//") -> "https:$u"
                // 大小写不敏感：B 站目前都返回小写，但没理由为此留个坑。
                // 用 substring(7) 而不是 removePrefix("http://")，后者是大小写敏感的
                u.startsWith("http://", ignoreCase = true) -> "https://" + u.substring(7)
                else -> u
            }
        }

        /**
         * 投稿者名与 P主 名是否像同一个账号。
         *
         * 用**前缀关系**判断，因为实测真实账号都长这样：
         * `chinozo` → `ChinozoX_X`、`ピノキオピー` → `ピノキオピー_official`、
         * `DECO*27` → `DECO27_Official`、`MIMI` → `MIMI_music`。
         *
         * 而搬运号的账号名和 P主 名毫无关系（`荻凪裕作` → `精神安定剤`），
         * 所以这个判断同时是个**搬运号筛子**。
         *
         * 短于 [MIN_NAME_LEN] 的名字不做前缀判断：`ハチ` 会被 `ハチミツ` 误判。
         * 已知局限：`Miku` 与 `MikuMikuDance` 这种前缀关系分不开（可接受，
         * 叫 "Miku" 的 P主 基本不存在，而别名情况很常见）。
         */
        fun sameAccount(producer: String, uploader: String): Boolean {
            val a = foldName(producer)
            val b = foldName(uploader)
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            val short = if (a.length <= b.length) a else b
            val long = if (a.length <= b.length) b else a
            if (short.length < MIN_NAME_LEN) return false
            return long.startsWith(short)
        }

        /** 去掉空白、下划线与装饰符并转小写，便于比对 */
        private fun foldName(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

        private const val MIN_NAME_LEN = 4
    }
}
