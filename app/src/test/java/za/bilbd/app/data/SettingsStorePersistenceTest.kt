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
 * 需要 Context 的设置项持久化。
 *
 * 重点是新手引导的「看过没有」这个开关：默认必须是 false（首次安装要看到引导），
 * 而且看过之后重开应用不能又冒出来。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStorePersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("biliboard_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ---------- 新手引导 ----------

    @Test
    fun onboarding_isNotDoneByDefault() {
        // 这条保证新装的应用一定会走到引导页
        assertFalse(SettingsStore(context).onboardingDone.value)
    }

    @Test
    fun onboarding_markingDonePersists() {
        SettingsStore(context).setOnboardingDone(true)
        assertTrue("重开应用后不该又弹引导", SettingsStore(context).onboardingDone.value)
    }

    @Test
    fun onboarding_canBeReplayed() {
        val store = SettingsStore(context)
        store.setOnboardingDone(true)
        store.setOnboardingDone(false)
        assertFalse(store.onboardingDone.value)
        assertFalse(SettingsStore(context).onboardingDone.value)
    }

    @Test
    fun onboarding_isIndependentOfOtherSettings() {
        val store = SettingsStore(context)
        store.setUpdateIntervalDays(SettingsStore.DAILY_AT_18)
        store.setThemeMode(ThemeMode.Dark)
        store.setOnboardingDone(true)

        val reopened = SettingsStore(context)
        assertTrue(reopened.onboardingDone.value)
        assertEquals(SettingsStore.DAILY_AT_18, reopened.updateIntervalDays.value)
        assertEquals(ThemeMode.Dark, reopened.themeMode.value)
    }

    // ---------- 外观 ----------

    @Test
    fun themeMode_isSystemByDefault() {
        assertEquals(ThemeMode.System, SettingsStore(context).themeMode.value)
    }

    @Test
    fun themeMode_persists() {
        SettingsStore(context).setThemeMode(ThemeMode.Light)
        assertEquals(ThemeMode.Light, SettingsStore(context).themeMode.value)
    }

    // ---------- 更新周期 ----------

    @Test
    fun updateInterval_defaultsToThreeDays() {
        assertEquals(SettingsStore.DEFAULT_DAYS, SettingsStore(context).updateIntervalDays.value)
    }

    @Test
    fun updateInterval_persistsDailyAt18() {
        SettingsStore(context).setUpdateIntervalDays(SettingsStore.DAILY_AT_18)
        assertEquals(SettingsStore.DAILY_AT_18, SettingsStore(context).updateIntervalDays.value)
    }

    // ---------- AI 配置 ----------

    @Test
    fun aiConfig_defaultsToEmptyKeys() {
        val ai = SettingsStore(context).ai.value
        assertEquals("", ai.apiKey)
        assertEquals("", ai.doubaoApiKey)
        assertFalse(ai.ready)
        assertFalse(ai.searchReady)
        assertEquals(DeepSeekApi.DEFAULT_BASE_URL, ai.baseUrl)
    }

    @Test
    fun aiConfig_persistsBothKeys() {
        val store = SettingsStore(context)
        store.setAi(
            AiConfig(
                keys = mapOf("custom" to "sk-test"),
                baseUrl = "https://example.com",
                model = "some-model",
                doubaoApiKey = "db-test",
                persona = true,
            )
        )
        val reopened = SettingsStore(context).ai.value
        // baseUrl 认不出是哪个预设 → providerId 是 "custom"，所以 Key 存在 custom 名下
        assertEquals("sk-test", reopened.apiKey)
        assertEquals("db-test", reopened.doubaoApiKey)
        // 人格模式也要存住，否则每次重开都静默回到关闭
        assertTrue(reopened.persona)
        assertEquals("https://example.com", reopened.baseUrl)
        assertEquals("some-model", reopened.model)
        assertTrue(reopened.ready)
        assertTrue(reopened.searchReady)
    }

    @Test
    fun aiConfig_blankBaseUrlFallsBackToDefault() {
        // 用户把接口地址清空后不该留下空字符串，否则请求会拼出坏 URL
        val store = SettingsStore(context)
        store.setAi(AiConfig(keys = mapOf("deepseek" to "k"), baseUrl = "", model = ""))
        val reopened = SettingsStore(context).ai.value
        assertEquals(DeepSeekApi.DEFAULT_BASE_URL, reopened.baseUrl)
        assertEquals(DeepSeekApi.MODELS.first(), reopened.model)
    }

    // ---------- 各服务商的 Key 互相独立 ----------

    @Test
    fun aiKeys_areStoredPerProvider() {
        // 切服务商不该把上一家的 Key 覆盖掉 —— 这是之前的真实缺陷
        val store = SettingsStore(context)
        store.setAi(
            AiConfig(
                keys = mapOf("deepseek" to "sk-deepseek", "openai" to "sk-openai"),
                baseUrl = "https://api.deepseek.com",
                model = "deepseek-chat",
            )
        )
        val cfg = SettingsStore(context).ai.value
        assertEquals("当前是 DeepSeek，应取 DeepSeek 的 Key", "sk-deepseek", cfg.apiKey)
        assertEquals(
            "切到 OpenAI 应取 OpenAI 的 Key",
            "sk-openai",
            cfg.copy(baseUrl = "https://api.openai.com/v1").apiKey,
        )
    }

    @Test
    fun aiKey_followsTheActiveBaseUrl() {
        val store = SettingsStore(context)
        store.setAi(
            AiConfig(
                keys = mapOf(
                    "deepseek" to "d-key",
                    "openai" to "o-key",
                    "qwen" to "q-key",
                    AiProvider.ID_CUSTOM to "c-key",
                ),
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model = "qwen-plus",
            )
        )
        assertEquals("q-key", store.ai.value.apiKey)
        // 自定义地址认不出服务商，就用「自定义」那一把
        val custom = store.ai.value.copy(baseUrl = "http://127.0.0.1:11434/v1")
        assertEquals("c-key", custom.apiKey)
    }

    @Test
    fun aiConfig_readyOnlyWhenActiveProviderHasKey() {
        val cfg = AiConfig(
            keys = mapOf("deepseek" to "d-key"),
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
        )
        // 有 DeepSeek 的 Key，但当前选的是 OpenAI，仍然算没配好
        assertFalse(cfg.ready)
        assertTrue(cfg.copy(baseUrl = "https://api.deepseek.com").ready)
    }

    @Test
    fun legacySingleKeyIsMigratedToTheMatchingProvider() {
        // 老版本只存一把 Key（ai_api_key）。升级后不能要求用户重填。
        val prefs = context.getSharedPreferences("biliboard_settings", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("ai_api_key", "legacy-key")
            .putString("ai_base_url", "https://api.openai.com/v1")
            .apply()
        val store = SettingsStore(context)
        assertEquals("legacy-key", store.ai.value.keyFor("openai"))
    }
}
