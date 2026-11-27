plugins {
    // AGP 9 起内置 Kotlin 支持，不能再应用 org.jetbrains.kotlin.android
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "za.bilbd.app"
    // 本机安装的是 android-37.1，因此需要同时声明小版本（AGP 9 起支持）
    compileSdk = 37
    compileSdkMinor = 1
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "za.bilbd.app"
        minSdk = 26
        targetSdk = 37
        // versionName 是用户在「关于」页看到的版本号；
        // versionCode 只给系统判断「哪个更新」，必须严格递增（1.145 那版是 1145，所以这里取 1200）。
        // 代码里不要再硬编码版本号，统一读 BuildConfig.VERSION_NAME。
        versionCode = 6200
        versionName = "6.2"
    }

    buildFeatures {
        // 让启动日志等处能读到 BuildConfig.VERSION_NAME，避免版本号散落在多个文件里
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // 开了 haze 之后包体是首要约束：R8 会把 Compose/Coil/haze 未用到的部分全部裁掉
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 使用 debug 签名，保证产出的 APK 可直接安装验证
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests {
            // 让单测能读到 android 资源，并且未被 mock 的 android 方法返回默认值
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    // 液体玻璃：真实折射（AGSL 采样背景）
    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-glass:2.0.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- 单元测试 ----
    testImplementation("junit:junit:4.13.2")
    // android.jar 里的 org.json 只是抛 "Stub!" 的空壳，
    // 换成真正的实现才能测 Parsers 这类解析逻辑
    testImplementation("org.json:json:20240303")

    // ---- Compose 界面测试（跑在 JVM 上，不需要模拟器）----
    // Robolectric 让 Compose 能在普通 JVM 里渲染，这正是为了绕开
    // 「模拟器不稳定 → 布局问题只能靠肉眼」这个死结
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ---------------------------------------------------------------------------
// 构建期硬规则：玻璃组件的明暗必须由主题决定，禁止在调用点写死。
//
// 这个错误已经犯过三次（设置页三行 / AI 按钮 / 图标选择框），每次的表现都是
// 「颜色和背景不融合」——不会崩、不会报警，只有肉眼能发现。编译器管不了，
// 所以在这里用构建任务兜住：扫描 UI 源码里有没有 `dark = true/false` 字面量，
// 有就直接让构建失败，并打印出具体文件和行号。
//
// 正确写法：不要传 dark（GlassSurface / CoverImage / GlassBackdrop 的默认值
// 已经是 isDarkGlass()），或者让调用方的 dark 参数一路从 isDarkGlass() 传下来。
// ---------------------------------------------------------------------------

val uiSourceDir = file("src/main/java/za/bilbd/app/ui")

val guardGlassTheme by tasks.registering {
    group = "verification"
    description = "禁止在 UI 代码里写死玻璃明暗（dark = true/false）"
    inputs.dir(uiSourceDir).withPropertyName("uiSources")

    doLast {
        val literal = Regex("""\bdark\s*=\s*(true|false)\b""")
        val offenders = mutableListOf<String>()

        uiSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .forEach { file ->
                file.readLines().forEachIndexed { index, raw ->
                    val trimmed = raw.trim()
                    // 跳过注释行，避免规则说明自己把自己判违规
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    if (literal.containsMatchIn(raw)) {
                        offenders += "${file.name}:${index + 1}  $trimmed"
                    }
                }
            }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("检测到写死的玻璃明暗值：")
                    offenders.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("玻璃配色必须跟随主题。请删掉该参数：")
                    appendLine("  · GlassSurface / CoverImage / GlassBackdrop 的 dark 默认值已经是 isDarkGlass()")
                    appendLine("  · 需要往下传时，让它一路从 isDarkGlass() 来，不要写字面量")
                }
            )
        }
    }
}

// 所有构建前先跑这条规则（preBuild 是 AGP 为每个变体都建立的任务）
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(guardGlassTheme)
}

