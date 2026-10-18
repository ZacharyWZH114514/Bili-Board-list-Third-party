package za.bilbd.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P主 头像的账号核对。
 *
 * 头像取自 B 站 `view` 接口的 `owner.face`（投稿者头像），但**投稿者不一定是 P主 本人**
 * ——搬运号很常见。挂一个陌生人的头像比不挂更糟，所以要先核对名字。
 *
 * 这里全部是纯逻辑，不联网。
 */
class ProducerAccountTest {

    @Test
    fun exactNameMatches() {
        assertTrue(BiliApi.sameAccount("ピノキオピー", "ピノキオピー"))
        assertTrue(BiliApi.sameAccount("DECO*27", "DECO*27"))
    }

    @Test
    fun officialSuffixStillMatches() {
        // 实测：B 站官方号是 ピノキオピー_official
        assertTrue(BiliApi.sameAccount("ピノキオピー", "ピノキオピー_official"))
        assertTrue(BiliApi.sameAccount("ピノキオピー_official", "ピノキオピー"))
    }

    @Test
    fun ignoresCaseAndPunctuation() {
        assertTrue(BiliApi.sameAccount("DECO*27", "deco27"))
        assertTrue(BiliApi.sameAccount("Pinocchio-P", "pinocchio p"))
        assertTrue(BiliApi.sameAccount("　ハチ　", "ハチ"))
    }

    @Test
    fun reprintAccountIsRejected() {
        // 搬运号：名字和 P主 毫无关系
        assertFalse(BiliApi.sameAccount("ピノキオピー", "某人的搬运频道"))
        assertFalse(BiliApi.sameAccount("Ayase", "VOCALOID搬运"))
        assertFalse(BiliApi.sameAccount("sasakure.UK", "音乐搬运工"))
    }

    @Test
    fun realAccountsArePrefixRelated() {
        // 实测的真实账号，全部是「P主 名 + 后缀」的前缀关系
        assertTrue(BiliApi.sameAccount("chinozo", "ChinozoX_X"))
        assertTrue(BiliApi.sameAccount("DECO*27", "DECO27_Official"))
        assertTrue(BiliApi.sameAccount("MIMI", "MIMI_music"))
        assertTrue(BiliApi.sameAccount("ピノキオピー", "ピノキオピー_official"))
        assertTrue(BiliApi.sameAccount("雄之助", "雄之助"))
    }

    @Test
    fun shortNamesAreNotPrefixMatched() {
        // 短于 4 个字符不做前缀判断：「ハチ」会被「ハチミツ」误判
        assertFalse(BiliApi.sameAccount("ハチ", "ハチミツ"))
        assertTrue("全等仍然算同一人", BiliApi.sameAccount("ハチ", "ハチ"))
    }

    @Test
    fun suffixIsNotEnough_onlyPrefixCounts() {
        // 名字只是**包含**对方的片段、但不是前缀 → 不算（搬运号常见这种无关名）
        assertFalse(BiliApi.sameAccount("Pinocchio", "ThePinocchio"))
        assertFalse(BiliApi.sameAccount("KuuRa", "羊小星KuuRaFan"))
    }

    @Test
    fun blankNamesNeverMatch() {
        // 空名字若返回 true，就会把任意投稿者的头像当成 P主 的
        assertFalse(BiliApi.sameAccount("", "ピノキオピー"))
        assertFalse(BiliApi.sameAccount("ピノキオピー", ""))
        assertFalse(BiliApi.sameAccount("", ""))
        assertFalse(BiliApi.sameAccount("   ", "  "))
    }

    @Test
    fun faceUriIsNormalizedToHttps() {
        // 头像和封面一样是协议相对或 http 地址，Android 9+ 会拦明文
        assertTrue(BiliApi.normalizeCover("//i0.hdslb.com/bfs/face/a.jpg").startsWith("https://"))
        assertTrue(BiliApi.normalizeCover("http://i0.hdslb.com/bfs/face/a.jpg").startsWith("https://"))
        assertTrue(BiliApi.normalizeCover("HTTP://i0.hdslb.com/x.jpg").startsWith("https://"))
        assertTrue(BiliApi.normalizeCover("https://i0.hdslb.com/x.jpg").startsWith("https://"))
        assertTrue(BiliApi.normalizeCover(null).isEmpty())
    }
}
