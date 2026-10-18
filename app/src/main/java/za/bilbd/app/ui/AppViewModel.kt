package za.bilbd.app.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.bilbd.app.data.AiConfig
import za.bilbd.app.data.AiResult
import za.bilbd.app.data.AppLog
import za.bilbd.app.data.BiliEnricher
import za.bilbd.app.data.BiliVideo
import za.bilbd.app.data.BoardRepository
import za.bilbd.app.data.CatalogSong
import za.bilbd.app.data.ChatMessage
import za.bilbd.app.data.DeepSeekApi
import za.bilbd.app.data.DoubaoSearchApi
import za.bilbd.app.data.FavoriteEntry
import za.bilbd.app.data.FavoritesCodec
import za.bilbd.app.data.FavoritesStore
import za.bilbd.app.data.FollowStore
import za.bilbd.app.data.Issue
import za.bilbd.app.data.RepoState
import za.bilbd.app.data.issueSystemPrompt
import za.bilbd.app.data.personaPrefix
import za.bilbd.app.data.songSystemPrompt
import za.bilbd.app.data.SearchOutcome
import za.bilbd.app.data.SettingsStore
import za.bilbd.app.data.SongHit
import za.bilbd.app.data.ThemeMode
import za.bilbd.app.data.WebSource
import za.bilbd.app.data.buildIssuePrompt
import za.bilbd.app.data.buildSongPrompt
import za.bilbd.app.data.buildSongSearchQuery

/** AI 分析某个页面的状态 */
sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Done(val text: String, val model: String) : AiUiState
    data class Failed(val message: String) : AiUiState
}

