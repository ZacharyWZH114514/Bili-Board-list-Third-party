# Vocaloid List · BiliBoard Android

**[English](README.md)** | 中文

---
用 [biliboard.uk](https://biliboard.uk)（Biliboard 术力口周榜）的公开术曲数据做的 Android 应用。
Jetpack Compose + 液态玻璃（Liquid Glass）视觉。数据以内置快照为基线，**每 3 天自动增量更新一次**。



---

## Environment Setup / 环境准备

克隆下来**不能直接构建**。仓库里刻意不含两样东西：JDK/Gradle 二进制（`.tools/`，在 `.gitignore` 里）和 `local.properties`（`sdk.dir` 与本机路径绑定）。
Cloning the repo is **not enough to build**. Two things are deliberately excluded: the JDK/Gradle binaries (`.tools/`, listed in `.gitignore`) and `local.properties` (it pins a machine-specific `sdk.dir`).

| 依赖 / Requirement | 版本 / Version | 说明 / Note |
|---|---|---|
| JDK | **21** (Temurin 21.0.12+) | `java -version` 若是 1.8 会直接失败 / A `java -version` of 1.8 fails outright |
| Gradle | **9.8.1** | 低于 9.x 不支持 AGP 9 / Anything below 9.x does not support AGP 9 |
| Android SDK | **compileSdk 37** (`android-37.1`)、**Build-Tools 37.0.0** | 必须同时声明 `compileSdkMinor = 1` / `compileSdkMinor = 1` is required alongside `compileSdk = 37` |
| AGP | 9.4.1 | **AGP 9 起内置 Kotlin**，不能再应用 `org.jetbrains.kotlin.android`，否则报错 / **AGP 9 ships built-in Kotlin** — do *not* apply `org.jetbrains.kotlin.android`, it will fail |
| Kotlin Compose 插件 | 2.4.21 | 只用 `org.jetbrains.kotlin.plugin.compose` / Only `org.jetbrains.kotlin.plugin.compose` is used |

### 步骤 / Steps

1. 安装 JDK 21 与 Gradle 9.8.1。 / Install JDK 21 and Gradle 9.8.1.
2. 在工程**同级**建 `.tools\`，按 `build.ps1` 期望的结构摆放：
   / Create a `.tools/` folder **next to** the project, laid out the way `build.ps1` expects:

   ```
   .tools\jdk21\jdk-21.x.y\bin\java.exe
   .tools\gradle-9.8.1\gradle-9.8.1\bin\gradle.bat
   ```

   或者改 `build.ps1` 里的 `$JdkHome` / `$GradleBin` 指到你自己的安装位置。
   / Or edit `$JdkHome` / `$GradleBin` in `build.ps1` to point at your own installation.
3. 新建 `local.properties`，写入 `sdk.dir=<你的 Android SDK 路径>`。
   / Create `local.properties` containing `sdk.dir=<path to your Android SDK>`.
4. 构建（见下）。 / Build (see below).

### 构建命令 / Build commands

```powershell
# 调试包 / debug APK
powershell -File build.ps1 -Task ":app:assembleDebug"

# 单元测试 / unit tests（219 个，纯 JVM + Robolectric）
powershell -File build.ps1 -Task ":app:testDebugUnitTest"

# 发布包 / release APK —— 先跑测试，测试不过不出包
powershell -File build.ps1 -Task ":app:assembleRelease"
```

`build.ps1` 会把 `JAVA_HOME` / `GRADLE_USER_HOME` / `TMP` / `ANDROID_USER_HOME` 全部指向工程内部，不依赖系统临时目录。
`build.ps1` redirects `JAVA_HOME`, `GRADLE_USER_HOME`, `TMP` and `ANDROID_USER_HOME` into the project, so it never depends on system temp directories.

### 其他脚本 / Other scripts

| 脚本 | 用途 / Purpose |
|---|---|
| `run.ps1` | 启动已装的调试包并等待就绪 / launch the installed debug build and wait until it is up |
| `tap.ps1` | 按文字找控件并点击，用于 UI 自动化 / find a widget by text and tap it — used for UI automation |
## 应用名称与图标

应用名为 **Vocaloid List**（`@string/app_name`，顶栏标题与「关于」页同步）。

「关于 → 应用图标」提供两种换图标方式，分别对应两种能力：

| 方式 | 效果 | 说明 |
|---|---|---|
| **预设配色**（6 套） | 替换**真正的启动器图标** | 清单里为每套配色声明一个 `activity-alias`，运行时用 `PackageManager.setComponentEnabledSetting` 启用目标、禁用其余 |
| **自定义图片** | 在桌面创建**一个新入口**，图标是你选的图 | Android 不开放运行时替换启动图标（`android:icon` 只能是打包进 APK 的资源），所以用 `ShortcutManagerCompat.requestPinShortcut` 固定快捷方式实现；应用本身的图标不变 |

几个实现要点：

- 真正的界面 `MainActivity` 不再带 `LAUNCHER`，启动入口全部交给 alias。
- **切换顺序必须是「先启用目标，再禁用其余」**：先禁用当前正在使用的 alias 会让系统回收本进程，后面的启用就执行不到，结果一个启动入口都不剩（这个坑踩过）。
- alias 组件名不能只靠 `getPackageInfo(GET_ACTIVITIES)` 反查——部分版本**不返回被禁用的 alias**，所以还需要按清单前缀兜底，并加 `MATCH_DISABLED_COMPONENTS`。
- 应用启动时会 `reconcile()` 对账一次：若组件状态与记录不一致就修回「恰好启用一个」，且状态一致时不调用任何 set，避免每次冷启动都触发组件变更。
- 自定义图片会先由 Coil 解码成 288×288 的方形位图（`allowHardware(false)` + `Scale.FILL`），再作为 `IconCompat.createWithAdaptiveBitmap` 交给桌面；尺寸控制在 Binder 事务上限内。

## 设置

顶栏右上角齿轮进入：

| 项 | 内容 |
|---|---|
| 关于 | 应用图标 / 名称 / 作者 / 版本 + 应用说明 + 数据来源 + **开源许可** |
| 更改图标 | 6 套预设配色（换真实启动图标）+ 用自定义图片创建桌面入口 |
| 自定义更新时间 | 仅手动 / **每天 18:00** / 1 / 3（默认）/ 7 / 14 / 30 天；同页显示上次更新、下次自动更新、已应用增量，并有「立即更新一次」 |
| AI 分析 | DeepSeek Key（周榜分析 + 单曲解读）、模型、接口地址、豆包搜索 Key，各带申请入口 |
| 重看新手引导 | 再看一遍首启的那 4 页 |
| 外观 | 跟随系统 / 浅色 / 深色 |

「关于」页里也保留了图标与「更改图标」入口，从设置进去后可直接点。

### 外观（深色 / 浅色）

玻璃的明暗是从 `MaterialTheme.colorScheme.background` 的亮度推导的（`isDarkGlass()`），
所以只要在这里选定模式，整应用的深浅色会一起跟着变，**不需要逐个页面传 `dark`**。
这也是为什么「写死 dark」那类 bug 会有构建期检查兜底。

### 「每天 18:00」是什么语义

它和别的档位不同：别的档位是「距上次更新满 N 天」，这一档按**墙上时间**算——
过了今天 18:00，下次就是明天 18:00，不会因为你在 19:00 打开过就漂到明天 19:00。

目前是**打开时检查**：18:00 之后第一次打开会更新，之前打开则不会。
要做到「App 关着也在 18:00 自己更新」需要后台定时任务（JobScheduler），
那套机制做过又被移除，原因是它同时带来了通知功能而用户不需要。
数据新鲜度两者是一样的，区别只是网络请求发生的时刻。

## 收藏与备份

收藏存在本机 `SharedPreferences`（按 bvid），**不会上传**。

收藏页顶部有「导入 / 导出」：

- **导出**：写出一个 JSON 文件，内容形如
  ```json
  { "app": "Vocaloid List", "version": 1, "exportedAt": 1760000000000, "count": 2,
    "favorites": [ { "bvid": "BV1UGa961Ejt", "title": "えねみぃ", "titleCn": "敌人",
                     "producer": "ピノキオピー", "vocalist": "初音ミク" } ] }
  ```
  除了 bvid 还写了曲名与 P主 —— 这样用户自己打开文件也看得懂，而不是一长串 BV 号。
- **导入**：**并集**合并，只增不减。导入旧备份不会把新收藏冲掉。

容错上按「尽量救回能认的部分」处理：坏记录跳过、坏文件明确报错、
来自更高版本的文件拒绝并说明原因。也接受手写的极简格式（纯数组、甚至纯字符串数组）。

> 为什么做这个：收藏只在本机，换机、重装、**改包名**都会丢——改包名那次真实发生过一次。

## 数据更新机制

| 项目 | 说明 |
|---|---|
| 更新周期 | **可自定义**（设置 → 自定义更新时间）：仅手动 / 1 / 3（默认）/ 7 / 14 / 30 天，存在 `SettingsStore`（SharedPreferences） |
| 触发时机 | 每次启动检查一次；未到周期时**完全不联网**；设为「仅手动」则永不自动联网 |
| 榜单增量 | 抓每个榜**最新的 6 期**排名（覆盖新期 + 最新一期的播放量变化），落盘 `files/live_snapshot.json` |
| 曲库增量 | 曲库接口按发布时间从新到旧返回，从第 1 页往后扫，遇到整页都是已有曲目就停（上限 10 页/次），落盘 `files/live_catalog.json` |
| 增量上限 | 榜单每榜 6 期；曲库增量最多保留 3000 首 |
| 合并方式 | 启动时「内置基线 + 本地增量」合并，所以离线也能看到已更新过的内容 |
| 手动更新 | 首页右上角刷新按钮、「设置 → 自定义更新时间 → 立即更新一次」，都不受周期限制 |
| 计时起点 | 首次启动时以**内置快照的生成时间**为起点，避免刚装好就白跑一次网络请求 |

一次典型更新的请求量：1（榜单目录）+ 3×1（期列表）+ 约 17（各期排名）+ 1~2（曲库）= **约 25 个请求**。

界面上会显示：上次更新时间、下次自动更新时间（含「2 天后」这类相对描述）、当前已应用的增量条数、以及上次更新的结果或错误。

## AI 解读每一首歌（联网搜索 + DeepSeek）

目标：**搞懂每首歌在讲什么**。曲目详情页有「AI 解读这首歌」卡片，点一下走完整条链路。

流程两步，缺一不可：

1. **先用豆包搜索联网查资料**。模型自己并不知道某首术曲的歌词——不先搜它就会编。搜索词由曲名 + 中文名 + P主 + 歌手拼成（`Query` 上限 100 字符，本地先截）。
2. **再把「歌曲信息 + 检索到的资料」交给 DeepSeek**，要求输出 400 字以内中文解读，并明确要求「只依据我给的资料，不确定就直说」。

- 搜索失败**不阻断**解读：原因写进提示词，让模型自己说明资料有限。
- 提示词把「歌曲信息」与「联网资料」分两块，并注明资料来自网络、可能不准确。
- 结果按 `bvid` 缓存在内存里，同一首不会重复消耗额度，可手动清除。
- 没配豆包 Key 时仍能解读，但只会说「资料有限」——不建议。

**两个 Key 都由用户自己填**（设置 → AI 分析）：DeepSeek Key 用于写周榜分析 + 单曲解读，豆包搜索 Key 用于联网查资料。应用不内置、不代理、不上传。

### 豆包搜索（Custom 版）接口要点

实测确认，都写在 `DoubaoSearchApi.kt` 里：

- `POST https://open.feedcoopapi.com/search_api/web_search`，`Authorization: Bearer <key>`
- 请求体 `{ Query, SearchType: "web", Count, NeedSummary: true }`
- `Query` 超 100 字符会被服务端截断，本地先截
- **错误可能是 HTTP 200 + `ResponseMetadata.Error`**，不能只看状态码
- `Summary`（500~1000 字）比 `Snippet`（约 200 字）更适合喂模型，优先取

## AI 分析周榜（DeepSeek）

**API Key 完全由用户自己填**，应用不内置、不代理、不上传到除该 AI 服务以外的任何地方，只存在本机 `SharedPreferences`。

- 入口：任意一期榜单 → 卡片「AI 分析本期」。
- 提示词把这一期的名次 / 曲名 / 中文名 / P主 / 播放 / 得分 / 名次变化 / 在榜期数 / 最高名次整理成紧凑的竖线分隔表（比 JSON 省 token），要求模型输出 400 字以内的趋势解读。
- 结果按 `issueId` 缓存在内存里，同一期不会重复消耗额度，可手动清除重来。
- 失败分类提示：401 Key 无效、402 余额不足、429 请求过频。
- 默认 `https://api.deepseek.com`，也兼容任何 OpenAI `/chat/completions` 格式的服务（Base URL 可改）。
- 「AI 分析本期」与「AI 解读这首歌」共用同一张 `AiResultCard`（`ui/AiCard.kt`）。

## 用 B 站接口补播放量与封面

曲库里有些曲子**没上过周榜**，这类曲目没有排名、没有播放量，biliboard 图床也多半没有封面。

- 接口：`https://api.bilibili.com/x/web-interface/view?bvid=` —— **一个请求同时返回** `stat.view`（播放量）和 `pic`（封面），所以「补播放量」和「补封面」共用同一份数据、只请求一次。
- 触发时机：曲目详情页（`snapshot.song(bvid) == null` 即没上过榜），以及搜索结果 / 收藏列表里 `chartCount == 0` 的行。
- 封面采用**链式回退**：先试 biliboard 图床，`AsyncImage.onError` 触发后换 B 站封面再试一次，两个都失败才显示占位。
- 结果落盘 `files/bili_cache.json`（上限 4000 条，按抓取时间淘汰）；本会话内失败的 bvid 记下来不再重试。
- 请求间隔 260ms 限速，避免被 B 站风控。

两个坑：

- 必须带 `User-Agent` 与 `Referer`，否则 B 站返回 `-412` 风控错误；
- `pic` 返回的是 `http://`，而 Android 9+ 默认禁止明文流量，必须改写成 `https://`。

HTTP 用 JDK 自带的 `HttpURLConnection` 而不是 OkHttp：应用只是通过 Coil 的传递依赖间接拿到 OkHttp，能否在编译期引用它取决于依赖 scope 声明，不值得为此冒险。

## 平板 / 大屏适配

按 Material 3 的窗口宽度断点分档，界面对窗口大小变化（旋转、分屏、折叠屏展开）即时响应：

| 档位 | 宽度 | 导航 | 内容宽度 | 首页榜单卡片 | 曲目详情 | 关于页 |
|---|---|---|---|---|---|---|
| Compact | < 600dp | 底部导航栏 | 铺满 | 纵向堆叠 | 居中单栏 | 上下两半屏 |
| Medium | 600–839dp | **侧边导航栏** | 居中限宽 700dp | 纵向堆叠 | 居中单栏 | 左右两栏 |
| Expanded | ≥ 840dp | **侧边导航栏** | 居中限宽 780dp | **三张横排** | 居中单栏 | 左右两栏 |

其它细节：

- 侧边导航栏顶部放应用图标，用玻璃底 + 右缘高光，与整体风格一致。
- 宽屏下所有内容居中并限制最大宽度，避免一行从屏幕这头拉到那头；首页因为要横排卡片单独放宽到 1240dp。
- 清单里声明了 `resizeableActivity` 与 `supports-screens`，支持分屏 / 自由窗口；Activity 通过 `configChanges` 自行处理尺寸变化，不重建。
- 未锁定屏幕方向，横竖屏都会重新排版。

> **曲目详情页的平板两栏布局已移除**，现在所有档位都是居中单栏。原因是这一页曾被反馈排版错乱，两栏需要把内容塞回 `Row + Column` 的嵌套里，而那正是容易再出问题的地方。等单栏版本稳定后再考虑加回来。

## 功能

| 页面 | 说明 |
|---|---|
| 榜单（首页） | 三个榜单：周榜（121 期）、传说曲周榜（82 期）、年榜（5 期）；卡片展示最新一期与冠军曲，顶部是数据来源与更新状态 |
| 期数列表 | 某一榜的全部期数（由新到旧），每行显示期号、年周、冠军曲、收录数、统计截止日 |
| 榜单详情 | 某一期的 Top N 逐曲排名，含名次、名次变化（NEW / ▲n / ▼n）、封面、P主、播放量；顶部有「AI 分析本期」卡片 |
| 曲目详情 | 整页是一个 **LazyColumn**，每块信息一个 item：封面 → 指标 → 操作 → 创作信息 → 最近一次上榜数据 → 播放量走势 → 上榜历史 → 数据来源。背景会换成该曲封面的主题色；没上过榜的曲子用 B 站数据补封面与播放量。手机与平板同一套结构（宽度由外层统一限制） |
| 搜索 | 检索曲库 **14155 首 + 已更新的新曲**，支持曲名、中文名、P主、歌手、BV 号与别名（`miku` → `初音ミク`、`匹诺曹P` → `ピノキオピー`） |
| 收藏 | 本地收藏曲目，SharedPreferences 持久化；收藏/取消收藏有 Snackbar 提示 |
| 关于 | 上半屏是应用图标、名称、作者（Zachary & 大肥鱼）与版本号；下半屏是应用说明与数据来源（biliboard.uk，可一键打开） |
| 应用图标 | 从「设置 → 更改图标」或「关于」页的图标进入：6 套预设配色（切换真实启动图标）+ 用自定义图片创建桌面入口 |
| 设置 | 顶栏齿轮进入：关于 / 更改图标 / 自定义更新时间 / AI 分析 |

## 构建期硬规则（`guardGlassTheme`）

玻璃组件的明暗**必须由主题决定**。这个错误犯过三次（设置页三行 / AI 按钮 / 图标选择框），每次表现都是「颜色和背景不融合」——不崩、不报警，只有肉眼能发现，编译器管不了。所以加了两道防线：

1. **默认值自动跟随主题**：`GlassSurface` / `CoverImage` / `GlassBackdrop` 的 `dark` 参数默认值就是 `isDarkGlass()`。不传 → 自动正确。
2. **构建期强制检查**：`app/build.gradle.kts` 里的 `guardGlassTheme` 任务挂在 `preBuild` 上，扫描 `ui/` 下所有 `.kt`，发现 `dark = true/false` 字面量直接 `BUILD FAILED` 并打印文件与行号。

实测有效（故意放一处违规）：

```
检测到写死的玻璃明暗值：
    _GuardProbe.kt:9  GlassSurface(dark = false) { }
玻璃配色必须跟随主题。请删掉该参数：
  · GlassSurface / CoverImage / GlassBackdrop 的 dark 默认值已经是 isDarkGlass()
  · 需要往下传时，让它一路从 isDarkGlass() 来，不要写字面量
BUILD FAILED
```

注意这条规则**只覆盖玻璃明暗这一类问题**。布局类（容器包错）与闪退类（列表 item 里嵌可滚动组件）它管不了。

## 单元测试

```powershell
powershell -File build.ps1 -Task ":app:testDebugUnitTest"
```

**160 个测试，不需要模拟器。** 纯逻辑部分 0.3 秒跑完；Compose 界面测试走
Robolectric，在 JVM 上渲染（首次运行要下 android-all 包，之后有缓存）。
报告在 `app/build/test-results/testDebugUnitTest/`，失败时看 `app/build/reports/tests/`。

| 测试 | 数量 | 覆盖 |
|---|---|---|
| `MotionTest` | 8 | 级联进场进度的映射 |
| `RepoStateTest` | 16 | 自动更新周期，重点是「每天 18:00」的边界 |
| `ParsersTest` | 12 | 榜单目录 / 期列表解析，含 snake_case 与 camelCase 双拼写 |
| `AiPromptTest` | 12 | 搜索词长度上限、提示词组装、无资料时的防编造提示 |
| `BiliApiTest` | 6 | 封面地址 `http://` → `https://` |
| `SettingsStoreTest` | 10 | 更新档位文案、外观档位的解析与回退 |
| `SettingsStorePersistenceTest` | 11 | 引导开关、外观、周期、两个 AI Key 的持久化与默认值 |
| `FavoritesCodecTest` | 18 | 收藏导出/导入格式：往返、容错、去重、版本、坏数据 |
| `FavoritesStoreTest` | 14 | 收藏存储与 `merge`（只增不减）、重开还原 |
| `ProducerIndexTest` | 18 | P主 倒排索引、多 P主 去重、关注流合并、推荐排序 |
| `FollowStoreTest` | 10 | P主 关注的增删、去空白、特殊字符、持久化 |
| `AiResultCardTest` | 18 | AI 卡片在 LazyColumn 里的渲染、四种状态、按钮与复制 |
| `SongDetailLayoutTest` | 6 | 详情页各区块的顺序与**不重叠**，深浅色两套 |

### 为什么加这些测试

这个项目此前的验证方式是「改代码 → 装到模拟器 → 用眼睛看」，而模拟器本身还不稳定。
结果是一批**不崩溃、不报错、只能靠肉眼发现**的缺陷反复出现：

| 出过的问题 | 现在由哪条测试挡住 |
|---|---|
| 级联动画让列表第 12 条之后**永远不可见**（表现为「搜索命中 300 条只显示 12 条」） | `MotionTest.fullProgress_everyIndexIsVisible` |
| 修完上一条后，那些项又变成**第一帧就全亮**，顺序反了 | `MotionTest.zeroProgress_nothingVisible` |
| 「每天 18:00」可能被写成「距上次满 24 小时」 | `RepoStateTest` 的 `dailyAt18_*` 一组 |
| 服务端字段换拼写导致年份静默变 0 | `ParsersTest.issues_readsCamelCase` |
| 封面 `http://` 没转换，加载失败 | `BiliApiTest.neverReturnsPlainHttp` |
| **AI 分析刚出结果就闪退**（LazyColumn 的 item 里嵌了 verticalScroll） | `AiResultCardTest.doneState_rendersInsideLazyColumn_withoutCrash` |
| **详情页排版错乱、内容重叠**（外层用 Box 把区块叠在一起） | `SongDetailLayoutTest.sections_appearTopToBottom_withoutOverlap` |

写测试时还顺带发现并修掉四处真问题：
`introCascade` 把超窗项做成「第一帧全亮」（顺序反了）；
`Parsers.parseIssues` 的字段别名只认 `issue_year` 不认 `issueYear`（驼峰会静默变成 0）；
`BiliApi.normalizeCover` 对 `HTTP://` 大写协议头不生效；
`FavoritesCodec.decode` 先试 JSON 对象、失败即返回错误，导致**纯数组格式的备份文件根本读不进来**。

### 界面测试怎么在 JVM 上跑

Robolectric 让 Compose 能在普通 JVM 里渲染，因此**不需要模拟器**——这正是为了绕开
「模拟器不稳定 → 布局问题只能靠肉眼」这个死结。

两个环境上的坑（都已在 `app/build.gradle.kts` 里处理）：

1. Robolectric 会把下载锁和 android-all 缓存写到 `user.home` 下，而这台机器的
   `C:\Users\wuzih` 对构建进程不可写，会直接抛 `Couldn't create lock file`。
   解决办法是把测试 JVM 的 `user.home` 指到 `app/build/test-home`。
2. 工程的 `compileSdk` 是 37，但 Robolectric 还没有对应版本的 android-all，
   所以在 `src/test/resources/robolectric.properties` 里固定在 **sdk=34**。
   界面逻辑与配色不依赖 37 的新特性，34 上的结论对 37 同样成立。

**注意 Compose 测试里 `setContent` 每个用例只能调用一次**，所以每种状态各写一个
测试方法，而不是在一个方法里循环。

### 这些测试本身验证过吗

回归测试没被证明过就等于没有，所以关键的两条都做了对照实验：

| 保证 | 验证方式 | 结果 |
|---|---|---|
| 测试不过就出不了 release 包 | 临时写一个必失败的测试再构建 | `BUILD FAILED`，且**没有生成新 APK**（时间戳未变） |
| 闪退回归测试真能抓到闪退 | 把 `verticalScroll` 临时放回 `AiCard` | 4 个测试失败，异常正是当初那条 `Vertically scrollable component was measured with an infinity maximum height constraints` |
| 重叠判据不是「永远通过」 | `overlapCheck_canActuallyDetectOverlap`：故意用 `Box` 叠两块 | 被判为重叠 ✓ |

> 仍然**没有覆盖**的：真实网络请求、AI 成功返回后的完整页面、平板两栏布局、
> 低版本 Android（8~12）的实际表现。这几类只能靠实机或真机跑。

## 新手引导（首启 OOBE）

首次安装打开时会先走 4 页引导，看完（或点「跳过」）才进主界面。
之后可以在「设置 → 重看新手引导」再看一遍。

**只讲用户不看就会误解的四件事**，不讲「怎么滑动列表」这类废话：

| 页 | 内容 | 为什么必须有 |
|---|---|---|
| 1 | 是什么 + **第三方客户端** | 与 Biliboard 官方无关，这话必须说在前面 |
| 2 | **数据内置、每 3 天增量更新、不实时** | 不说清楚最容易招来「怎么没更新」 |
| 3 | AI 是选配、要自己填 Key | 免得用户以为 AI 不能用是坏了 |
| 4 | 收藏记得导出备份 | 换机重装会丢，这事已经发生过一次 |

- 右上角有「跳过」，不逼人一页页翻完。
- 第 3 页额外给一个「现在就去填 Key」直达 AI 设置页。
- 实现上是**提前返回**而不是弹窗或新页面：引导期间不该存在底部导航和返回栈。
- 状态存 `onboarding_done`，默认 `false`。

## P主 关注

底部导航第 3 个标签。**全部本地计算，不联网** —— 曲库里 14000+ 首本来就带 P主 与投稿时间，
所以点进去立刻出内容。

三条路径：

1. **关注**：曲目详情 → 点「创作信息」里的 P主 名字（主色 + 下划线，表示可点）→ P主页 → 「关注」。
2. **看**：「关注」页上半是关注的 P主 列表（可取消关注），下半是他们的最新投稿合并流。
3. **发现**：还没关注任何人时，列出曲库里投稿最多的 12 位 P主 作为起点。

几个实现上的取舍：

- 关注的是**名字原文**，不做别名归一。曲库里的别名是给搜索用的，拿它当关注键会把不同写法
  混成一个人，反而不好解释「我到底关注了谁」。
- 一首歌可能有**多位 P主**，而用户可能都关注了，所以合并流按 bvid 去重。
- P主 → 曲目 建了**惰性倒排索引**（`ProducerIndex`，纯函数、可单测），并记住对应的曲库实例；
  曲库更新后自动重建。否则每次点进 P主页都要全表扫 14000 首。
- P主页里曲目超过 20 首时**不做级联进场动画** —— 否则要滑很久才全部出现。

## 数据来源

采集时（构建期）调用的 biliboard.uk 公开接口：

```
GET /api/public/boards                                    榜单目录
GET /api/public/boards/{boardId}/issues                   期列表
GET /api/public/boards/{boardId}/issues/{issueId}/rankings 某期逐曲排名
GET /api/public/songs?page=&per_page=                     全站曲库（含每首的 rank_history）
GET /api/provider/biliimg/bvid/{bvid}                     封面图（302 到 B 站图床）
```

内置快照：**3 个榜 / 208 期 / 4358 条排名 / 669 首上榜曲 + 全站曲库 14155 首 + 87 条别名**。

> App 运行时只在两种情况下联网：加载曲目封面图，以及每 3 天一次的增量更新
> （`INTERNET` 权限用于这两处）。平时浏览与搜索全部读本地数据。

## 工程结构

```
BiliBoard/
├── app/src/main/
│   ├── assets/
│   │   ├── biliboard_snapshot.json   内置榜单快照（2.25 MB）
│   │   └── catalog.json              内置全站曲库（2.13 MB）
│   ├── java/uk/biliboard/app/
│   │   ├── BiliBoardApplication.kt   Coil 图片加载器（挂 OkHttp）
│   │   ├── MainActivity.kt           入口，铺液态玻璃底色
│   │   ├── data/
│   │   │   ├── Model.kt              Board / Issue / RankEntry / Song / Catalog
│   │   │   ├── Parsers.kt            org.json 解析（兼容内置精简键名与线上原始键名）
│   │   │   ├── BoardApi.kt           biliboard.uk 接口（返回原始 JSON 字符串，便于直接落盘）
│   │   │   ├── LiveStore.kt          增量数据的本地存储（live_snapshot / live_catalog / lastUpdateAt）
│   │   │   ├── SettingsStore.kt      用户偏好（自动更新周期，SharedPreferences）
│   │   │   ├── BoardRepository.kt    基线 + 增量合并、3 天周期的增量更新、离线检索
│   │   │   └── FavoritesStore.kt     收藏持久化
│   │   └── ui/
│   │       ├── Glass.kt              液态玻璃视觉层（背景柔光 + 玻璃面板 + 封面主题色上色）
│   │       ├── Motion.kt             非线性动效（缓动曲线、弹簧、级联进场驱动）
│   │       ├── CoverAccent.kt        从封面抽取主色/次色（直方图 + 鲜艳度加权）
│   │       ├── Adaptive.kt           窗口宽度分档与各页面内容最大宽度
│   │       ├── AppIcon.kt            应用图标（与启动图标共用矢量资源）
│   │       ├── Theme.kt              深浅色 Material3 配色
│   │       ├── AppRoot.kt            页面栈 + 玻璃顶栏/底栏
│   │       ├── HomeScreen.kt / IssuesScreen.kt / RankingScreen.kt
│   │       ├── SongDetailScreen.kt / SearchScreen.kt / FavoritesScreen.kt
│   │       ├── AboutScreen.kt        关于页（图标 / 名称 / 作者 / 说明 / 数据来源）
│   │       ├── AppIconScreen.kt      应用图标设置页（预设配色 + 自定义图片）
│   │       ├── SettingsScreen.kt     设置页 + 自定义更新时间页
│   │       ├── AppIconSwitcher.kt    activity-alias 切换、固定快捷方式、启动时对账
│   │       └── Widgets.kt / Common.kt / AppViewModel.kt
│   └── res/                          主题、图标（自适应矢量图标）
└── tools/                            构建期数据管线（Node.js，无需依赖）
```

## 数据管线（重新生成内置资源）

```bash
node tools/1-harvest-boards.cjs     # 采集三个榜的全部期与排名  -> <工作区>/data/biliboard-snapshot.json
node tools/2-harvest-catalog.cjs    # 采集全站曲库            -> <工作区>/data/biliboard-catalog.json
node tools/3-prepare-assets.cjs     # 精简并写入 app/src/main/assets/
```

`3-prepare-assets.cjs` 可重复执行且结果稳定（榜单 3.54 MB → 2.25 MB，曲库 5.01 MB → 2.13 MB）。

## 构建

> 环境依赖与首要步骤见上面的 **Environment Setup / 环境准备**；本节是更细的构建说明与版本组合。

| 项 | 值 |
|---|---|
| 包名 / namespace | `za.bilbd.app`（debug 构建自动加 `.debug` 后缀） |
| 版本名 | `1.145` |
| versionCode | `1145` |
| minSdk | 26（Android 8.0） |
| targetSdk / compileSdk | 37 / 37.1（Android 17） |
| ABI | arm64-v8a、armeabi-v7a、x86、x86_64（四种都打进去了，手机平板通用） |
| 权限 | 只有 `INTERNET` |

```powershell
powershell -File build.ps1 -Task ":app:assembleDebug"     # 调试包
powershell -File build.ps1 -Task ":app:assembleRelease"   # 发布包（用 debug 签名，可直接安装）
```

产物：`app/build/outputs/apk/{debug,release}/`

> 改包名时注意：`namespace` 决定清单里 `.MainActivity` / `.icon.IconX` 这些相对名解析到哪个包，
> `applicationId` 只决定安装后的包名。两者要一起改；另外 `AppIconSwitcher.ALIAS_PREFIX`
> 是硬编码的 alias 前缀，`app/build.gradle.kts` 里 `guardGlassTheme` 的 `uiSourceDir`
> 也是硬编码路径，改名时都要跟着改（这两处漏掉过一次：前者会让换图标失效，后者会让构建直接失败）。

### 本机环境注意事项

本工程在这台机器上踩到了几个坑，`build.ps1` 已经处理好：

1. PATH 上的 `java` 是 1.8，AGP 需要 JDK 17+ → 脚本固定使用工作区里的 JDK 21。
2. `~/.gradle` 下 `native-platform.dll` 加载失败 → 改用工程内 `.gradle-home` 作为 `GRADLE_USER_HOME`。
3. 构建子进程写系统 `TEMP` 被拒绝 → `TMP/TEMP` 指向工程内 `.tmp`。
4. AGP 默认把 `debug.keystore` 写到 `~/.android`，同样被拒绝 → `ANDROID_USER_HOME` 指向工程内 `.android-home`（缺失时 AGP 自动生成）。

### 版本组合

| 组件 | 版本 | 说明 |
|---|---|---|
| AGP | 9.4.1 | **AGP 9 起内置 Kotlin**，不能再应用 `org.jetbrains.kotlin.android` |
| Gradle | 9.8.1 | |
| Kotlin | 2.4.21 | 仅应用 `org.jetbrains.kotlin.plugin.compose` |
| JDK | 21 (Temurin) | |
| compileSdk | 37 + `compileSdkMinor = 1` | 对应已安装的 `android-37.1` 平台 |
| buildTools | 37.0.0 | |
| minSdk / targetSdk | 26 / 37 | |
| Compose BOM | 2026.09.00 | material3 / ui / material-icons-core 1.7.8 |
| Coil | 3.6.3 | `coil-compose` + `coil-network-okhttp` |

> 现代的 androidx / Compose / Coil 依赖都要求 `compileSdk >= 37`，所以这里必须用 37.1 而不是 36。

## 非线性动效（`Motion.kt`）

所有缓动都走三次贝塞尔，没有一条是匀速的：

| 曲线 | 值 | 用在哪 |
|---|---|---|
| Emphasized Decelerate | (0.05, 0.7, 0.1, 1.0) | 页面进场、列表级联驱动 |
| Emphasized Accelerate | (0.3, 0.0, 0.8, 0.15) | 页面出场 |
| Standard / Decelerate / Accelerate | Material 标准三条 | 标签页切换、透明度 |
| 欠阻尼弹簧 | dampingRatio 0.42 / stiffness Medium | 玻璃面板按压（松手有回弹） |
| 柔和弹簧 | dampingRatio 0.72 / stiffness MediumLow | 整块内容进场 |

具体效果：

- **页面转场**（`AnimatedContent`）：前进时新页从右侧滑入并淡入（减速曲线，起步快、收尾缓），旧页向左收走（加速曲线，离场干脆）；返回时方向相反且出场位移更小；切标签页不位移，只做淡入 + 从 0.965 放大。进出场用**不同**的曲线，位移才有惯性感。
- **列表级联进场**：一次 720ms 的 emphasized-decelerate 驱动值由 AppRoot 通过 `LocalIntroProgress` 下发，每个列表项用 `Modifier.cascade(index)` 从同一个进度里推导自己的局部进度，再过一次 smoothstep —— 天然错峰，且**整屏只跑一次**，滚动回来不会重复播放。
- **背景柔光**：四团光用不同的 `speed`（1.00/0.83/1.19/0.91），且 X、Y 用不同频率（`cos(a)` / `sin(0.73a)`），走李萨如曲线，轨迹不闭合、不会周期性一起「归位」。
- **封面主题色过渡**：颜色不适合用会过冲的弹簧（中途会闪脏色），所以走强调标准曲线，720ms。

性能上有一条硬约束：**所有动画状态只在 `graphicsLayer {}` 里读取**，所以每帧只更新图层属性，不触发重组。实测静止 8 秒仍是 20 帧（加动效前 19 帧），没有因为动效增加静止功耗。

## 已修复的问题（用户反馈）

| 现象 | 根因 | 修复 | 实机验证 |
|---|---|---|---|
| 搜索命中 300 条却只显示 12 条（榜单 20 首也只显示 12 首） | 上一版加的级联动画里 `local = progress * span - index`，`span = 12` 时 index ≥ 12 算出负数，`coerceIn(0,1)` 后 `alpha` **永久为 0** | 超出错峰窗口的项直接按「已到位」处理 | ✅ 可滚到第 12 条之后 |
| 曲目详情排版错乱（多个卡片叠在同一位置） | `DetailSections` 会发射多个**兄弟**卡片，必须放在 `Column` 里；却用 `Box(Modifier.cascade(...))` 包了它，而 **Box 会把子项叠在一起**。改成 `Column` 后仍被反馈「还是错乱」 | 整页重写为**单个 LazyColumn**，每块信息一个 item；`DetailSections` 拆成四个卡片函数；去掉这一页的 cascade 动画，只保留列表这一种布局机制 | ✅ 顶部与滚动后都确认不再重叠 |
| AI 分析时闪退 | 「分析完成」分支的 `Text` 上挂了 `Modifier.verticalScroll`，而它所在的 `AiCard` 是 **LazyColumn 的 item**——LazyColumn 给 item 的主轴约束是无限高，嵌套可滚动组件直接抛异常。时序正好是「结果刚拿到就崩」 | 去掉 `verticalScroll`；并给 AI 调用加 `runCatching` 兜底，任何意外异常变成卡片上的错误提示 | ✅ 真实打到 api.deepseek.com，401 被正确提示，进程存活 |
| 深色模式下设置页配色错误（三行发白） | `SettingRow` / `IntervalOption` 里把 `dark` 写死成 `false` | 去掉行内那层玻璃，由外层卡片提供底色 | ✅ |
| AI 按钮 / 图标选择框颜色不融合 | 同样是写死 `dark`（AI 按钮 `false`、图标框 `true`）；AI 设置页的输入框还**把明暗用反了**（深色用黑、浅色用白，两边都看不见） | 全部改为跟随主题；输入框改成深色提亮 / 浅色压暗 | ✅ 深浅两种主题都确认 |
| 封面背后的颜色不对 / 封面有时加载不出来 | ① `CoverSection` 是全应用唯一还在用 `elevation = 8.dp` 的地方，阴影画在半透明玻璃后会透出来把面板压脏；② 占位色用了不透明的 `surfaceVariant`；③ 图床没有该曲目时没有兜底 | 去掉 elevation；占位色改为跟随玻璃的低透明度填充；加 biliboard → B 站 的链式回退 | ✅ 详情页封面背景已正常 |
| 收藏没有提示 | 只翻转了状态，没有任何反馈 | 加 Snackbar（「已收藏 / 已取消收藏」，清空收藏也有提示） | 代码级 |
| 背景动画卡顿 | 相位步长 400ms，配合李萨如轨迹能看出「一跳一跳」 | 步长缩到 120ms | 代码级 |
| 在二级页点底部标签无反应 | `selectedTab` 取自 stack 首项，二级页的首项本就是该标签的根，`selectedTab != index` 为 false → 直接 no-op | 点当前标签时改为回到该标签的根页面 | 代码级 |

> 「AI 分析出结果并显示」这条分支本身只验到了错误分支（手上没有可用的 Key）——但那一分支里引发闪退的 `verticalScroll` 已删除，且 `RankingScreen.kt` 里已无任何 `verticalScroll` 用法，这一点有 grep 佐证。


## 液态玻璃实现说明

- 背景是四团彩色柔光（径向渐变图层）缓慢漂移，叠加在底色渐变上；上下暗角并入底色，少一层全屏混合。
- 面板 = 半透明渐变填充（顶部镜面高光已合成进同一条渐变）+ 1dp 高光描边；不对内容做模糊，否则文字会糊。
- 相位每 400ms 更新一次（不是每帧），并用 `graphicsLayer` 只改变换矩阵，因此**静止时不重绘**：实测静止 8 秒仅 19 帧。
- `Modifier.blur` 未使用：模拟器是软件渲染，全屏逐帧模糊会严重掉帧。

### 曲目详情页的封面主题色

进入曲目详情时，整屏背景会平滑过渡成**该曲封面的主色调**；返回列表再淡回默认配色。

- **取色**（`CoverAccent.kt`）：用 Coil 取一张 48×48 小图（命中 Coil 缓存，不会重复下载），把像素量化到 4 位/通道做直方图，按「鲜艳度² × 明度接近 0.62 的程度」加权选出主色；再挑一个色相相差 40° 以上的作为次色（找不到就由主色旋转 42° 派生）。最后做主色增艳与背景用色钳制，保证上面压白字仍然清楚。
- **上色**（`GlassBackdrop`）：四团柔光的颜色各自 `animateColorAsState` 过渡到主色/次色（650ms），再叠一层很淡的主色「水洗」。两个状态之间是平滑互切的，所以来回进出歌曲页不会闪。
- **缓存**：取色结果按封面 URL 缓存在进程内，同一首歌反复进出不重复解码。
- 浅色主题下柔光会向白色靠拢、水洗更淡，避免把浅底压闷。

## 验证

在 Android 17（API 37，x86_64，1080×2424）模拟器上实机验证：

**基础功能**

- `adb install` 成功；冷启动约 4–8 秒；无崩溃、无 `FATAL EXCEPTION`、无 ANR。
- 首页 → 周榜 → 第 122 期 → 曲目详情 → 收藏 → 收藏页，全链路点击可达，收藏状态持久化。
- 顶栏 ⓘ 可进入关于页：上半屏显示图标、名称、作者（Zachary & 大肥鱼）与版本号，下半屏显示应用说明与数据来源，返回正常。深浅色两套配色均已验证。
- 搜索 `初音ミク` 命中 300 条（内置曲库上限），别名检索有效。
- 帧统计（软件渲染）：静止 8 秒 19 帧；滚动压力测试中位帧时间 44ms（优化前为 125ms）。

**更新机制**

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 未到周期不联网 | 干净安装后启动，检查 app 私有目录 | `files/` 只有 `profileInstalled`，**没有产生任何增量文件**；界面显示「上次更新 2026-10-10 14:35 / 下次自动更新 2026-10-13 14:35（2 天后）」 |
| 手动立即更新 | 点击首页刷新按钮 | 生成 `live_snapshot.json`（508 KB），界面显示「上次更新 15:48 / 下次 10-13 15:48 / 已是最新，没有新增内容」 |
| 增量内容正确性 | 拉出增量文件解析 | 3 个榜各取最新 6 期（年榜仅 5 期），每期 20 条排名，字段保持线上原始格式 |
| 到周期自动更新 | 临时把周期改为 1 分钟、清数据后冷启动，**不做任何点击** | 启动后自动生成 `live_snapshot.json`，无需交互 |
| 无新增时提前收敛 | 曲库第 1 页全为已知曲目 | 立即停止翻页，不生成 `live_catalog.json`，不产生多余请求 |

**平板适配**（用 `adb shell wm size/density` 模拟三种窗口尺寸验证）

| 窗口 | 实测 | 结果 |
|---|---|---|
| 2560×1600 @240dpi（1280×800dp → Expanded） | 首页 / 期数 / 榜单 / 曲目详情 / 关于 | 侧边导航栏生效、底部导航消失；三张榜单卡片横排；**关于页左右两栏**（曲目详情已改为居中单栏，见上方说明）；内容居中限宽 |
| 1600×2560 @320dpi（800×1280dp → Medium） | 首页 | 侧边导航栏生效；卡片回到纵向堆叠；内容居中限宽 700dp |
| 1080×2424 @420dpi（411×924dp → Compact） | 首页 | 底部导航栏、单栏铺满，与适配前一致 |

截图见 `shots/`（`9x-pad-*.png` 为平板，`97-phone-home.png` 为手机回归）。

**封面主题色**（实测三首不同配色的封面）

| 曲目 | 封面配色 | 结果 |
|---|---|---|
| えねみぃ | 黑 / 白 / 红 | 背景变深红 |
| Displayholic | 蓝 / 青 | 背景变深蓝 |
| チューチュードレイン | 青绿 / 粉 | 背景变深青绿 |

另已验证：从曲目详情返回榜单后背景会淡回默认配色；深浅色两种主题下都生效；取色无异常日志。

> 注意：`build.ps1` / `run.ps1` / `tap.ps1` **必须保持纯 ASCII**。Windows PowerShell 5.1 在没有 BOM 时按 ANSI/GBK 读取 .ps1，中文注释的字节会被解码成引号或反引号从而破坏语法（这个坑踩过两次）。

**图标切换**（在模拟器上实测）

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 应用名 | 启动器标签 / 顶栏 / 关于页 | 均为 `Vocaloid List` |
| 启动入口唯一性 | `cmd package query-activities -a MAIN -c LAUNCHER` | 只有 1 个入口（5 个禁用的 alias 正确被排除） |
| 预设配色切换 | 点「海蓝」后重新查询 | 入口由 `icon.IconDefault` 变为 `icon.IconBlue`，应用未被杀，仍只有一个入口 |
| 自定义图片 | 选图 → 系统弹出「添加到主屏幕」确认框 | 确认框中的圆形预览就是所选图片；确认后桌面上出现两个入口（应用本体 + 自定义图标） |
| 移除入口 | 应用内「移除」按钮 | 出现（说明已记录），点击后调用 `disableShortcuts` |

> 模拟器是软件渲染（swiftshader），桌面图标层没有合成进 `screencap`，所以桌面截图看不到图标；上面的结论都来自系统查询（`query-activities`）与桌面无障碍节点树。

**动效**

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 列表级联 | 把 `animator_duration_scale` 设为 5 放慢后冷启动抓帧 | 早期帧内容区完全为空（全部 alpha=0 且带位移），随后整体就位 —— 错峰生效 |
| 页面转场 | 放慢后点击榜单卡片抓中间帧 | 抓到中间态：标题已切换、内容仍是上一页，随后落位 |
| 静止功耗 | `dumpsys gfxinfo` 重置后静止 8 秒 | 20 帧（加动效前 19 帧），未因动效增加 |
| 稳定性 | logcat | 无崩溃、无 ANR |

> 关于跳帧：本模拟器**软件渲染每帧约 69ms**，`Skipped N frames` 在这里不代表主线程被阻塞。对照实验：冷启动系统「设置」1.66s 也跳 92 帧，Pixel Launcher 跳 160 帧。

顺带修掉一处真问题：`loadBundled()` / `ensureCatalog()` 是 `suspend` 但没切 IO，而 `viewModelScope` 默认跑在主线程，等于把 4.4MB 的 JSON 读取与解析压在主线程上。现在都包进 `withContext(Dispatchers.IO)`，并把 `AppViewModel.init` 里三次重复的 `loadBundled()` 串成一条链。冷启动 7733ms → 7098ms。

**设置**

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 进入与三项 | 顶栏齿轮 → 设置 | 关于 / 更改图标（当前：紫粉）/ 自定义更新时间（每 3 天 · 下次 2 天后） |
| 改周期 | 选「仅手动更新」 | 落盘 `shared_prefs/biliboard_settings.xml` → `update_interval_days=0`；设置页副标题变「仅手动更新」 |
| 生效范围 | 首页更新卡 | 「数据来源 biliboard.uk · 仅手动更新（设置里可改周期）」「自动更新已关闭 · 可在设置里改」 |
| 重启持久化 | 杀进程重开 | 文案仍是「仅手动更新」 |
| 不联网 | 手动模式下 logcat | 无任何 biliboard.uk 请求 |
| 改回 | 选「每 3 天」 | 状态变回 `2026-10-13 14:35（2 天后）` |
| 子页导航 | 设置 → 关于 → 更改图标 | 均正常，无崩溃、无 ANR |
| 平板 | `wm size 2560x1600` + `density 240` | 设置页居中限宽，左侧导航栏 |

**曲目详情重写为 LazyColumn**

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 排版不再错乱 | 打开「えねみぃ」详情，分别截顶部与滚动后两屏 | 封面 → 指标 → 操作 → AI 解读 → 创作信息 → 最近一次上榜数据 → 播放量走势 → 上榜历史 → 数据来源，依次排列、间隔正常、可滚动，**没有重叠** |
| 封面背景 | 同屏观察 | 去掉 `elevation` 后压在玻璃后的阴影消失，封面背景干净 |
| 稳定性 | logcat | 无崩溃、无 ANR |

**AI 解读每一首歌**（豆包搜索 + DeepSeek）

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 联网检索在 App 内生效 | 填好两个 Key 后点「解读这首歌」，看 `SongInsight` 日志 | `bvid=BV1UGa961Ejt 检索到 6 条资料；提示词 5501 字` —— 豆包真的返回了 6 条并全部进了提示词 |
| 检索内容相关性 | 同一查询用 Node 直连接口 | 返回 MusicBrainz、VocaDB（歌词库）、B 站视频页等，`Summary` 每条约 300~1000 字 |
| 全链路不闪退 | 用**假** DeepSeek Key 触发 | 卡片显示「API Key 无效或已失效：Authentication Fails…」，进程存活；证明请求确实打到了服务端 |
| 卡片配色 | 深色模式观察 | 「重试」按钮与卡片同为深色玻璃，不融合的问题已消失 |

> 「模型成功返回解读并渲染」这一分支只验到错误分支（手上没有可用的 DeepSeek Key）。但两个分支共用同一段渲染代码，且该代码中引发过闪退的 `verticalScroll` 已删除。

**AI 设置页**

| 场景 | 验证方式 | 结果 |
|---|---|---|
| 四项齐全 | 设置 → AI 分析 | DeepSeek API Key / 模型 / 接口地址 / 豆包搜索 API Key |
| 配色（深色） | 截图 | 输入框为**提亮**填充，未选中 chip 清晰可辨（此前深色用黑、浅色用白，两边都看不见） |
| 配色（浅色） | 截图 | 输入框为**压暗**填充，各级文字可读 |
| Key 不落进代码 | 全盘扫描源码 / 文档 / APK 二进制 | 0 处命中；Key 只由用户在设置里填写，存本机 `SharedPreferences` |

---

## 许可证

**MIT** —— 见 [LICENSE](LICENSE)。

```
Copyright (c) 2026 Vocaloid List contributors
```

注意：许可证只覆盖**本仓库的源代码**。榜单数据来自 [biliboard.uk](https://biliboard.uk)，
封面与头像来自 B 站，这些内容的版权归各自原作者所有，不在本许可证范围内。