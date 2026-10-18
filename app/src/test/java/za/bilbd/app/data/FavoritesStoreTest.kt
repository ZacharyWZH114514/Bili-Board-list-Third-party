package za.bilbd.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 收藏的本地存储。
 *
 * 重点是 [merge]（导入用）：它的语义必须是**只增不减**——
 * 导入是用户拿来救数据的，丢东西比多东西严重得多。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FavoritesStoreTest {

    private lateinit var context: Context
    private lateinit var store: FavoritesStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每个用例从干净状态开始（SharedPreferences 在同一进程内是共享的）
        context.getSharedPreferences("biliboard_favorites", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = FavoritesStore(context)
    }

    // ---------- 基础 ----------

    @Test
    fun startsEmpty() {
        assertTrue(store.ids.value.isEmpty())
    }

    @Test
    fun toggle_addsThenRemoves() {
        assertTrue("第一次点应是收藏", store.toggle("BV1"))
        assertTrue(store.isFavorite("BV1"))
        assertFalse("第二次点应是取消", store.toggle("BV1"))
        assertFalse(store.isFavorite("BV1"))
    }

    @Test
    fun clear_emptiesEverything() {
        store.toggle("BV1")
        store.toggle("BV2")
        store.clear()
        assertTrue(store.ids.value.isEmpty())
    }

    @Test
    fun stateSurvivesRecreation() {
        store.toggle("BV1")
        val reopened = FavoritesStore(context)
        assertEquals(setOf("BV1"), reopened.ids.value)
    }

    // ---------- 导入（merge） ----------

    @Test
    fun merge_intoEmpty_addsAll() {
        assertEquals(3, store.merge(listOf("BV1", "BV2", "BV3")))
        assertEquals(setOf("BV1", "BV2", "BV3"), store.ids.value)
    }

    @Test
    fun merge_countsOnlyNewOnes() {
        store.merge(listOf("BV1", "BV2"))
        assertEquals("只有 BV3 是新的", 1, store.merge(listOf("BV2", "BV3")))
        assertEquals(setOf("BV1", "BV2", "BV3"), store.ids.value)
    }

    @Test
    fun merge_neverRemovesExisting() {
        // 这条是这个方法存在的理由：导入旧备份不能把新收藏冲掉
        store.merge(listOf("BV1", "BV2", "BV3"))
        store.merge(listOf("BV1"))
        assertEquals("已有的收藏不能被导入覆盖掉", setOf("BV1", "BV2", "BV3"), store.ids.value)
    }

    @Test
    fun merge_withNothingNew_reportsZero() {
        store.merge(listOf("BV1"))
        assertEquals(0, store.merge(listOf("BV1", "BV1")))
    }

    @Test
    fun merge_ignoresBlankIds() {
        assertEquals(1, store.merge(listOf("", "   ", "BV1")))
        assertEquals(setOf("BV1"), store.ids.value)
    }

    @Test
    fun merge_persists() {
        store.merge(listOf("BV1", "BV2"))
        val reopened = FavoritesStore(context)
        assertEquals(setOf("BV1", "BV2"), reopened.ids.value)
    }

    @Test
    fun merge_handlesDuplicatesInsideInput() {
        assertEquals(2, store.merge(listOf("BV1", "BV1", "BV2", "BV2")))
    }

    @Test
    fun merge_emptyInput_changesNothing() {
        store.merge(listOf("BV1"))
        assertEquals(0, store.merge(emptyList()))
        assertEquals(setOf("BV1"), store.ids.value)
    }

    @Test
    fun merge_largeBatch() {
        val many = (1..500).map { "BV$it" }
        assertEquals(500, store.merge(many))
        assertEquals(500, FavoritesStore(context).ids.value.size)
    }

    // ---------- 导出内容 ----------

    @Test
    fun exportedJson_reimportsToSameSet() {
        val original = setOf("BV1", "BV2", "BV3")
        store.merge(original)

        val json = FavoritesCodec.encode(
            store.ids.value.map { FavoriteEntry(it) },
            exportedAtMillis = 0L,
        )
        store.clear()
        assertTrue("清空后应是空的", store.ids.value.isEmpty())

        val decoded = FavoritesCodec.decode(json)
        assertTrue(decoded is ImportResult.Ok)
        store.merge((decoded as ImportResult.Ok).entries.map { it.bvid })
        assertEquals("导出再导入应完全还原", original, store.ids.value)
    }
}
