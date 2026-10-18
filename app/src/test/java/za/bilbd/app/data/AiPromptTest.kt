package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 提示词组装。
 *
 * 这些是纯字符串逻辑，却是「AI 答得对不对」的关键：
 * 资料有没有真的塞进提示词、没资料时有没有明确告诉模型别编，
 * 都只能靠这里保证。豆包那边 `Query` 上限 100 字符，超了会被服务端截断。
 */
class AiPromptTest {

    private fun source(i: Int) = WebSource(
        title = "资料$i",
        url = "https://example.com/$i",
        site = "example.com",
        summary = "摘".repeat(50),
    )

    // ---------- 搜索词 ----------

    @Test
    fun query_neverExceedsDoubaoLimit() {
        val cases = listOf(
            Triple("えねみぃ", "敌人", "ピノキオピー"),
            Triple("あ".repeat(80), "中".repeat(60), "P".repeat(50)),
            Triple("短", null, "P"),
        )
        for ((title, cn, producer) in cases) {
            val q = buildSongSearchQuery(title, cn, producer, "初音ミク")
            assertTrue("搜索词超过 100 字符会被豆包截断：${q.length}", q.length <= 100)
        }
    }

    @Test
    fun query_containsTitleAndProducer() {
        val q = buildSongSearchQuery("えねみぃ", "敌人", "ピノキオピー", "初音ミク")
        assertTrue(q.contains("えねみぃ"))
        assertTrue(q.contains("ピノキオピー"))
        assertTrue("中文名不同时应带上", q.contains("敌人"))
    }

    @Test
    fun query_omitsChineseNameWhenIdentical() {
        val q = buildSongSearchQuery("Same", "Same", "P", "V")
        // 「Same Same」这种重复没有检索价值
        assertEquals(1, Regex(Regex.escape("Same")).findAll(q).count())
    }

    @Test
    fun query_handlesBlankInputs() {
        val q = buildSongSearchQuery("", null, "", "")
        assertTrue(q.isNotBlank())
        assertTrue(q.length <= 100)
    }

    // ---------- 单曲解读提示词 ----------

    @Test
    fun songPrompt_includesAllSongFacts() {
        val prompt = buildSongPrompt(
            bvid = "BV1UGa961Ejt",
            title = "えねみぃ",
            titleCn = "敌人",
            producer = "ピノキオピー",
            vocalist = "初音ミク",
            pubtimeSec = 1_700_000_000L,
            extraInfo = listOf("最高排名：#1", "上榜期数：2"),
            sources = listOf(source(1), source(2)),
        )
        assertTrue(prompt.contains("BV1UGa961Ejt"))
        assertTrue(prompt.contains("えねみぃ"))
        assertTrue(prompt.contains("敌人"))
        assertTrue(prompt.contains("ピノキオピー"))
        assertTrue(prompt.contains("初音ミク"))
        assertTrue(prompt.contains("最高排名：#1"))
        assertTrue(prompt.contains("上榜期数：2"))
    }

    @Test
    fun songPrompt_includesEverySource() {
        val sources = (1..6).map { source(it) }
        val prompt = buildSongPrompt(
            bvid = "BV1", title = "T", titleCn = null, producer = "P", vocalist = "V",
            pubtimeSec = 0L, extraInfo = emptyList(), sources = sources,
        )
        for (i in 1..6) {
            assertTrue("第 $i 条资料没进提示词", prompt.contains("资料$i"))
            assertTrue(prompt.contains("https://example.com/$i"))
        }
        // 编号要能让模型引用
        assertTrue(prompt.contains("[1]"))
        assertTrue(prompt.contains("[6]"))
    }

    @Test
    fun songPrompt_withoutSources_tellsModelNotToInvent() {
        val prompt = buildSongPrompt(
            bvid = "BV1", title = "T", titleCn = null, producer = "P", vocalist = "V",
            pubtimeSec = 0L, extraInfo = emptyList(), sources = emptyList(),
        )
        // 没有资料时必须明确交代，否则模型会自己编歌词
        assertTrue("没资料时要提示资料有限", prompt.contains("没有检索到资料"))
        assertFalse(prompt.contains("联网资料】以下内容"))
    }

    @Test
    fun songPrompt_withSources_marksThemAsUnreliable() {
        val prompt = buildSongPrompt(
            bvid = "BV1", title = "T", titleCn = null, producer = "P", vocalist = "V",
            pubtimeSec = 0L, extraInfo = emptyList(), sources = listOf(source(1)),
        )
        assertTrue("要提醒模型检索结果可能不准", prompt.contains("可能不准确"))
    }

    @Test
    fun songPrompt_omitsChineseNameWhenSameAsTitle() {
        val prompt = buildSongPrompt(
            bvid = "BV1", title = "Same", titleCn = "Same", producer = "P", vocalist = "V",
            pubtimeSec = 0L, extraInfo = emptyList(), sources = emptyList(),
        )
        assertFalse(prompt.contains("中文名："))
    }

    @Test
    fun songPrompt_skipsBlankExtraInfo() {
        val prompt = buildSongPrompt(
            bvid = "BV1", title = "T", titleCn = null, producer = "P", vocalist = "V",
            pubtimeSec = 0L, extraInfo = listOf("", "  ", "有效信息"), sources = emptyList(),
        )
        assertTrue(prompt.contains("有效信息"))
        assertFalse("空行不该混进提示词", prompt.contains("\n\n\n"))
    }

    @Test
    fun songSystemPrompt_forbidsFabrication() {
        assertTrue(songSystemPrompt().contains("不要编造"))
    }

    // ---------- 周榜分析提示词 ----------

    @Test
    fun issuePrompt_includesBoardAndIssue() {
        val entry = RankEntry(
            bvid = "BV1", rank = 1, title = "曲名", titleCn = "中文名",
            producers = listOf(Credit("P主", null)), vocalists = listOf(Credit("歌手", null)),
            pubtime = 0L, firstRecordedAt = null,
            views = 1000, likes = 10, coins = 5, favorites = 3, score = 999,
            weeksOnBoard = 2, peakRank = 1, lastRank = 3,
            rankChangeKind = "up", rankChangeDiff = 2, rate = null,
        )
        val issue = Issue(
            boardId = 1, boardName = "周榜", issueId = 122, year = 2026, week = 41,
            videoCount = 1, endDate = 0L, videoBvid = null, entries = listOf(entry),
        )
        val prompt = buildIssuePrompt("周榜", issue)
        assertTrue("应带上期号", prompt.contains("122"))
        assertTrue("应带上榜名", prompt.contains("周榜"))
        assertTrue("应带上曲名", prompt.contains("曲名"))
        assertTrue("应带上中文名", prompt.contains("中文名"))
        assertTrue("应带上 P主", prompt.contains("P主"))
        assertTrue("应带上播放量", prompt.contains("1000"))
        assertTrue("应带上得分", prompt.contains("999"))
        // bvid 刻意不进提示词：模型不需要它，20 首就是 20 段无谓 token
        assertFalse("不该把 BV 号塞进提示词", prompt.contains("BV1"))
    }

    @Test
    fun issueSystemPrompt_isNonBlank() {
        assertTrue(issueSystemPrompt().isNotBlank())
        assertTrue(issueSystemPrompt().contains("简体中文"))
    }
}
