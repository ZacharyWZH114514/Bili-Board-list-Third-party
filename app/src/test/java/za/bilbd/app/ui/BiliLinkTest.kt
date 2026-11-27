package za.bilbd.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「B站观看」的跳转地址。
 *
 * 以前直接开 https，系统把它交给了浏览器；现在要按 B 站 App 的深链走。
 * 这里只钉住**地址格式**——真正的跳转要靠真机（装没装 B 站、版本认不认协议），
 * 但格式写错的话一定跳不过去，而这类错误肉眼很难发现。
 */
class BiliLinkTest {

    private val bvid = "BV1UGa961Ejt"

    @Test
    fun appUriUsesBilibiliScheme() {
        assertEquals("bilibili://video/$bvid", BiliLink.appUri(bvid))
        assertTrue(BiliLink.appUri(bvid).startsWith("bilibili://video/"))
    }

    @Test
    fun webUriIsTheNormalVideoPage() {
        assertEquals("https://www.bilibili.com/video/$bvid", BiliLink.webUri(bvid))
    }

    @Test
    fun hdEditionIsTriedFirst() {
        // 平板用户装的是 HD 版（tv.danmaku.bilibilihd），它的优先级必须最高，
        // 否则手机版和 HD 同时装着的设备会被手机版抢走
        val list = BiliLink.candidates()
        assertEquals("tv.danmaku.bilibilihd", list.first())
        assertTrue("手机版也要在候选里", list.contains("tv.danmaku.bili"))
        assertTrue("国际版也要在候选里", list.contains("com.bilibili.app.in"))
    }

    @Test
    fun appUriKeepsTheBvIdIntact() {
        // 别搞出 bilibili://video//BV... 这种双斜杠，B 站会解析失败
        val uri = BiliLink.appUri(bvid)
        assertTrue("不该出现双斜杠", !uri.contains("video//"))
        assertTrue("BV 号必须原样带上", uri.endsWith(bvid))
    }
}
