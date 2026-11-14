package za.bilbd.app.data

import android.content.Context
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外观模式。
 * 之前只能跟随系统，想晚上用深色、白天用浅色就只能改系统设置。
 */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
    ;

    companion object {
        /** 从持久化的名字还原；认不出来就回退到跟随系统 */
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name == key } ?: System
    }
}

/** 联网搜索走哪条路 */
enum class SearchMode(val key: String, val label: String) {
    /** 不联网：只靠模型自己的知识，解读里会说明资料有限 */
    Off("off", "关闭"),

    /** 豆包搜索接口，结果作为资料塞进提示词（需要单独的豆包 Key） */
    Doubao("doubao", "豆包"),

    /** DeepSeek 自带的 deepseek_web_search：服务端搜、服务端引，只需要模型那把 Key */
    DeepSeek("deepseek", "DeepSeek 内置"),

    ;

    companion object {
        fun fromKey(k: String?): SearchMode? = entries.firstOrNull { it.key == k }
    }
}

/** AI 分析的相关配置 */
data class AiConfig(
    /**
     * 每个服务商各存一把 Key（providerId -> key），**互不覆盖**。
     * 之前只有一把共享的，切到 OpenAI 就得重填，DeepSeek 那把直接丢了。
     */
    val keys: Map<String, String> = emptyMap(),
    val baseUrl: String = DeepSeekApi.DEFAULT_BASE_URL,
    val model: String = DeepSeekApi.MODELS.first(),
    /** 豆包搜索（Custom 版）的 Key，用来联网查歌词与背景资料 */
    val doubaoApiKey: String = "",
    /** 人格模式（PERSONA_LOAD）：开启后系统提示词前会拼上角色设定 */
    val persona: Boolean = false,
    /** 联网搜索走哪条路，见 [SearchMode] */
    val searchMode: SearchMode = SearchMode.DeepSeek,
) {
    /** 当前 Base URL 指向哪个服务商（认不出来就是自定义） */
    val providerId: String get() = AiProviders.of(baseUrl).id

    /** 当前服务商对应的那把 Key */
    val apiKey: String get() = keys[providerId].orEmpty()

    /** 能不能让模型写东西 */
    val ready: Boolean get() = apiKey.isNotBlank()

    /** 能不能联网搜索。没有也能解读，只是少了外部资料 */
    val searchReady: Boolean get() = when (searchMode) {
        SearchMode.Off -> false
        SearchMode.Doubao -> doubaoApiKey.isNotBlank()
        // 复用模型那把 Key，不用额外配置
        SearchMode.DeepSeek -> ready
    }

    /** 取某个服务商的 Key，供设置页切服务商时回填 */
    fun keyFor(id: String): String = keys[id].orEmpty()
}

