package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏导出/导入格式。
 *
 * 这是「数据能不能找回来」的保证，所以容错要求比一般解析高：
 * 用户手里的备份可能是手改过的、来自旧版本、或者干脆是别的文件，
 * 各种情况都要给出**能判断的结果**，而不是静默失败。
 */
class FavoritesCodecTest {

    private fun entry(i: Int) = FavoriteEntry(
        bvid = "BV$i",
        title = "曲名$i",
        titleCn = "中文名$i",
        producer = "P主$i",
        vocalist = "歌手$i",
    )

    // ---------- 往返 ----------

    @Test
    fun encodeThenDecode_roundTrips() {
        val entries = (1..5).map { entry(it) }
        val json = FavoritesCodec.encode(entries, exportedAtMillis = 1_700_000_000_000L)
        val result = FavoritesCodec.decode(json)

        assertTrue(result is ImportResult.Ok)
        val decoded = (result as ImportResult.Ok).entries
        assertEquals(5, decoded.size)
        assertEquals(entries.map { it.bvid }, decoded.map { it.bvid })
        assertEquals(entries.map { it.title }, decoded.map { it.title })
        assertEquals(entries.map { it.producer }, decoded.map { it.producer })
    }

    @Test
    fun encodedFile_isHumanReadable() {
        // 带上曲名/P主就是为了这个：文件打开要能看懂
        val json = FavoritesCodec.encode(listOf(entry(1)), 0L)
        assertTrue("应包含曲名", json.contains("曲名1"))
        assertTrue("应包含 P主", json.contains("P主1"))
        assertTrue("应标明来源应用", json.contains("Vocaloid List"))
        assertTrue("应带格式版本", json.contains("version"))
    }

    @Test
    fun emptyList_roundTrips() {
        val json = FavoritesCodec.encode(emptyList(), 0L)
        val result = FavoritesCodec.decode(json)
        assertTrue(result is ImportResult.Ok)
        assertEquals(0, (result as ImportResult.Ok).entries.size)
    }

    // ---------- 容错 ----------

    @Test
    fun decode_rejectsNonJson() {
        val result = FavoritesCodec.decode("这不是 JSON")
        assertTrue(result is ImportResult.Err)
    }

    @Test
    fun decode_rejectsEmptyString() {
        assertTrue(FavoritesCodec.decode("") is ImportResult.Err)
    }

    @Test
    fun decode_rejectsFutureVersion() {
        // 来自更新版本的文件要明确拒绝，而不是假装导入了
        val json = """{"app":"Vocaloid List","version":99,"favorites":[{"bvid":"BV1"}]}"""
        val result = FavoritesCodec.decode(json)
        assertTrue(result is ImportResult.Err)
        assertTrue((result as ImportResult.Err).message.contains("99"))
    }

    @Test
    fun decode_acceptsBareArray() {
        // 也接受最简写法：[{"bvid":"BV1"}, ...]
        val result = FavoritesCodec.decode("""[{"bvid":"BV1"},{"bvid":"BV2"}]""")
        assertTrue(result is ImportResult.Ok)
        assertEquals(listOf("BV1", "BV2"), (result as ImportResult.Ok).entries.map { it.bvid })
    }

    @Test
    fun decode_acceptsStringArray() {
        // 手写的最简备份：["BV1","BV2"]
        val result = FavoritesCodec.decode("""["BV1","BV2"]""")
        assertTrue(result is ImportResult.Ok)
        assertEquals(2, (result as ImportResult.Ok).entries.size)
    }

    @Test
    fun decode_skipsBadEntriesButKeepsGoodOnes() {
        val json = """
            {"version":1,"favorites":[
              {"bvid":"BV1"},
              null,
              42,
              {"title":"没有 bvid"},
              {"bvid":"  "},
              {"bvid":"BV2"}
            ]}
        """.trimIndent()
        val result = FavoritesCodec.decode(json)
        assertTrue(result is ImportResult.Ok)
        assertEquals("坏数据应被跳过，好数据要保留",
            listOf("BV1", "BV2"), (result as ImportResult.Ok).entries.map { it.bvid })
    }

    @Test
    fun decode_deduplicates() {
        val json = """{"version":1,"favorites":[{"bvid":"BV1"},{"bvid":"BV1"},{"bvid":"BV2"}]}"""
        val result = FavoritesCodec.decode(json)
        assertEquals(2, (result as ImportResult.Ok).entries.size)
    }

    @Test
    fun decode_trimsWhitespaceInBvid() {
        val result = FavoritesCodec.decode("""[{"bvid":"  BV1  "}]""")
        assertEquals("BV1", (result as ImportResult.Ok).entries[0].bvid)
    }

    @Test
    fun decode_allEntriesUnusable_reportsErrorNotSuccess() {
        // 全解析不出来时必须报错——静默返回空列表会让用户以为导入成功了
        val result = FavoritesCodec.decode("""{"version":1,"favorites":[{"foo":"bar"},{"x":1}]}""")
        assertTrue(result is ImportResult.Err)
        assertTrue((result as ImportResult.Err).message.contains("2"))
    }

    @Test
    fun decode_objectWithoutFavoritesKey_reportsError() {
        val result = FavoritesCodec.decode("""{"hello":"world"}""")
        assertTrue(result is ImportResult.Err)
    }

    @Test
    fun decode_doesNotCrashOnNullFields() {
        // Android 的 optString 会把 JSON null 读成字符串 "null"，必须挡住
        val json = """[{"bvid":"BV1","title":null,"producer":null}]"""
        val result = FavoritesCodec.decode(json)
        assertTrue(result is ImportResult.Ok)
        val e = (result as ImportResult.Ok).entries[0]
        assertEquals("BV1", e.bvid)
        assertEquals(null, e.title)
        assertEquals(null, e.producer)
    }

    @Test
    fun decode_keepsBvidWhenOtherFieldsMissing() {
        val result = FavoritesCodec.decode("""[{"bvid":"BV1"}]""")
        val e = (result as ImportResult.Ok).entries[0]
        assertNotNull(e.bvid)
        assertEquals(null, e.title)
        assertEquals(null, e.vocalist)
    }

    @Test
    fun decode_largeListDoesNotLoseEntries() {
        val entries = (1..500).map { entry(it) }
        val json = FavoritesCodec.encode(entries, 0L)
        val result = FavoritesCodec.decode(json) as ImportResult.Ok
        assertEquals(500, result.entries.size)
        assertEquals("BV500", result.entries.last().bvid)
    }

    // ---------- 版本 ----------

    @Test
    fun formatVersion_isOne() {
        // 改结构时这个值要跟着动，测试会提醒
        assertEquals(1, FavoritesCodec.FORMAT_VERSION)
    }

    @Test
    fun encode_recordsEntryCount() {
        val json = FavoritesCodec.encode((1..3).map { entry(it) }, 0L)
        assertTrue("文件里应记录条数，便于人工核对", json.contains("\"count\": 3"))
    }
}
