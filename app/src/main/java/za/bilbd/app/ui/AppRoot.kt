package za.bilbd.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import za.bilbd.app.R
import za.bilbd.app.data.AppLog
import za.bilbd.app.data.ImportResult

/** 页面栈 */
sealed interface Screen {
    data object Home : Screen
    data class Issues(val boardId: Int) : Screen
    data class Ranking(val boardId: Int, val issueId: Int) : Screen
    data class SongDetail(val bvid: String) : Screen
    data object Search : Screen
    data object Following : Screen
    data class Producer(val name: String) : Screen
    data object Favorites : Screen
    data object About : Screen
    data object Logs : Screen
    data object AppIcon : Screen
    data object Settings : Screen
    data object UpdateInterval : Screen
    data object AiSettings : Screen
}

// 标签文案走资源：语言在运行时可变，所以存 resId 而不是字符串
private data class Tab(@StringRes val labelRes: Int, val icon: ImageVector, val root: Screen)

/**
 * 导航意图。三种转场走不同的曲线和方向：
 *  - Forward：进场从右侧滑入（强调减速，起步快收尾缓），出场向左收（强调加速）
 *  - Back：方向相反，且出场位移更小，避免「整页飞走」的廉价感
 *  - Tab：不位移，只做淡入 + 轻微放大，切标签页时不会让人误以为进了下一层
 */
private enum class NavKind { Forward, Back, Tab }

private val TABS = listOf(
    Tab(R.string.tab_home, Icons.Filled.Home, Screen.Home),
    Tab(R.string.tab_search, Icons.Filled.Search, Screen.Search),
    Tab(R.string.tab_following, Icons.Filled.Person, Screen.Following),
    Tab(R.string.tab_favorites, Icons.Filled.Favorite, Screen.Favorites),
)

/**
 * 各标签的根页面，**顺序必须与 [TABS] 一致**。
 *
 * 单独抽出来是因为这里踩过坑：以前 `selectedTab` 是一个手写的 `when`，
 * 加「关注」标签时忘了加分支，结果在关注页被当成在榜单页 ——
 * 点「榜单」被判成「已经在这一页」，于是**点了没反应**；
 * 收藏的索引同时错位成 2。改成按顺序查表之后，加标签不可能再漏。
 */
internal val TAB_ROOTS: List<Screen> =
    listOf(Screen.Home, Screen.Search, Screen.Following, Screen.Favorites)

/** 页面栈首项属于哪个标签；认不出来就回退到第一个（榜单） */
internal fun selectedTabIndex(screen: Screen?): Int =
    TAB_ROOTS.indexOfFirst { it == screen }.takeIf { it >= 0 } ?: 0

/**
 * 页面转场。`togetherWith` 把进场和出场合成一条 ContentTransform。
 * 关键是进出场用不同的缓动：进场减速、出场加速，位移才有惯性。
 */
