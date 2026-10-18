package za.bilbd.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底部标签与页面栈的对应关系。
 *
 * 这里出过一次真 bug：`selectedTab` 原本是手写的 `when`，加「关注」标签时忘了加分支，
 * 于是**在关注页被当成在榜单页** —— 点「榜单」被判成「已经在这一页」，
 * `stack.size == 1` 于是什么都不做，用户看到的就是「点了没反应」。
 * 同时收藏的索引被写成 2（实际是 3），也错位了。
 *
 * 现在改成按 [TAB_ROOTS] 的顺序查表，这组测试把对应关系钉死。
 */
class TabIndexTest {

    @Test
    fun everyTabRootMapsToItsOwnIndex() {
        TAB_ROOTS.forEachIndexed { index, screen ->
            assertEquals(
                "$screen 应该映射到第 $index 个标签",
                index,
                selectedTabIndex(screen),
            )
        }
    }

    @Test
    fun tabRootsAreUnique() {
        assertEquals(
            "同一个页面不能占两个标签位",
            TAB_ROOTS.size,
            TAB_ROOTS.distinct().size,
        )
    }

    @Test
    fun hasFourTabs() {
        // 榜单 / 搜索 / 关注 / 收藏
        assertEquals(4, TAB_ROOTS.size)
    }

    @Test
    fun tabOrderIsHomeSearchFollowingFavorites() {
        // 顺序变了的话，TABS 的图标与文案就会和实际页面错位
        assertEquals(Screen.Home, TAB_ROOTS[0])
        assertEquals(Screen.Search, TAB_ROOTS[1])
        assertEquals(Screen.Following, TAB_ROOTS[2])
        assertEquals(Screen.Favorites, TAB_ROOTS[3])
    }

    @Test
    fun secondaryScreenFallsBackToFirstTab() {
        // 二级页（关于、设置、曲目详情…）不在标签里，应回退到榜单，
        // 不能返回 -1 —— 负数会让底部导航没有任何一项高亮。
        assertEquals(0, selectedTabIndex(Screen.About))
        assertEquals(0, selectedTabIndex(Screen.Settings))
        assertEquals(0, selectedTabIndex(Screen.SongDetail("BV1")))
        assertEquals(0, selectedTabIndex(null))
    }

    @Test
    fun followingAndFavoritesAreNotConfusedWithEachOther() {
        // 这两条正是当初错位的地方，单独再钉一次
        assertEquals(2, selectedTabIndex(Screen.Following))
        assertEquals(3, selectedTabIndex(Screen.Favorites))
    }
}