// ---------------------------------------------------------------------------
// 第二条硬规则：每个页面都必须让位给「覆盖层」栏。
//
// 顶栏/底栏现在是浮在内容之上的覆盖层，页面必须自己消费 LocalBarsInsets：
//   · LazyColumn  → contentPadding = barsPadded(...)
//   · verticalScroll 的 Column → .barsInset()（必须写在 verticalScroll 之后）
//   · 不在滚动容器里的固定头部 → padding(top = LocalBarsInsets.current.top)
//
// 漏掉这一条不会报错、不会崩，只会让首尾内容被玻璃盖住——只有用户能发现。
// 曲目详情页就是这么漏掉的（v3.1 之前封面被标题栏盖掉一截、最后一张卡片压在底栏下面）。
// ---------------------------------------------------------------------------

val guardScreenInsets by tasks.registering {
    group = "verification"
    description = "每个 *Screen.kt 都必须消费 LocalBarsInsets（barsPadded / barsInset / LocalBarsInsets）"
    inputs.dir(uiSourceDir).withPropertyName("uiSources")

    doLast {
        val consumers = Regex("""\bbarsPadded\s*\(|\bbarsInset\s*\(|\bLocalBarsInsets\b""")
        // 新手引导是「提前返回」的独立全屏页面：那时两条栏都还没渲染，没有要让位的对象
        val exempt = setOf("OnboardingScreen.kt")
        val offenders = mutableListOf<String>()

        uiSourceDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Screen.kt") && it.name !in exempt }
            .sortedBy { it.name }
            .forEach { file ->
                // 去掉注释行再判断，避免注释里提一句 barsPadded 就蒙混过关
                val code = file.readLines()
                    .filterNot { line ->
                        val t = line.trim()
                        t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                    }
                    .joinToString("\n")
                if (!consumers.containsMatchIn(code)) offenders += file.name
            }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("这些页面没有给覆盖层栏让位：")
                    offenders.forEach { appendLine("  · $it") }
                    appendLine()
                    appendLine("栏是覆盖层，页面必须自己消费 LocalBarsInsets，否则首尾内容会被玻璃盖住：")
                    appendLine("  · LazyColumn → contentPadding = barsPadded(base)")
                    appendLine("  · verticalScroll 的 Column → Modifier.barsInset()（写在 verticalScroll 之后）")
                    appendLine("  · 固定头部 → padding(top = LocalBarsInsets.current.top)")
                    appendLine("确实不需要让位的页面，加进本规则的 exempt 白名单并写明理由。")
                }
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(guardScreenInsets)
}

// ---------------------------------------------------------------------------
// 第三条硬规则：单元测试不过，就出不了 release 包。
//
// 这条是「约定」和「保证」的分界：把它写进构建图之后，
// 忘记跑测试不会导致问题悄悄发出去，而是直接构建失败。
// 只挂 release——debug 迭代时不必每次都等测试。
// ---------------------------------------------------------------------------
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}

// ---------------------------------------------------------------------------
// 测试 JVM 的环境
//
// Robolectric 会把下载锁（.robolectric-download-lock）和 android-all 缓存
// 写到 user.home 下。这台机器的 C:\Users\wuzih 对构建进程不可写，
// 会直接抛 "Couldn't create lock file" —— 所以把 user.home 指到工程内部。
// 顺带也避免污染真实用户目录。
// ---------------------------------------------------------------------------
tasks.withType<Test>().configureEach {
    val testHome = File(project.layout.buildDirectory.asFile.get(), "test-home")
    testHome.mkdirs()

    systemProperty("user.home", testHome.absolutePath)
    // Robolectric 的 android-all 包走 Maven Central；这里显式给仓库，
    // 免得它内部再去猜（本机网络对境外源时好时坏）
    systemProperty("robolectric.dependency.repo.id", "central")
    systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
    environment("ANDROID_USER_HOME", testHome.absolutePath)

    // 让失败信息直接出现在构建日志里，不用再去翻 XML 报告
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}


// ---------------------------------------------------------------------------
