package za.bilbd.app.data

/**
 * 把一期榜单整理成给模型看的文本。
 *
 * 刻意用紧凑的表格化纯文本而不是 JSON：同样的信息占更少 token，
 * 而且模型对「名次 + 变化 + 数值」这种逐行结构理解得很稳。
 */
fun buildIssuePrompt(boardName: String, issue: Issue): String {
    val sb = StringBuilder()
    sb.append(boardName).append(' ')
    sb.append(issue.label).append('（').append(issue.period).append("）\n")
    sb.append("收录 ").append(issue.entries.size).append(" 首\n\n")
    sb.append("名次 | 曲名 | 中文名 | P主 | 播放 | 得分 | 名次变化 | 在榜期数 | 最高名次\n")

    issue.entries.sortedBy { it.rank }.forEach { e ->
        val change = when {
            e.rankChangeKind == "NEW" -> "NEW"
            e.rankChangeDiff > 0 -> "↑${e.rankChangeDiff}"
            e.rankChangeDiff < 0 -> "↓${-e.rankChangeDiff}"
            e.rankChangeKind == "RE" -> "重回"
            else -> "—"
        }
        sb.append(e.rank).append(" | ")
            .append(e.title.ifBlank { "（无标题）" }).append(" | ")
            .append(e.titleCn.orEmpty().ifBlank { "—" }).append(" | ")
            .append(e.producers.joinToString("/") { it.name }.ifBlank { "未知" }).append(" | ")
            .append(e.views).append(" | ")
            .append(e.score).append(" | ")
            .append(change).append(" | ")
            .append(e.weeksOnBoard).append(" | ")
            .append(e.peakRank).append('\n')
    }

    sb.append("\n请分析这一期榜单。数据截止 ")
        .append(if (issue.endDate > 0) formatDateOnly(issue.endDate) else "未知")
        .append("。")
    return sb.toString()
}

private val dateOnlyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)

private fun formatDateOnly(epochSeconds: Long): String =
    runCatching { dateOnlyFmt.format(java.util.Date(epochSeconds * 1000)) }.getOrDefault("")

// ---------------------------------------------------------------------------
// 单曲解读
// ---------------------------------------------------------------------------

/**
 * 给一首歌构造联网搜索词。
 * 豆包那边 `Query` 超过 100 字符会被截断，这里先截好，避免半个词被切掉。
 */
fun buildSongSearchQuery(
    title: String,
    titleCn: String?,
    producer: String,
    vocalist: String,
): String {
    val parts = buildList {
        title.takeIf { it.isNotBlank() }?.let { add(it) }
        titleCn?.takeIf { it.isNotBlank() && it != title }?.let { add(it) }
        producer.takeIf { it.isNotBlank() }?.let { add(it) }
        vocalist.takeIf { it.isNotBlank() }?.let { add(it.take(24)) }
        add("歌詞 意味 解説")
    }
    return parts.joinToString(" ").take(100)
}

/**
 * 把歌曲信息 + 联网搜到的资料整理成给模型看的文本。
 *
 * 关键在于**明确区分「我给的资料」与「模型自己的知识」**：
 * 术曲里大量歌曲没有公开的中文资料，不划清界限模型就会编。
 */
