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

/** P主 关注的本地存储 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FollowStoreTest {

    private lateinit var context: Context
    private lateinit var store: FollowStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("biliboard_follows", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = FollowStore(context)
    }

    @Test
    fun startsEmpty() {
        assertTrue(store.names.value.isEmpty())
    }

    @Test
    fun toggle_followsThenUnfollows() {
        assertTrue("第一次点应是关注", store.toggle("ピノキオピー"))
        assertTrue(store.isFollowing("ピノキオピー"))
        assertFalse("第二次点应是取消", store.toggle("ピノキオピー"))
        assertFalse(store.isFollowing("ピノキオピー"))
    }

    @Test
    fun toggle_trimsWhitespace() {
        store.toggle("  DECO*27  ")
        assertTrue("两端空白应被抹掉", store.isFollowing("DECO*27"))
    }

    @Test
    fun toggle_ignoresBlank() {
        assertFalse(store.toggle("   "))
        assertTrue(store.names.value.isEmpty())
    }

    @Test
    fun unfollow_removesOnlyThatOne() {
        store.toggle("A")
        store.toggle("B")
        store.unfollow("A")
        assertEquals(setOf("B"), store.names.value)
    }

    @Test
    fun unfollow_unknownIsNoOp() {
        store.toggle("A")
        store.unfollow("不存在")
        assertEquals(setOf("A"), store.names.value)
    }

    @Test
    fun clear_emptiesEverything() {
        store.toggle("A")
        store.toggle("B")
        store.clear()
        assertTrue(store.names.value.isEmpty())
    }

    @Test
    fun persistsAcrossRecreation() {
        store.toggle("ピノキオピー")
        store.toggle("DECO*27")
        val reopened = FollowStore(context)
        assertEquals(setOf("ピノキオピー", "DECO*27"), reopened.names.value)
    }

    @Test
    fun handlesNamesWithSpecialCharacters() {
        // 名字里有 * 和日文，JSON 存储要能原样还原
        val names = listOf("DECO*27", "ピノキオピー", "ナユタン星人", "sasakure.UK")
        names.forEach { store.toggle(it) }
        assertEquals(names.toSet(), FollowStore(context).names.value)
    }

    @Test
    fun manyFollows() {
        (1..200).forEach { store.toggle("P$it") }
        assertEquals(200, store.names.value.size)
        assertEquals(200, FollowStore(context).names.value.size)
    }
}
