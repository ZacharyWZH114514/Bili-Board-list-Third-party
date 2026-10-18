package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B 站封面地址归一化。
 *
 * B 站接口返回的 `pic` 经常是 `http://`，而目标 SDK 37 默认禁止明文流量，
 * 不转成 https 封面就会加载失败——之前「封面有时加载不出来」就是这类问题。
 */
class BiliApiTest {

    @Test
    fun httpIsUpgradedToHttps() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/abc.jpg",
            BiliApi.normalizeCover("http://i0.hdslb.com/bfs/archive/abc.jpg"),
        )
    }

    @Test
    fun protocolRelativeGetsHttps() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/abc.jpg",
            BiliApi.normalizeCover("//i0.hdslb.com/bfs/archive/abc.jpg"),
        )
    }

    @Test
    fun httpsIsLeftAlone() {
        val url = "https://i0.hdslb.com/bfs/archive/abc.jpg"
        assertEquals(url, BiliApi.normalizeCover(url))
    }

    @Test
    fun whitespaceIsTrimmed() {
        assertEquals(
            "https://i0.hdslb.com/a.jpg",
            BiliApi.normalizeCover("  https://i0.hdslb.com/a.jpg \n"),
        )
    }

    @Test
    fun blankOrNullBecomesEmpty() {
        assertEquals("", BiliApi.normalizeCover(null))
        assertEquals("", BiliApi.normalizeCover(""))
        assertEquals("", BiliApi.normalizeCover("   "))
    }

    @Test
    fun neverReturnsPlainHttp() {
        // 这条是防回归的关键：任何输入都不能以 http:// 开头返回
        val inputs = listOf(
            "http://a.com/x.jpg",
            "//a.com/x.jpg",
            "https://a.com/x.jpg",
            "HTTP://a.com/x.jpg",
        )
        for (input in inputs) {
            val out = BiliApi.normalizeCover(input)
            assertEquals("输入 $input 不该产出明文 http（大小写不敏感）",
                false, out.startsWith("http://", ignoreCase = true))
        }
    }
}
