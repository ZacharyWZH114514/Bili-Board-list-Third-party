package za.bilbd.app.data

import org.json.JSONArray

/**
 * 「有没有新一期」的判定逻辑。
 *
 * 抽成纯函数是为了能单测：这块一旦判错，用户要么收不到提醒，要么每次开都收到
 * 同一条重复通知 —— 两种都很难在真机上第一时间发现。
 */
object NewIssueChecker {

    /** 一期榜单的最小标识，够用来显示通知就够了 */
    data class IssueRef(
        val issueId: Int,
        val year: Int,
        val week: Int,
        /** 统计截止 / 发布日期（拿不到就是 0） */
        val date: Long,
    )

    /**
     * 从 `/api/public/boards/{id}/issues` 的原始 JSON 里取出**期号最大**的那一期。
     *
     * 线上返回的数组不保证有序，所以不能只取第一个。
     */
    fun newest(issuesJson: String): IssueRef? {
        val arr = runCatching { JSONArray(issuesJson) }.getOrNull() ?: return null
        var best: IssueRef? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("issue_id", o.optInt("issueId", 0))
            if (id <= 0) continue
            if (best == null || id > best.issueId) {
                best = IssueRef(
                    issueId = id,
                    year = o.optInt("year", 0),
                    week = o.optInt("week", 0),
                    date = o.optLong("video_pubdate", o.optLong("end_date", 0L)),
                )
            }
        }
        return best
    }

    /**
     * 要不要发通知。
     *
     * [lastNotified] 为 0 表示第一次跑（刚开启提醒）：只记下来、**不通知**，
     * 否则用户一开开关就会收到一条「你看，第 127 期」（其实早就看过了）。
     */
    fun shouldNotify(newest: IssueRef?, lastNotified: Int): Boolean {
        if (newest == null) return false
        if (lastNotified <= 0) return false
        return newest.issueId > lastNotified
    }
}
