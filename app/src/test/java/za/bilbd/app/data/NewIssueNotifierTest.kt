package za.bilbd.app.data

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 通知本身：渠道建对没有、标题写对没有、图标资源真的能解析吗。
 *
 * 这几样在真机上出问题时表现都很安静 —— 通知根本不出现，或者出现但点不动，
 * 而且 WorkManager 的定时任务要等 6 小时才跑一次，靠真机验太慢。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NewIssueNotifierTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun notify_postsOnItsOwnChannelWithTheIssueNumber() {
        val c = ctx()
        NewIssueNotifier.notify(c, NewIssueChecker.IssueRef(128, 2026, 47, 0))

        val mgr = c.getSystemService(NotificationManager::class.java)
        val posted = mgr.activeNotifications
        assertEquals("应该只有一条通知", 1, posted.size)

        val n = posted[0].notification
        assertEquals(NewIssueNotifier.CHANNEL_ID, n.channelId)
        // 小图标解析不出来时系统会静默丢弃通知，所以这里必须真的对得上
        assertTrue("通知图标没解析出来", n.icon != 0)
        val title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertTrue("标题不对：$title", title.contains("新一期"))
        val text = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue("正文里应该有期号：$text", text.contains("128"))
        assertTrue("正文里应该有周数：$text", text.contains("47"))
    }

    @Test
    fun notify_createsChannelForAndroidO() {
        val c = ctx()
        NewIssueNotifier.ensureChannel(c)
        val mgr = c.getSystemService(NotificationManager::class.java)
        assertNotNull("渠道没建出来", mgr.getNotificationChannel(NewIssueNotifier.CHANNEL_ID))
    }
}
