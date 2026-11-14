package za.bilbd.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import za.bilbd.app.data.AppLog
import za.bilbd.app.data.NewIssueScheduler
import za.bilbd.app.data.SettingsStore
import za.bilbd.app.ui.AppIconState

/** 注册全局 Coil 图片加载器（封面来自 biliboard.uk 的图片代理，会 302 到 B 站图床） */
class BiliBoardApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 顺序很重要：先接上日志文件，再装崩溃捕获。
        // 反过来的话崩溃发生时还没有写入口，什么也留不下。
        runCatching { AppLog.attach(this) }
        installCrashHandler()
        // 读出用户当前选中的图标配色，供侧边导航栏、「关于」页显示
        AppIconState.refresh(this)
        // 按用户开关登记/撤销「新一期提醒」的定时任务。幂等，每次启动同步一次即可
        runCatching {
            NewIssueScheduler.sync(this, SettingsStore(this).newIssueAlert.value)
        }
    }

    /**
     * 把未捕获异常写进应用日志，再交还给系统默认处理。
     *
     * 为什么要用它：用户报「有时会闪退」，但崩在哪完全看不出来——release 包拿不到
     * logcat，崩溃后进程也没了。接上这个之后，崩溃栈会落进 files/app.log，
     * 下次启动「设置 → 日志」里就能看到并导出。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { AppLog.recordCrash(error) }
            previous?.uncaughtException(thread, error)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