private fun screenTransition(kind: NavKind): ContentTransform = when (kind) {
    NavKind.Forward ->
        (
            slideInHorizontally(
                animationSpec = tween(Motion.SCREEN_ENTER, easing = Motion.EmphasizedDecelerate),
            ) { full -> full / 5 } + fadeIn(tween(240, delayMillis = 80))
            ) togetherWith (
            slideOutHorizontally(
                animationSpec = tween(Motion.SCREEN_EXIT, easing = Motion.EmphasizedAccelerate),
            ) { full -> -full / 9 } + fadeOut(tween(190))
            )

    NavKind.Back ->
        (
            slideInHorizontally(
                animationSpec = tween(Motion.SCREEN_ENTER, easing = Motion.EmphasizedDecelerate),
            ) { full -> -full / 5 } + fadeIn(tween(240, delayMillis = 80))
            ) togetherWith (
            slideOutHorizontally(
                animationSpec = tween(Motion.SCREEN_EXIT, easing = Motion.EmphasizedAccelerate),
            ) { full -> full / 12 } + fadeOut(tween(190))
            )

    NavKind.Tab ->
        (
            fadeIn(tween(300, easing = Motion.StandardDecelerate)) +
                scaleIn(
                    animationSpec = tween(360, easing = Motion.EmphasizedDecelerate),
                    initialScale = 0.965f,
                )
            ) togetherWith fadeOut(tween(170, easing = Motion.StandardAccelerate))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    // 首次安装先走新手引导；看完（或跳过）之后才进主界面。
    // 用「提前返回」而不是弹窗/新页面：引导期间不该存在底部导航和返回栈。
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    var startAtAiSettings by remember { mutableStateOf(false) }
    if (!onboardingDone) {
        OnboardingScreen(
            dark = isDarkGlass(),
            onFinish = { vm.finishOnboarding() },
            onOpenAiSettings = {
                // 引导里点「现在就去填 Key」：结束引导并直接落到 AI 设置页
                startAtAiSettings = true
                vm.finishOnboarding()
            },
        )
        return
    }

    // snackbar 回调里不能用 stringResource，统一走 context.getString
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    // 导航栈放在 ViewModel 里而不是 remember：
    // 改语言会 recreate() 重建 Activity，remember 的东西全没了 ——
    // 那样用户在「设置」里切个语言就会被踢回首页。放 ViewModel 才能存活。
    // 追问状态要 collect，不能只调读取函数 —— 否则状态变了不会重组
    val songChats by vm.songChats.collectAsStateWithLifecycle()
    val issueChats by vm.issueChats.collectAsStateWithLifecycle()
    val askingKeys by vm.asking.collectAsStateWithLifecycle()

    // P主 头像缓存（走 B 站接口，失败就一直是占位）
    val producerAvatars by vm.producerAvatars.collectAsStateWithLifecycle()

    val stack = vm.screenStack

    // 引导里点「现在就去填 Key」：落到 AI 设置页。
    // 必须用 LaunchedEffect 消费掉这个标记 —— 写成裸 if 会在**每次重组**都推一次，
    // 那样用户一旦回到任何标签根页就会被弹去 AI 设置，表现为「点标签没反应」。
    LaunchedEffect(startAtAiSettings) {
        if (startAtAiSettings) {
            stack.add(Screen.AiSettings)
            startAtAiSettings = false
        }
    }
    val current = stack.last()
    val snapshot = state.snapshot
    val dark = isDarkGlass()
    // 当前生效的图标配色（用户可在「关于 → 应用图标」里切换）
    val iconId by AppIconState.currentId.collectAsStateWithLifecycle()

    // 导航方向决定走哪套转场（进场方向、曲线、时长都不同）
    var navKind by remember { mutableStateOf(NavKind.Forward) }
    val push: (Screen) -> Unit = { navKind = NavKind.Forward; stack.add(it) }
    val pop: () -> Unit = {
        if (stack.size > 1) {
            navKind = NavKind.Back
            stack.removeAt(stack.lastIndex)
        }
    }

    // 轻提示：收藏这类操作必须给反馈，否则用户不知道点没点上
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // 导航选中项跟随「当前标签页的根页面」，这样从某个标签页打开二级页时高亮不会跳走
    // 按 TABS 的顺序查表，而不是手写 when —— 手写的那版加标签时漏过分支
    val selectedTab = selectedTabIndex(stack.firstOrNull())

    val title = when (current) {
        is Screen.Home -> ctx.getString(R.string.app_name)
        is Screen.Issues -> snapshot?.board(current.boardId)?.name
            ?: ctx.getString(R.string.title_issues)
        is Screen.Ranking -> ctx.getString(R.string.title_ranking, current.issueId)
        is Screen.SongDetail -> ctx.getString(R.string.title_song_detail)
        is Screen.Search -> ctx.getString(R.string.title_search)
        is Screen.Following -> ctx.getString(R.string.title_following)
        is Screen.Producer -> current.name
        is Screen.Favorites -> ctx.getString(R.string.title_favorites)
        is Screen.About -> ctx.getString(R.string.title_about)
            is Screen.Logs -> "日志"
        is Screen.AppIcon -> ctx.getString(R.string.title_app_icon)
        is Screen.Settings -> ctx.getString(R.string.title_settings)
        is Screen.UpdateInterval -> ctx.getString(R.string.title_update_interval)
        is Screen.AiSettings -> ctx.getString(R.string.title_ai_settings)
    }

    val selectTab: (Int) -> Unit = { index ->
        if (selectedTab == index) {
            // 已经在这个标签页：再点一次应当回到该标签页的根页面。
            // 否则从「设置 / 曲目详情」这类二级页点底部标签会毫无反应，
            // 因为它们的 stack 首项就是当前标签页的根，selectedTab 早就等于 index。
            if (stack.size > 1) {
                navKind = NavKind.Back
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
            }
        } else {
            navKind = NavKind.Tab
            stack.clear()
            stack.add(TABS[index].root)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val window = windowWidthOf(maxWidth)

        // 曲目详情页：背景换成当前歌曲封面的主题色（离开该页时自动淡回默认配色）
        val songCoverUrl = (current as? Screen.SongDetail)?.let { s ->
            snapshot?.song(s.bvid)?.coverUrl ?: vm.repo.lookupHit(s.bvid)?.coverUrl
        }
        val accent = rememberCoverAccent(songCoverUrl)
        GlassBackdrop(dark = dark, accent = accent)

        Row(Modifier.fillMaxSize()) {
            // 平板：左侧导航栏；手机：底部导航栏
            if (window.isWide) {
                GlassNavigationRail(
                    dark = dark,
                    palette = IconPalettes.byId(iconId),
                    selectedIndex = selectedTab,
                    onSelect = selectTab,
                )
            }

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    GlassTopBar(
                        dark = dark,
                        title = title,
                        showBack = stack.size > 1,
                        onBack = pop,
                        showSettings = current !is Screen.Settings,
                        onSettings = { push(Screen.Settings) },
                    )
                },
                bottomBar = {
                    if (!window.isWide) {
                        GlassBottomBar(dark = dark, selectedIndex = selectedTab, onSelect = selectTab)
                    }
                },
                snackbarHost = {
                    SnackbarHost(snackbarHost) { data ->
                        GlassSurface(
                            dark = dark,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
            ) { padding ->
                // 宽屏下内容居中并限制最大宽度，避免一行从屏幕这头拉到那头
                Box(Modifier.fillMaxSize().padding(padding)) {
                  AnimatedContent(
                    targetState = current,
                    transitionSpec = { screenTransition(navKind) },
                    label = "screen",
                    modifier = Modifier.fillMaxSize(),
                  ) { s ->
                    // 每次页面进场给一份级联驱动值：列表项由此错峰出现
                    val intro = rememberIntroProgress()
                    CompositionLocalProvider(LocalIntroProgress provides intro) {
                    // 宽屏下内容居中并限制最大宽度，避免一行从屏幕这头拉到那头
                    val maxContent = when (s) {
                        is Screen.Home -> window.homeMaxWidth
                        is Screen.SongDetail -> window.detailMaxWidth
                        is Screen.About -> window.aboutMaxWidth
            is Screen.Logs -> window.aboutMaxWidth
                        else -> window.listMaxWidth
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .then(if (maxContent != null) Modifier.widthIn(max = maxContent) else Modifier)
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        when (s) {
                            is Screen.Home -> HomeScreen(
                                state = state,
                                dark = dark,
                                window = window,
                                onOpenIssues = { push(Screen.Issues(it)) },
                                onOpenSong = { push(Screen.SongDetail(it)) },
                                onUpdate = vm::updateNow,
                            )

                            is Screen.Issues -> IssuesScreen(
                                board = snapshot?.board(s.boardId),
                                dark = dark,
                                onOpenIssue = { push(Screen.Ranking(s.boardId, it)) },
                            )

                            is Screen.Ranking -> RankingScreen(
                                issue = snapshot?.issue(s.boardId, s.issueId),
                                boardName = snapshot?.board(s.boardId)?.name ?: "榜单",
                                dark = dark,
                                aiState = vm.aiStates.collectAsStateWithLifecycle().value[s.issueId]
                                    ?: AiUiState.Idle,
                                aiReady = vm.aiConfig.collectAsStateWithLifecycle().value.ready,
                                onOpenSong = { push(Screen.SongDetail(it)) },
                                onAnalyze = {
                                    snapshot?.issue(s.boardId, s.issueId)?.let { issue ->
                                        vm.analyzeIssue(issue.boardName, issue)
                                    }
                                },
                                onOpenAiSettings = { push(Screen.AiSettings) },
                                onClearAi = { vm.clearAi(s.issueId) },
                                chat = AiChatState(
                                    enabled = vm.canAsk("issue:${s.issueId}"),
                                    messages = issueChats[s.issueId].orEmpty(),
                                    asking = "issue:${s.issueId}" in askingKeys,
                                    onAsk = { vm.askAboutIssue(s.issueId, it) },
                                    onClear = { vm.clearChat("issue:${s.issueId}") },
                                ),
                            )

                            is Screen.SongDetail -> {
                                // 榜单之外的曲子没有封面/播放量，用 B 站接口补一次
                                val song = snapshot?.song(s.bvid)
                                val hit = if (song == null) vm.repo.lookupHit(s.bvid) else null
                                // snapshot.song() 只含有过上榜记录的曲子；
                                // 每首曲子都拉一次 B 站数据（有内存+磁盘缓存，重复进页面不会再请求）。
                                // 上榜曲以前被跳过，导致详情页看不到 B站播放/点赞/投币/收藏、UP主 与投稿时间 ——
                                // 曲库有封面和排名，不代表 B 站的那些数值就没必要显示。
                                LaunchedEffect(s.bvid) { vm.ensureBili(s.bvid) }
                                val bili = vm.biliVideos.collectAsStateWithLifecycle().value[s.bvid]
                                val insight = vm.insights.collectAsStateWithLifecycle().value[s.bvid]
                                    ?: AiUiState.Idle
                                val aiReady = vm.aiConfig.collectAsStateWithLifecycle().value.ready
                                SongDetailScreen(
                                    bvid = s.bvid,
                                    song = song,
                                    fallbackHit = hit,
                                    isFavorite = vm.isFavorite(s.bvid),
                                    dark = dark,
                                    window = window,
                                    bili = bili,
                                    insight = insight,
                                    insightReady = aiReady,
                                    onExplain = {
                                        // 曲库、曲库命中、B 站三个来源依次兜底，保证信息尽量全
                                        vm.explainSong(
                                            bvid = s.bvid,
                                            title = song?.title ?: hit?.title ?: s.bvid,
                                            titleCn = song?.titleCn ?: hit?.titleCn,
                                            producer = song?.producerText
                                                ?: hit?.producerText.orEmpty(),
                                            vocalist = song?.vocalistText
                                                ?: hit?.vocalistText.orEmpty(),
                                            pubtimeSec = song?.pubtime
                                                ?: (bili?.pubdateMs?.div(1000) ?: 0L),
                                            extraInfo = buildList {
                                                song?.let {
                                                    add("最高排名：#${it.bestRank}")
                                                    add("上榜期数：${it.onBoardCount}")
                                                    add("上榜历史：${it.history.size} 期")
                                                }
                                                // SongHit 只有这些统计字段，没有投稿时间
                                                hit?.let {
                                                    add("上榜次数：${it.chartCount}")
                                                    if (it.bestRank > 0) add("最高排名：#${it.bestRank}")
                                                    if (it.views > 0) add("播放量：${it.views}")
                                                }
                                                bili?.takeIf { it.play > 0 }?.let {
                                                    add("B站播放：${it.play}")
                                                }
                                                if (song == null && hit == null) {
                                                    add("该曲目未上过周榜")
                                                }
                                            },
                                        )
                                    },
                                    onClearInsight = { vm.clearInsight(s.bvid) },
                                    onOpenAiSettings = { push(Screen.AiSettings) },
                                    onOpenProducer = { push(Screen.Producer(it)) },
                                    chat = AiChatState(
                                        enabled = vm.canAsk("song:${s.bvid}"),
                                        messages = songChats[s.bvid].orEmpty(),
                                        asking = "song:${s.bvid}" in askingKeys,
                                        onAsk = { vm.askAboutSong(s.bvid, it) },
                                        onClear = { vm.clearChat("song:${s.bvid}") },
                                    ),
                                    onToggleFavorite = {
                                        val adding = !vm.isFavorite(s.bvid)
                                        vm.toggleFavorite(s.bvid)
                                        toast(ctx.getString(if (adding) R.string.toast_favorited else R.string.toast_unfavorited))
                                    },
                                )
                            }

                            is Screen.Search -> SearchScreen(
                                vm = vm,
                                dark = dark,
                                onOpenSong = { push(Screen.SongDetail(it)) },
                            )

                            is Screen.Following -> {
                                // 关注页全部依赖本地曲库，进来确保加载过
                                LaunchedEffect(Unit) { vm.ensureCatalogLoaded() }
                                val names = vm.followingNames.collectAsStateWithLifecycle().value
                                val catalogRef = state.catalog
                                val feed = remember(names, catalogRef) { vm.followingFeed() }
                                val recs = remember(catalogRef) { vm.topProducers() }
                                FollowingScreen(
                                    names = names,
                                    feed = feed,
                                    songCountOf = { vm.producerSongs(it).size },
                                    recommendations = recs,
                                    catalogLoading = state.catalogLoading,
                                    dark = dark,
                                    onOpenProducer = { push(Screen.Producer(it)) },
                                    onOpenSong = { push(Screen.SongDetail(it)) },
                                    onUnfollow = { vm.unfollow(it) },
                                )
                            }

                            is Screen.Producer -> {
                                // 注意用 AnimatedContent 的 lambda 参数 s（已智能转型），
                                // 外层那个 current 在 lambda 里不会智能转型
                                val producerName = s.name
                                LaunchedEffect(producerName) { vm.ensureCatalogLoaded() }
                                val catalogRef = state.catalog
                                val names = vm.followingNames.collectAsStateWithLifecycle().value
                                val songs = remember(producerName, catalogRef) {
                                    vm.producerSongs(producerName)
                                }
                                ProducerScreen(
                                    name = producerName,
                                    songs = songs,
                                    isFollowing = names.contains(producerName),
                                    catalogLoading = state.catalogLoading,
                                    avatarUrl = producerAvatars[producerName],
                                    onLoadAvatar = {
                                        // 传多首：头像靠「投稿者一致性」判断，名字对不上也能认出来
                                        //（曲库显示 chinozo，B 站账号是 ChinozoX_X）。上榜曲优先。
                                        vm.loadProducerAvatar(
                                            producerName,
                                            songs.filter { it.bvid.isNotBlank() }
                                                .sortedByDescending { it.chartCount }
                                                .map { it.bvid },
                                        )
                                    },
                                    dark = dark,
                                    onToggleFollow = {
                                        val nowFollowing = vm.toggleFollow(producerName)
                                        toast(
                                            if (nowFollowing) ctx.getString(R.string.toast_following, producerName)
                                            else ctx.getString(R.string.toast_unfollowed, producerName)
                                        )
                                    },
                                    onOpenSong = { push(Screen.SongDetail(it)) },
                                )
                            }

                            is Screen.Favorites -> FavoritesScreen(
                                favoriteIds = vm.favoriteIds.collectAsStateWithLifecycle().value,
                                lookup = { vm.repo.lookupHit(it) },
                                dark = dark,
                                biliVideos = vm.biliVideos.collectAsStateWithLifecycle().value,
                                onNeedBili = vm::ensureBili,
                                onOpenSong = { push(Screen.SongDetail(it)) },
                                onClear = {
                                    vm.clearFavorites()
                                    toast(ctx.getString(R.string.toast_favorites_cleared))
                                },
                                buildExport = vm::exportFavoritesJson,
                                onImportResult = { result ->
                                    when (result) {
                                        is ImportResult.Ok -> {
                                            val added = vm.importFavorites(result.entries)
                                            toast(
                                                if (added == 0) {
                                                    ctx.getString(R.string.toast_import_none, result.entries.size)
                                                } else {
                                                    ctx.getString(R.string.toast_import_ok, added, result.entries.size)
                                                }
                                            )
                                        }

                                        is ImportResult.Err -> toast(result.message)
                                    }
                                },
                                onIoError = { toast(it) },
                            )

                            is Screen.Logs -> LogScreen(
                                                                dark = dark,
                                                                lines = AppLog.lines.collectAsStateWithLifecycle().value,
                                                                onClear = { AppLog.clear() },
                                                            )


                            is Screen.About -> AboutScreen(
                                dark = dark,
                                window = window,
                                onOpenAppIcon = { push(Screen.AppIcon) },
                            )

                            is Screen.AppIcon -> AppIconScreen(dark = dark)

                            is Screen.Settings -> SettingsScreen(
                                dark = dark,
                                state = state,
                                aiConfig = vm.aiConfig.collectAsStateWithLifecycle().value,
                                themeMode = vm.themeMode.collectAsStateWithLifecycle().value,
                                onThemeModeChange = vm::setThemeMode,
                                onReplayOnboarding = vm::replayOnboarding,
                                onOpenAbout = { push(Screen.About) },
                                    onOpenLog = { push(Screen.Logs) },
                                onOpenAppIcon = { push(Screen.AppIcon) },
                                onOpenUpdateInterval = { push(Screen.UpdateInterval) },
                                onOpenAi = { push(Screen.AiSettings) },
                            )

                            is Screen.AiSettings -> AiSettingsScreen(
                                dark = dark,
                                config = vm.aiConfig.collectAsStateWithLifecycle().value,
                                onSave = vm::setAiConfig,
                            )

                            is Screen.UpdateInterval -> UpdateIntervalScreen(
                                dark = dark,
                                state = state,
                                onSelect = vm::setUpdateIntervalDays,
                                onUpdateNow = vm::updateNow,
                            )
                        }
                    }
                    }
                    }
                  }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTopBar(
    dark: Boolean,
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    showSettings: Boolean,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(glassFill(dark))) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            },
            actions = {
                if (showSettings) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        // 下缘一道玻璃高光
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(glassBorderBrush(dark))
        )
    }
}

/** 平板用的侧边导航栏 */
@Composable
private fun GlassNavigationRail(
    dark: Boolean,
    palette: IconPalette,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxHeight().background(glassFill(dark))) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(92.dp),
            containerColor = Color.Transparent,
            header = {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(16.dp))
                AppIconTile(palette = palette, size = 44.dp, corner = 12.dp)
                Spacer(Modifier.height(12.dp))
            },
        ) {
            TABS.forEachIndexed { index, tab ->
                NavigationRailItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                    label = { Text(stringResource(tab.labelRes)) },
                    alwaysShowLabel = true,
                )
            }
        }
        // 右缘一道玻璃高光
        Box(Modifier.fillMaxHeight().width(1.dp).background(glassBorderBrush(dark)))
    }
}

/** 手机用的底部导航栏 */
@Composable
private fun GlassBottomBar(
    dark: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(glassFill(dark))) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(glassBorderBrush(dark))
        )
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            TABS.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                    label = { Text(stringResource(tab.labelRes)) },
                )
            }
        }
    }
}