/**
 * 界面状态。
 * 数据以内置快照为基线做增量更新，周期可在「设置 → 自定义更新时间」里改；
 * 另外用 B 站接口补榜单之外的封面/播放量，用 DeepSeek 做周榜分析（Key 由用户自填）。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = BoardRepository(app)
    private val favoritesStore = FavoritesStore(app)
    private val followStore = FollowStore(app)
    private val bili = BiliEnricher(app)

    init {
        // 把日志挂到 filesDir，闪退后下次启动还能导出上一次的记录
        AppLog.attach(app)
        // 启动也记一笔：日志页不该只有「出错」才有内容，
        // 而且这样用户第一次打开日志页就能看到东西，知道功能是活的
        AppLog.add("启动", "应用启动 · v1.145")
    }
    private val settings = SettingsStore(app)
    private val deepSeek = DeepSeekApi()
    private val doubao = DoubaoSearchApi()


    val state: StateFlow<RepoState> = repo.state
    val favoriteIds: StateFlow<Set<String>> = favoritesStore.ids
    val followingNames: StateFlow<Set<String>> = followStore.names

    /**
     * 导航栈。放在 ViewModel 里是为了在 Activity 重建后仍然保留：
     * 切换语言会 recreate()，如果放在 remember 里，用户切个语言就会被踢回首页。
     */
    val screenStack = mutableStateListOf<Screen>(Screen.Home)
    val biliVideos: StateFlow<Map<String, BiliVideo>> = bili.videos
    val aiConfig: StateFlow<AiConfig> = settings.ai

    // -------- 搜索状态 --------
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _hits = MutableStateFlow<List<SongHit>>(emptyList())
    val hits: StateFlow<List<SongHit>> = _hits.asStateFlow()

    // -------- AI 分析状态（按 issueId 存） --------
    private val _aiStates = MutableStateFlow<Map<Int, AiUiState>>(emptyMap())
    val aiStates: StateFlow<Map<Int, AiUiState>> = _aiStates.asStateFlow()
    private val aiJobs = HashMap<Int, Job>()

    private var searchJob: Job? = null

    init {
        // 串成一条链：先出榜单快照（首屏要靠它），再后台补曲库，最后看要不要联网更新。
        // 之前是三个 launch 各调一次 loadBundled()，虽然互斥锁保证只做一次，但白等了两次。
        viewModelScope.launch {
            repo.loadBundled()
            repo.ensureCatalog()
            // 到周期了才联网；设为「仅手动」或未到周期时这里不会有任何请求
            repo.autoUpdateIfDue()
        }
    }

    /** 手动立即更新（不受自动更新周期限制） */
    fun updateNow() {
        viewModelScope.launch { repo.updateNow() }
    }

    /** 改自动更新周期；0 = 仅手动，-1 = 每天 18:00 */
    fun setUpdateIntervalDays(days: Int) {
        repo.setUpdateIntervalDays(days)
        // 改完立刻按新周期重新判断一次，缩短周期时能马上生效。
        // 「每天 18:00」也是自动更新（值为 -1），不能只判断 > 0。
        if (days != SettingsStore.MANUAL_ONLY) {
            viewModelScope.launch { repo.autoUpdateIfDue() }
        }
    }

    // -------- B 站补数据 --------

    /** 榜单之外的曲子缺少封面/播放量时，用 B 站接口补一次（有内存 + 磁盘缓存） */
    fun ensureBili(bvid: String) {
        if (bvid.isBlank() || biliVideos.value.containsKey(bvid)) return
        viewModelScope.launch { bili.ensure(bvid) }
    }

    // -------- AI 分析 --------

    fun setAiConfig(config: AiConfig) = settings.setAi(config)

    fun aiStateOf(issueId: Int): AiUiState = _aiStates.value[issueId] ?: AiUiState.Idle

    fun analyzeIssue(boardName: String, issue: Issue) {
        if (_aiStates.value[issue.issueId] is AiUiState.Loading) return
        val config = settings.ai.value
        if (!config.ready) {
            _aiStates.value = _aiStates.value +
                (issue.issueId to AiUiState.Failed("还没有填 API Key，请到「设置 → AI 分析」里填写"))
            return
        }
        _aiStates.value = _aiStates.value + (issue.issueId to AiUiState.Loading)
        aiJobs[issue.issueId]?.cancel()
        aiJobs[issue.issueId] = viewModelScope.launch {
            // 兜底：AI 这块涉及网络与 JSON 解析，任何没预料到的异常都应该变成
            // 一条错误提示，而不是直接把应用崩掉。
            val result = runCatching {
                deepSeek.chat(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.model,
                    systemPrompt = personaPrefix(settings.ai.value.persona) + issueSystemPrompt(),
                    userPrompt = buildIssuePrompt(boardName, issue).also {
                        // 缓存下来供追问复用：资料作为 system 消息带着，模型才不会脱离资料答
                        promptCache["issue:${issue.issueId}"] =
                            issueSystemPrompt() + "\n\n" + it
                    },
                )
            }.getOrElse { t ->
                AppLog.add("AI", "请求失败：${t.javaClass.simpleName}: ${t.message?.take(80)}")
                AiResult.Err(
                    "分析出错：${t.javaClass.simpleName}" +
                        t.message?.let { " · $it" }.orEmpty()
                )
            }
            logAiOutcome("周榜分析", result)
            _aiStates.value = _aiStates.value + (
                issue.issueId to when (result) {
                    is AiResult.Ok -> AiUiState.Done(result.text, result.model)
                    is AiResult.Err -> AiUiState.Failed(result.message)
                }
                )
        }
    }

    fun clearAi(issueId: Int) {
        aiJobs.remove(issueId)?.cancel()
        _aiStates.value = _aiStates.value - issueId
    }

    // -------- 单曲解读（联网搜索 + 模型写解读） --------

    private val _insights = MutableStateFlow<Map<String, AiUiState>>(emptyMap())
    val insights: StateFlow<Map<String, AiUiState>> = _insights.asStateFlow()
    private val insightJobs = HashMap<String, Job>()

    fun insightOf(bvid: String): AiUiState = _insights.value[bvid] ?: AiUiState.Idle

    /**
     * 解读某一首歌在讲什么。
     *
     * 流程：先用豆包搜索查这首歌的歌词／背景资料，再把「歌曲信息 + 检索到的资料」
     * 一起交给模型。模型自己并不知道某首术曲的歌词，不先搜就会编——所以检索这一步
     * 不是锦上添花，而是这个功能能不能信的前提。
     *
     * 没配豆包 Key 时仍然会跑，只是少了外部资料，提示词里会明确告诉模型资料有限。
     */
    fun explainSong(
        bvid: String,
        title: String,
        titleCn: String?,
        producer: String,
        vocalist: String,
        pubtimeSec: Long,
        extraInfo: List<String>,
    ) {
        if (_insights.value[bvid] is AiUiState.Loading) return
        val config = settings.ai.value
        if (!config.ready) {
            _insights.value = _insights.value +
                (bvid to AiUiState.Failed("还没有填 DeepSeek API Key，请到「设置 → AI 分析」里填写"))
            return
        }
        _insights.value = _insights.value + (bvid to AiUiState.Loading)
        insightJobs[bvid]?.cancel()
        insightJobs[bvid] = viewModelScope.launch {
            val result = runCatching {
                var searchNote: String? = null

                // 1) 联网取资料；失败不阻断解读，只把原因记下来交给模型说明
                val sources: List<WebSource> = if (config.searchReady) {
                    when (
                        val outcome = doubao.search(
                            apiKey = config.doubaoApiKey,
                            query = buildSongSearchQuery(title, titleCn, producer, vocalist),
                        )
                    ) {
                        is SearchOutcome.Ok -> outcome.sources
                        is SearchOutcome.Err -> {
                            searchNote = outcome.message
                            emptyList()
                        }
                    }
                } else {
                    searchNote = "未配置豆包搜索 Key，本次没有联网资料"
                    emptyList()
                }

                // 2) 让模型基于资料写解读
                val prompt = buildSongPrompt(
                    bvid = bvid,
                    title = title,
                    titleCn = titleCn,
                    producer = producer,
                    vocalist = vocalist,
                    pubtimeSec = pubtimeSec,
                    extraInfo = extraInfo + listOfNotNull(
                        searchNote?.let { "（检索说明：$it）" }
                    ),
                    sources = sources,
                )
                // 检索是否成功、资料有没有进提示词，从这里能直接看出来
                Log.i(
                    TAG_INSIGHT,
                    "bvid=$bvid 检索到 ${sources.size} 条资料" +
                        (searchNote?.let { "（说明：$it）" } ?: "") +
                        "；提示词 ${prompt.length} 字",
                )
                deepSeek.chat(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.model,
                    systemPrompt = personaPrefix(settings.ai.value.persona) + songSystemPrompt(),
                    userPrompt = prompt.also {
                        // 同上：这份提示词里含检索到的资料，追问要复用
                        promptCache["song:$bvid"] = songSystemPrompt() + "\n\n" + it
                    },
                )
            }.getOrElse { t ->
                AppLog.add("AI", "请求失败：${t.javaClass.simpleName}: ${t.message?.take(80)}")
                AiResult.Err(
                    "解读出错：${t.javaClass.simpleName}" +
                        t.message?.let { " · $it" }.orEmpty()
                )
            }
            logAiOutcome("单曲解读", result)
            _insights.value = _insights.value + (
                bvid to when (result) {
                    is AiResult.Ok -> AiUiState.Done(result.text, result.model)
                    is AiResult.Err -> AiUiState.Failed(result.message)
                }
                )
        }
    }

    /**
     * 把 AI 请求结果记进日志。
     *
     * 关键点：401 之类是**正常返回的 Err**，不是异常，所以只把日志挂在 getOrElse 里
     * 会漏掉最常见的一种失败（实机验证时踩到的，日志页一直是空的）。
     */
    private fun logAiOutcome(action: String, result: AiResult) {
        when (result) {
            is AiResult.Ok -> AppLog.add("AI", "$action 成功 · 模型 ${result.model}")
            is AiResult.Err -> AppLog.add("AI", "$action 失败 · ${result.message.take(80)}")
        }
    }

    fun clearInsight(bvid: String) {
        insightJobs.remove(bvid)?.cancel()
        _insights.value = _insights.value - bvid
    }

    // -------- 搜索 --------

    fun onQueryChange(raw: String) {
        _query.value = raw
        searchJob?.cancel()
        val q = raw.trim()
        if (q.isEmpty()) {
            _hits.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            repo.ensureCatalog()
            _hits.value = repo.searchLocal(q)
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun isFavorite(bvid: String): Boolean = favoriteIds.value.contains(bvid)

    fun toggleFavorite(bvid: String) {
        favoritesStore.toggle(bvid)
    }

    // -------- 外观 --------

    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    // -------- 新手引导 --------

    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone

    fun finishOnboarding() = settings.setOnboardingDone(true)

    /** 设置里可以重看引导 */
    fun replayOnboarding() = settings.setOnboardingDone(false)




    // -------- P主 关注 --------

    fun isFollowing(name: String): Boolean = followStore.isFollowing(name)

    /** @return 操作后是否处于「已关注」 */
    fun toggleFollow(name: String): Boolean = followStore.toggle(name)

    fun unfollow(name: String) = followStore.unfollow(name)

    fun clearFollows() = followStore.clear()

    /** 某位 P主 的全部曲目（本地曲库，按投稿时间从新到旧） */
    fun producerSongs(name: String): List<CatalogSong> = repo.producerSongs(name)

    // -------- P主 头像 --------
    // 头像走 B 站接口，靠「投稿者一致性」判断，名字对不上也能认出来（chinozo → ChinozoX_X）。
    // avatarTried 兼作去重与「试过了」标记：失败不重试，免得每次进页面都打一次接口。
    private val _producerAvatars = MutableStateFlow<Map<String, String>>(emptyMap())
    val producerAvatars: StateFlow<Map<String, String>> = _producerAvatars.asStateFlow()
    private val avatarTried = mutableSetOf<String>()

    fun loadProducerAvatar(name: String, bvids: List<String>) {
        if (name.isBlank() || bvids.isEmpty()) return
        if (!avatarTried.add(name)) return
        viewModelScope.launch {
            val url = runCatching { bili.producerAvatar(name, bvids) }.getOrNull()
            if (!url.isNullOrBlank()) {
                _producerAvatars.value = _producerAvatars.value + (name to url)
            }
        }
    }

    /** 已关注 P主 的最新投稿合并流 */
    fun followingFeed(): List<CatalogSong> = repo.followingFeed(followingNames.value)

    /** 曲库里投稿最多的 P主，用于「还没有关注」时给推荐 */
    fun topProducers(limit: Int = 12): List<Pair<String, Int>> = repo.topProducers(limit)

    /** 关注页需要曲库；没有就触发加载 */
    fun ensureCatalogLoaded() {
        viewModelScope.launch { repo.ensureCatalog() }
    }

    fun clearFavorites() {
        favoritesStore.clear()
    }

    // -------- 收藏的导出 / 导入 --------

    /**
     * 生成导出内容。
     * 除了 bvid 还写上曲名与 P主，这样这个文件用户自己打开也能看懂，
     * 而不是一长串 BV 号。
     */
    fun exportFavoritesJson(): String {
        val entries = favoriteIds.value.map { bvid ->
            val hit = repo.lookupHit(bvid)
            FavoriteEntry(
                bvid = bvid,
                title = hit?.title,
                titleCn = hit?.titleCn,
                producer = hit?.producerText,
                vocalist = hit?.vocalistText,
            )
        }
        return FavoritesCodec.encode(entries, System.currentTimeMillis())
    }

    /** 并集导入，返回新增数量 */
    fun importFavorites(entries: List<FavoriteEntry>): Int =
        favoritesStore.merge(entries.map { it.bvid })

    // -------- AI 追问 --------
    //
    // 设计要点：首次分析时把**组装好的提示词缓存下来**，追问时把它当作 system 消息，
    // 后面接往来消息。这样追问基于同一份资料（榜单表格 / 那首歌的检索结果），
    // 而不是让模型脱离资料自由发挥 —— 这正是「依据回答提出问题」的前提。

    /** key = "issue:<期号>" 或 "song:<bvid>"，值是首次分析用的完整提示词 */
    private val promptCache = HashMap<String, String>()

    private val _issueChats = MutableStateFlow<Map<Int, List<ChatMessage>>>(emptyMap())
    private val _songChats = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val _asking = MutableStateFlow<Set<String>>(emptySet())

    // 必须暴露成 StateFlow 让界面 collect —— 只提供读取函数的话，
    // 状态变了 Compose 不会重组，表现就是「发出追问没反应」。这个坑踩过。
    val issueChats: StateFlow<Map<Int, List<ChatMessage>>> = _issueChats.asStateFlow()
    val songChats: StateFlow<Map<String, List<ChatMessage>>> = _songChats.asStateFlow()
    val asking: StateFlow<Set<String>> = _asking.asStateFlow()

    fun issueChat(issueId: Int): List<ChatMessage> = _issueChats.value[issueId].orEmpty()

    fun songChat(bvid: String): List<ChatMessage> = _songChats.value[bvid].orEmpty()

    fun isAsking(key: String): Boolean = key in _asking.value

    /** 能不能追问：得先分析过一次（那时才有资料可依据） */
    fun canAsk(key: String): Boolean = promptCache.containsKey(key)

    fun askAboutIssue(issueId: Int, question: String) =
        askFollowUp("issue:$issueId", question) {
            _issueChats.value = _issueChats.value + (issueId to it)
        }

    fun askAboutSong(bvid: String, question: String) =
        askFollowUp("song:$bvid", question) {
            _songChats.value = _songChats.value + (bvid to it)
        }

    fun clearChat(key: String) {
        when {
            key.startsWith("issue:") ->
                key.removePrefix("issue:").toIntOrNull()?.let { _issueChats.value = _issueChats.value - it }

            key.startsWith("song:") ->
                _songChats.value = _songChats.value - key.removePrefix("song:")
        }
    }

    private fun askFollowUp(key: String, question: String, store: (List<ChatMessage>) -> Unit) {
        val q = question.trim()
        if (q.isEmpty()) return
        if (key in _asking.value) return // 上一条还没回来，避免并发打两次

        val config = settings.ai.value
        // 人格前缀在追问时再拼一次：缓存里存的是不含人格的基础版，
        // 这样用户中途开关人格模式也立刻生效，不会用到旧的缓存
        val systemPrompt = personaPrefix(config.persona) + promptCache[key].orEmpty()
        val history = when {
            key.startsWith("issue:") ->
                issueChat(key.removePrefix("issue:").toIntOrNull() ?: -1)

            else -> songChat(key.removePrefix("song:"))
        }

        if (!config.ready) {
            store(
                history + ChatMessage(
                    ChatMessage.ROLE_ASSISTANT,
                    "还没有填 DeepSeek API Key，请到「设置 → AI 分析」里填写",
                )
            )
            return
        }
        if (systemPrompt == null) {
            store(history + ChatMessage(ChatMessage.ROLE_ASSISTANT, "先点上面的分析按钮，我才有资料可以依据。"))
            return
        }

        val withQuestion = history + ChatMessage(ChatMessage.ROLE_USER, q)
        store(withQuestion)
        _asking.value = _asking.value + key

        viewModelScope.launch {
            val result = runCatching {
                deepSeek.chatWithHistory(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    model = config.model,
                    messages = listOf(ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt)) + withQuestion,
                )
            }.getOrElse { t ->
                AppLog.add("AI", "请求失败：${t.javaClass.simpleName}: ${t.message?.take(80)}")
                AiResult.Err("提问出错：${t.javaClass.simpleName}${t.message?.let { " · $it" }.orEmpty()}")
            }
            val reply = when (result) {
                is AiResult.Ok -> result.text
                is AiResult.Err -> result.message
            }
            logAiOutcome("追问", result)
            store(withQuestion + ChatMessage(ChatMessage.ROLE_ASSISTANT, reply))
            _asking.value = _asking.value - key
        }
    }

    companion object {
        private const val TAG_INSIGHT = "SongInsight"
    }
}
