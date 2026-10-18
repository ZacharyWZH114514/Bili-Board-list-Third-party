package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 自动更新周期的时间计算。
 *
 * 重点覆盖「每天 18:00」这一档：它和别的档位语义不同——别的档位是
 * 「距上次更新满 N 天」，而它是**按墙上时间**算的（过了今天 18:00 就等明天 18:00），
 * 很容易写成前者。这里把边界钉死。
 */
class RepoStateTest {

    private val day = 24L * 60 * 60 * 1000

    /** 构造「今天 ± 天数」的某个时刻 */
    private fun at(hour: Int, minute: Int = 0, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun daily(lastUpdate: Long) =
        RepoState(lastUpdateAt = lastUpdate, updateIntervalDays = SettingsStore.DAILY_AT_18)

    // ---------- 档位语义 ----------

    @Test
    fun dailyAt18_isAutoUpdate_notManual() {
        val s = daily(at(12))
        assertTrue("每天 18:00 属于自动更新", s.autoUpdateEnabled)
        assertTrue(s.isDailyAt18)
    }

    @Test
    fun manualOnly_isNotAutoUpdate() {
        val s = RepoState(lastUpdateAt = at(12), updateIntervalDays = SettingsStore.MANUAL_ONLY)
        assertFalse(s.autoUpdateEnabled)
        assertFalse(s.isDailyAt18)
        assertFalse("仅手动时永远不该判定为到点", s.isDueAt(at(23)))
        assertEquals(0L, s.nextUpdateAtFrom(at(23)))
    }

    // ---------- 每天 18:00 的到点判定 ----------

    @Test
    fun dailyAt18_notDueBeforeTodayDeadline() {
        val now = at(17)
        val s = daily(lastUpdate = at(12))
        assertFalse("18:00 之前不该触发", s.isDueAt(now))
    }

    @Test
    fun dailyAt18_dueAfterTodayDeadline() {
        val now = at(19)
        val s = daily(lastUpdate = at(12))
        assertTrue("过了 18:00 且当天没更新过，应触发", s.isDueAt(now))
    }

    @Test
    fun dailyAt18_notDueIfAlreadyUpdatedAfterDeadline() {
        val now = at(23)
        val s = daily(lastUpdate = at(18, 30))
        assertFalse("18:00 之后已经更新过，不应重复触发", s.isDueAt(now))
    }

    @Test
    fun dailyAt18_dueNextDayEvenIfUpdatedYesterdayEvening() {
        // 昨天 23:00 更新过，今天 19:00 应该再次到点
        val now = at(19)
        val s = daily(lastUpdate = at(23, dayOffset = -1))
        assertTrue(s.isDueAt(now))
    }

    // ---------- 每天 18:00 的「下次时间」 ----------

    @Test
    fun dailyAt18_nextIsTodayBeforeDeadline() {
        val now = at(9)
        val s = daily(lastUpdate = at(8))
        assertEquals(RepoState.todayAt(18, now), s.nextUpdateAtFrom(now))
    }

    @Test
    fun dailyAt18_nextIsTomorrowAfterDeadline() {
        val now = at(20)
        val s = daily(lastUpdate = at(19))
        assertEquals(
            "过了今天的 18:00，下次就是明天 18:00（不随实际更新时刻漂移）",
            RepoState.todayAt(18, now) + day,
            s.nextUpdateAtFrom(now),
        )
    }

    @Test
    fun dailyAt18_anchorDoesNotDriftWithLastUpdate() {
        // 同样是 20:00 看，无论上次是 18:05 还是 19:55 更新的，下次都该是明天 18:00
        val now = at(20)
        val a = daily(lastUpdate = at(18, 5)).nextUpdateAtFrom(now)
        val b = daily(lastUpdate = at(19, 55)).nextUpdateAtFrom(now)
        assertEquals(a, b)
    }

    // ---------- 普通间隔档位 ----------

    @Test
    fun interval_notDueBeforePeriod() {
        val now = at(12)
        val s = RepoState(lastUpdateAt = now - 2 * day, updateIntervalDays = 3)
        assertFalse(s.isDueAt(now))
        assertEquals(now + day, s.nextUpdateAtFrom(now))
    }

    @Test
    fun interval_dueAtExactPeriod() {
        val now = at(12)
        val s = RepoState(lastUpdateAt = now - 3 * day, updateIntervalDays = 3)
        assertTrue(s.isDueAt(now))
    }

    @Test
    fun interval_dueAfterPeriod() {
        val now = at(12)
        val s = RepoState(lastUpdateAt = now - 10 * day, updateIntervalDays = 3)
        assertTrue(s.isDueAt(now))
    }

    @Test
    fun intervalMs_matchesDays() {
        assertEquals(7 * day, RepoState(updateIntervalDays = 7).updateIntervalMs)
        assertEquals(day, RepoState(updateIntervalDays = SettingsStore.DAILY_AT_18).updateIntervalMs)
        assertEquals(0L, RepoState(updateIntervalDays = SettingsStore.MANUAL_ONLY).updateIntervalMs)
    }

    // ---------- 从未更新过 ----------

    @Test
    fun neverUpdated_isDueAndHasNoNextTime() {
        val s = RepoState(lastUpdateAt = 0, updateIntervalDays = SettingsStore.DEFAULT_DAYS)
        assertTrue("从没更新过就该更新一次", s.isDueAt(at(12)))
        assertEquals(0L, s.nextUpdateAtFrom(at(12)))
    }

    // ---------- 辅助函数 ----------

    @Test
    fun todayAt_ignoresTimeOfDayOfNow() {
        val morning = RepoState.todayAt(18, at(6))
        val evening = RepoState.todayAt(18, at(22))
        assertEquals(morning, evening)
    }

    @Test
    fun lastDeadlineAt_isTodayOncePassed_otherwiseYesterday() {
        val before = at(10)
        val after = at(20)
        assertEquals(RepoState.todayAt(18, before) - day, RepoState.lastDeadlineAt(18, before))
        assertEquals(RepoState.todayAt(18, after), RepoState.lastDeadlineAt(18, after))
    }
}
