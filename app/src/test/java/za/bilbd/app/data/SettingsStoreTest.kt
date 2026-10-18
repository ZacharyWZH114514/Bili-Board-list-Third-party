package za.bilbd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动更新档位的取值与文案。
 *
 * 这里特意钉住 `-1 / 0 / N` 这套约定的行为——用魔法数字表示三种语义本身是个弱点，
 * 至少要有测试保证改动时不会静默改变含义。
 */
class SettingsStoreTest {

    @Test
    fun presets_coverManualDaily18AndIntervals() {
        val presets = SettingsStore.PRESETS
        assertTrue("「仅手动更新」必须在列表里", SettingsStore.MANUAL_ONLY in presets)
        assertTrue("「每天 18:00」必须在列表里", SettingsStore.DAILY_AT_18 in presets)
        assertTrue("默认周期必须在列表里", SettingsStore.DEFAULT_DAYS in presets)
    }

    @Test
    fun presets_haveNoDuplicates() {
        assertEquals(SettingsStore.PRESETS.size, SettingsStore.PRESETS.distinct().size)
    }

    @Test
    fun manualOnly_isZero_andDailyIsNegative() {
        // 约定：0 = 仅手动，负数 = 固定时刻。改动这两个值会破坏 RepoState 的判断
        assertEquals(0, SettingsStore.MANUAL_ONLY)
        assertEquals(-1, SettingsStore.DAILY_AT_18)
    }

    @Test
    fun describe_coversEveryPresetWithDistinctText() {
        val texts = SettingsStore.PRESETS.map { SettingsStore.describe(it) }
        assertEquals("每个档位的文案都该不同", texts.size, texts.distinct().size)
        assertTrue(texts.none { it.isBlank() })
    }

    @Test
    fun describe_specificValues() {
        assertEquals("仅手动更新", SettingsStore.describe(0))
        assertEquals("每天 18:00", SettingsStore.describe(-1))
        assertEquals("每天", SettingsStore.describe(1))
        assertEquals("每 3 天", SettingsStore.describe(3))
        assertEquals("每 30 天", SettingsStore.describe(30))
    }

    @Test
    fun describe_unknownValueStillReadable() {
        // 万一将来写入了一个没预设的值，也不该崩或显示空白
        assertFalse(SettingsStore.describe(99).isBlank())
    }

    // ---------- 外观 ----------

    @Test
    fun themeMode_fromKey_roundTrips() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromKey(mode.name))
        }
    }

    @Test
    fun themeMode_unknownKeyFallsBackToSystem() {
        // 存档里出现认不出来的值（改过枚举名、手改过配置）不能崩
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.System, ThemeMode.fromKey(""))
        assertEquals(ThemeMode.System, ThemeMode.fromKey("Neon"))
    }

    @Test
    fun themeMode_everyEntryHasALabel() {
        val labels = ThemeMode.entries.map { it.label }
        assertTrue(labels.none { it.isBlank() })
        assertEquals("每个档位的文案都该不同", labels.size, labels.distinct().size)
    }

    @Test
    fun themeMode_coversSystemLightDark() {
        // 这三种是产品承诺，少了任何一个都是功能缺失
        assertEquals(3, ThemeMode.entries.size)
        assertTrue(ThemeMode.entries.contains(ThemeMode.System))
        assertTrue(ThemeMode.entries.contains(ThemeMode.Light))
        assertTrue(ThemeMode.entries.contains(ThemeMode.Dark))
    }
}
