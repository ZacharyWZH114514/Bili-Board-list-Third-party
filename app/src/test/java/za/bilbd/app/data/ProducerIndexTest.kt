package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P主 索引与关注流的纯计算。
 *
 * 两个容易写错的地方，测试专门钉住：
 *  · 一首歌有多位 P主 时，索引里会出现在每个人名下，但**同一个人的名下不能重复**；
 *  · 用户可能同时关注了这几位，合并流必须按 bvid 去重，否则同一首歌出现两三次。
 */
class ProducerIndexTest {

    private fun song(
        bvid: String,
        producers: List<String>,
        pubtime: Long,
        vocalist: String = "初音ミク",
    ) = CatalogSong(
        bvid = bvid,
        title = "曲$bvid",
        titleCn = null,
        producers = producers,
        vocalists = listOf(vocalist),
        pubtime = pubtime,
        chartCount = 0,
        searchText = "",
    )

    private val songs = listOf(
        song("BV1", listOf("ピノキオピー"), pubtime = 100),
        song("BV2", listOf("ピノキオピー"), pubtime = 300),
        song("BV3", listOf("ピノキオピー", "DECO*27"), pubtime = 200),
        song("BV4", listOf("DECO*27"), pubtime = 400),
        song("BV5", listOf("  空白P  "), pubtime = 500),
        song("BV6", listOf("", "  "), pubtime = 600),
    )

    // ---------- 索引 ----------

    @Test
    fun index_groupsByProducer() {
        val index = ProducerIndex.build(songs)
        assertEquals(setOf("ピノキオピー", "DECO*27", "空白P"), index.keys)
    }

    @Test
    fun index_sortsNewestFirst() {
        val index = ProducerIndex.build(songs)
        assertEquals(
            listOf("BV2", "BV3", "BV1"),
            index.getValue("ピノキオピー").map { it.bvid },
        )
    }

    @Test
    fun index_multiProducerSongAppearsUnderEach() {
        val index = ProducerIndex.build(songs)
        assertTrue("BV3 在ピノキオピー名下", index.getValue("ピノキオピー").any { it.bvid == "BV3" })
        assertTrue("BV3 也在 DECO*27 名下", index.getValue("DECO*27").any { it.bvid == "BV3" })
    }

    @Test
    fun index_trimsProducerNames() {
        val index = ProducerIndex.build(songs)
        assertTrue("名字两端空白要抹掉", index.containsKey("空白P"))
        assertTrue(index.getValue("空白P").any { it.bvid == "BV5" })
    }

    @Test
    fun index_ignoresBlankProducerNames() {
        val index = ProducerIndex.build(songs)
        assertTrue("空名字不该建出条目", index.keys.none { it.isBlank() })
    }

    @Test
    fun index_deduplicatesSameProducerOnSameSong() {
        // 数据里出现过同一首里同一个 P主 写两遍
        val dup = listOf(song("BV9", listOf("P", "P"), pubtime = 1))
        val index = ProducerIndex.build(dup)
        assertEquals(1, index.getValue("P").size)
    }

    @Test
    fun index_emptyInput() {
        assertTrue(ProducerIndex.build(emptyList()).isEmpty())
    }

    // ---------- 关注流 ----------

    @Test
    fun feed_mergesMultipleProducersNewestFirst() {
        val index = ProducerIndex.build(songs)
        val feed = ProducerIndex.mergeFeed(index, listOf("ピノキオピー", "DECO*27"))
        // BV4(400)  BV2(300)  BV3(200)  BV1(100)
        assertEquals(listOf("BV4", "BV2", "BV3", "BV1"), feed.map { it.bvid })
    }

    @Test
    fun feed_deduplicatesSharedSongs() {
        // BV3 同时属于两位被关注的 P主，只能出现一次
        val index = ProducerIndex.build(songs)
        val feed = ProducerIndex.mergeFeed(index, listOf("ピノキオピー", "DECO*27"))
        assertEquals(1, feed.count { it.bvid == "BV3" })
    }

    @Test
    fun feed_emptyWhenNoFollows() {
        val index = ProducerIndex.build(songs)
        assertTrue(ProducerIndex.mergeFeed(index, emptyList()).isEmpty())
    }

    @Test
    fun feed_ignoresUnknownProducers() {
        val index = ProducerIndex.build(songs)
        // 关注的两个人里只有后者存在，结果应与只关注他一致
        assertEquals(
            listOf("BV2", "BV3", "BV1"),
            ProducerIndex.mergeFeed(index, listOf("不存在的人", "ピノキオピー")).map { it.bvid },
        )
    }

    @Test
    fun feed_handlesNamesWithWhitespace() {
        val index = ProducerIndex.build(songs)
        assertEquals(
            listOf("BV5"),
            ProducerIndex.mergeFeed(index, listOf("  空白P  ")).map { it.bvid },
        )
    }

    @Test
    fun feed_ignoresBlankFollowNames() {
        val index = ProducerIndex.build(songs)
        assertTrue(ProducerIndex.mergeFeed(index, listOf("", "   ")).isEmpty())
    }

    @Test
    fun feed_singleProducerMatchesItsIndexList() {
        val index = ProducerIndex.build(songs)
        assertEquals(
            index.getValue("ピノキオピー").map { it.bvid },
            ProducerIndex.mergeFeed(index, listOf("ピノキオピー")).map { it.bvid },
        )
    }

    // ---------- 推荐 ----------

    @Test
    fun topProducers_ordersBySongCount() {
        val index = ProducerIndex.build(songs)
        val top = ProducerIndex.topProducers(index)
        assertEquals("ピノキオピー", top.first().first)
        assertEquals(3, top.first().second)
    }

    @Test
    fun topProducers_respectsLimit() {
        val index = ProducerIndex.build(songs)
        assertEquals(2, ProducerIndex.topProducers(index, limit = 2).size)
    }

    @Test
    fun topProducers_isDeterministicOnTies() {
        // 数量相同时顺序必须稳定，否则每次刷新列表都在跳。
        // 这里不去钉死具体谁在前（那取决于字符串码点顺序），只要求「每次都一样」。
        val index = ProducerIndex.build(
            listOf(
                song("A", listOf("乙"), 1),
                song("B", listOf("甲"), 1),
            )
        )
        val first = ProducerIndex.topProducers(index)
        assertEquals("两位都在", 2, first.size)
        assertEquals(setOf("甲", "乙"), first.map { it.first }.toSet())
        repeat(5) { assertEquals("顺序必须稳定", first, ProducerIndex.topProducers(index)) }
    }

    @Test
    fun topProducers_emptyIndex() {
        assertTrue(ProducerIndex.topProducers(emptyMap()).isEmpty())
    }
}
