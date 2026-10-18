package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 从一群投稿者里挑出「可信的那个」。
 *
 * 这层判断存在的理由：曲库里的 P主 名和 B 站账号名经常毫无关系
 * （`chinozo` ↔ `ChinozoX_X`），名字匹配不上时只能靠**投稿者一致性**判断——
 * 一个账号稳定投稿这位 P主 的多首作品，那个账号就是他的。
 *
 * 判错的代价是挂上搬运号的头像，所以要求**严格多数**，宁可没有。
 */
class ProducerAvatarVoteTest {

    @Test
    fun singleSampleIsEnough() {
        // 只有一首曲子时无从比较，按严格多数（1×2 > 1）采信
        assertEquals(0, BiliEnricher.dominantIndex(listOf(203655966L)))
    }

    @Test
    fun unanimousSamplesWin() {
        assertEquals(0, BiliEnricher.dominantIndex(listOf(11L, 11L, 11L)))
        assertEquals(0, BiliEnricher.dominantIndex(listOf(11L, 11L, 11L, 11L)))
    }

    @Test
    fun strictMajorityWins() {
        // 3 票里有 2 票来自同一账号 —— 多半是官方号，另一首是搬运
        assertEquals(0, BiliEnricher.dominantIndex(listOf(11L, 11L, 22L)))
        assertEquals(1, BiliEnricher.dominantIndex(listOf(22L, 11L, 11L)))
    }

    @Test
    fun tieIsRejected() {
        // 2 对 2 说明无法判断，宁可不显示头像，也不要赌
        assertEquals(-1, BiliEnricher.dominantIndex(listOf(11L, 11L, 22L, 22L)))
        assertEquals(-1, BiliEnricher.dominantIndex(listOf(11L, 22L)))
    }

    @Test
    fun allDistinctIsRejected() {
        // 每首曲子来自不同账号 —— 典型的搬运散摊子
        assertEquals(-1, BiliEnricher.dominantIndex(listOf(11L, 22L, 33L, 44L)))
    }

    @Test
    fun emptyInputIsRejected() {
        assertEquals(-1, BiliEnricher.dominantIndex(emptyList()))
    }

    @Test
    fun unknownOwnerIsIgnored() {
        // mid=0 表示没拿到，不能拿它去投票
        assertEquals(-1, BiliEnricher.dominantIndex(listOf(0L, 0L)))
        assertEquals(1, BiliEnricher.dominantIndex(listOf(0L, 11L)))
    }

    @Test
    fun returnedIndexPointsAtTheDominantOwner() {
        val owners = listOf(22L, 11L, 11L, 11L)
        val idx = BiliEnricher.dominantIndex(owners)
        assertTrue("下标要落在合法范围", idx in owners.indices)
        assertEquals(11L, owners[idx])
    }
    // ---------- 搬运账号黑名单 ----------

    @Test
    fun knownRelayAccountIsBlacklisted() {
        // 2705870 = 「苏维埃冰棺中的伊利亚」，200 万粉的代发/搬运号。
        // 没有官方 B 站号的 P主，作品全由这类账号代发，投票会一致选中它 ——
        // 必须挡住，否则会把搬运号的头像挂到 P主 头上。
        assertTrue(BiliEnricher.RELAY_MIDS.contains(2705870L))
        // 4676168 = 「精神安定剤」，签名自述「视频转载搬运工」，覆盖多个"P主"
        assertTrue(BiliEnricher.RELAY_MIDS.contains(4676168L))
        // 488817090 = 「百合咲奈奈」，用户确认是代发
        assertTrue(BiliEnricher.RELAY_MIDS.contains(488817090L))
    }

    @Test
    fun realProducersAreNotBlacklisted() {
        // Chinozo 官方号（实测 14 首里 12 首由它投稿）绝不能在黑名单里
        assertFalse(BiliEnricher.RELAY_MIDS.contains(1910879962L))
    }
}