/**
 * 用户偏好（SharedPreferences）。
 * 自动更新周期、AI 分析的 Key / 模型，都用 StateFlow 暴露，改动后界面立刻能看到。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("biliboard_settings", Context.MODE_PRIVATE)

    private val _updateIntervalDays = MutableStateFlow(
        prefs.getInt(KEY_INTERVAL_DAYS, DEFAULT_DAYS)
    )
    val updateIntervalDays: StateFlow<Int> = _updateIntervalDays.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _newIssueAlert = MutableStateFlow(prefs.getBoolean(KEY_NEW_ISSUE_ALERT, false))

    /** 有新一期榜单时发通知（后台由 WorkManager 定时检查） */
    val newIssueAlert: StateFlow<Boolean> = _newIssueAlert.asStateFlow()

    private val _dateOffsetDays = MutableStateFlow(
        prefs.getInt(KEY_DATE_OFFSET, 0).coerceIn(DATE_OFFSET_MIN, DATE_OFFSET_MAX)
    )

    /** 周榜日期偏移（天），见 [setDateOffsetDays] */
    val dateOffsetDays: StateFlow<Int> = _dateOffsetDays.asStateFlow()

    /** 新手引导是否看过。默认 false —— 首次安装会走到引导页 */
    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _ai = MutableStateFlow(
        AiConfig(
            keys = loadAiKeys(),
            baseUrl = prefs.getString(KEY_AI_BASE, DeepSeekApi.DEFAULT_BASE_URL)
                .orEmpty().ifBlank { DeepSeekApi.DEFAULT_BASE_URL },
            // 老配置里可能是已停用的 deepseek-chat / deepseek-reasoner，读出来就换成新名字
            model = DeepSeekApi.migrateModel(
                prefs.getString(KEY_AI_MODEL, DeepSeekApi.MODELS.first())
                    .orEmpty().ifBlank { DeepSeekApi.MODELS.first() }
            ),
            persona = prefs.getBoolean(KEY_AI_PERSONA, false),
            doubaoApiKey = prefs.getString(KEY_DOUBAO_KEY, "").orEmpty(),
            searchMode = loadSearchMode(),
        )
    )
    val ai: StateFlow<AiConfig> = _ai.asStateFlow()

    /**
     * 联网搜索方式。
     *
     * 没存过时按现状推断：以前只有豆包一条路，所以**填过豆包 Key 的老用户继续用豆包**，
     * 否则默认走 DeepSeek 自带搜索（不需要额外 Key）。
     */
    private fun loadSearchMode(): SearchMode {
        SearchMode.fromKey(prefs.getString(KEY_SEARCH_MODE, null))?.let { return it }
        return if (prefs.getString(KEY_DOUBAO_KEY, "").orEmpty().isNotBlank()) {
            SearchMode.Doubao
        } else {
            SearchMode.DeepSeek
        }
    }


    /**
     * 读出各服务商的 Key。
     * 老版本只存了一把（KEY_AI_KEY），这里做一次迁移：
     * 把它归到当时 Base URL 对应的服务商名下，用户不用重新填。
     */
    private fun loadAiKeys(): Map<String, String> {
        val raw = prefs.getString(KEY_AI_KEYS, null)
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching {
                val o = JSONObject(raw)
                o.keys().asSequence().associateWith { o.optString(it) }
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        val legacy = prefs.getString(KEY_AI_KEY, "").orEmpty()
        if (legacy.isBlank()) return emptyMap()
        val url = prefs.getString(KEY_AI_BASE, DeepSeekApi.DEFAULT_BASE_URL).orEmpty()
        return mapOf(AiProviders.of(url).id to legacy)
    }

    private fun buildKeysJson(keys: Map<String, String>): String {
        val o = JSONObject()
        keys.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun setUpdateIntervalDays(days: Int) {
        prefs.edit().putInt(KEY_INTERVAL_DAYS, days).apply()
        _updateIntervalDays.value = days
    }

    /**
     * 周榜日期偏移（天）。
     *
     * 期列表里的日期用的是网站标的 `video_pubdate`，正常情况下和网站一致；
     * 但个别期如果站点自己也标错、或者用户想按自己的口径看，就用这个手动挪几天。
     * 范围 -7..+7，0 表示不动。
     */
    fun setNewIssueAlert(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEW_ISSUE_ALERT, enabled).apply()
        _newIssueAlert.value = enabled
    }

    fun setDateOffsetDays(days: Int) {
        val v = days.coerceIn(DATE_OFFSET_MIN, DATE_OFFSET_MAX)
        prefs.edit().putInt(KEY_DATE_OFFSET, v).apply()
        _dateOffsetDays.value = v
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
        _onboardingDone.value = done
    }

    fun setAi(config: AiConfig) {
        prefs.edit()
            .putString(KEY_AI_KEYS, buildKeysJson(config.keys))
            .putString(KEY_AI_BASE, config.baseUrl)
            .putString(KEY_AI_MODEL, config.model)
        .putBoolean(KEY_AI_PERSONA, config.persona)
            .putString(KEY_DOUBAO_KEY, config.doubaoApiKey)
            .putString(KEY_SEARCH_MODE, config.searchMode.key)
            .apply()
        _ai.value = config
    }

    companion object {
        /** 默认 3 天 */
        const val DEFAULT_DAYS = 3

        /** 0 表示只在用户手动点「立即更新」时联网 */
        const val MANUAL_ONLY = 0

        /** -1 表示每天固定 18:00（按墙上时间，不是「距上次满 24 小时」） */
        const val DAILY_AT_18 = -1

        /** 可选周期 */
        val PRESETS = listOf(MANUAL_ONLY, DAILY_AT_18, 1, 3, 7, 14, 30)

        /** 周榜日期偏移的可选值（天） */
        val DATE_OFFSET_PRESETS = listOf(-3, -2, -1, 0, 1, 2, 3)
        const val DATE_OFFSET_MIN = -7
        const val DATE_OFFSET_MAX = 7

        private const val KEY_INTERVAL_DAYS = "update_interval_days"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_AI_KEY = "ai_api_key"
        private const val KEY_DATE_OFFSET = "date_offset_days"
        private const val KEY_NEW_ISSUE_ALERT = "new_issue_alert"

        /** 各服务商的 Key 存成一个 JSON 对象（providerId -> key） */
        private const val KEY_AI_KEYS = "ai_api_keys"
        private const val KEY_AI_BASE = "ai_base_url"
        private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_AI_PERSONA = "ai_persona"
        private const val KEY_DOUBAO_KEY = "doubao_api_key"
        private const val KEY_SEARCH_MODE = "ai_search_mode"

        /** 周期的中文描述 */
        fun describe(days: Int): String = when (days) {
            MANUAL_ONLY -> "仅手动更新"
            DAILY_AT_18 -> "每天 18:00"
            1 -> "每天"
            else -> "每 $days 天"
        }
    }
}