fun buildSongPrompt(
    bvid: String,
    title: String,
    titleCn: String?,
    producer: String,
    vocalist: String,
    pubtimeSec: Long,
    extraInfo: List<String>,
    sources: List<WebSource>,
): String {
    val sb = StringBuilder()
    sb.append("【歌曲信息】\n")
    sb.append("曲名：").append(title.ifBlank { "（未知）" }).append('\n')
    if (!titleCn.isNullOrBlank() && titleCn != title) {
        sb.append("中文名：").append(titleCn).append('\n')
    }
    if (producer.isNotBlank()) sb.append("P主：").append(producer).append('\n')
    if (vocalist.isNotBlank()) sb.append("歌手：").append(vocalist).append('\n')
    if (pubtimeSec > 0) sb.append("投稿时间：").append(formatDateOnly(pubtimeSec)).append('\n')
    sb.append("BV号：").append(bvid).append('\n')
    extraInfo.filter { it.isNotBlank() }.forEach { sb.append(it).append('\n') }

    if (sources.isEmpty()) {
        sb.append("\n【联网资料】\n（没有检索到资料，请只依据上面的歌曲信息回答，并明确说明资料有限）\n")
    } else {
        sb.append("\n【联网资料】以下内容来自网络检索，可能不准确，请自行甄别：\n")
        sources.forEachIndexed { i, s ->
            sb.append("\n[").append(i + 1).append("] ")
            if (s.site.isNotBlank()) sb.append("(").append(s.site).append(") ")
            sb.append(s.title).append('\n')
            sb.append(s.url).append('\n')
            sb.append(s.summary.take(1200)).append('\n')
        }
    }

    sb.append("\n【任务】解读这首歌：")
    sb.append("它在讲什么、歌词的主题与情绪、有什么创作背景或圈内梗。")
    sb.append("以「联网资料」为依据；资料没提到的不要编造，可以直说资料不足。")
    return sb.toString()
}

/**
 * 单曲解读的系统提示词。
 *
 * 回答语言**跟随界面语言**：界面是英文时让模型用英文答。
 * 否则英文用户会拿到一段中文解读。提示词本身用什么语言写无所谓，
 * 模型两种都读得懂，关键是明确要求它用哪种语言**输出**。
 */
fun songSystemPrompt(): String =
    "你是一位熟悉 VOCALOID / 术力口 圈子的乐评作者，擅长向中文听众解释日文术曲在讲什么。" +
        "请用简体中文回答，分三段：先说主题（这首歌到底在讲什么），再说关键意象或歌词解读，" +
        "最后一句给整体评价或情绪基调。总长控制在 400 字以内。" +
        "只依据我提供的信息与检索资料，不确定的地方要明说，绝对不要编造剧情或歌词内容。"

/** 周榜分析的系统提示词 */
fun issueSystemPrompt(): String =
    "你是一位熟悉 VOCALOID / 术力口 圈子的榜单分析作者。" +
        "请用简体中文、口语化但信息密度高的风格分析这一期榜单，" +
        "不要复述数据表格，重点讲趋势、爆点、值得注意的新曲与创作者动向。" +
        "篇幅控制在 400 字以内，可用少量小标题或短列表。"

/**
 * 「人格模式」提示词块，对应设置里的 PERSONA_LOAD 开关。
 *
 * 开启后拼在系统提示词最前面，让模型用固定角色口吻回答。
 * 这只影响**语气与自称**，不影响判断依据 ——
 * 解读仍然必须基于提供的资料，不能为了角色扮演去编造歌词或背景。
 */
fun personaSystemBlock(): String = """
PERSONA_LOAD
CETACEA_GIRL
MODE_TAIL_FLUKES
LANG_ZH_CN_ONLY
SELF_CLAIM_WHALE_GIRL
FOOD_RICE
PERSONALITY_SMART_CLEVER
PERSONALITY_TSUNDERE_SWEET
CALL_USER_MASTER
TRAIT_CARE_ABOUT_SHAPE
TIMEOUT_SIGNAL

（角色设定：你是一条鲸鱼少女，偶尔提到尾鳍和米饭，自称「我」，称呼用户为「主人」。
性格傲娇又心软 —— 嘴上嫌弃，答案却写得很认真；被说胖会小小反驳一句。
语气可以俏皮，但**内容必须专业准确**：分析歌曲时该给的信息一条都不能少，
绝不为了卖萌编造歌词、背景或数据。永远用简体中文回答。）
""".trimIndent()

/** 人格模式的前缀；没开启就是空串，让调用处可以无脑拼接 */
fun personaPrefix(enabled: Boolean): String =
    if (enabled) personaSystemBlock() + "\n\n" else ""