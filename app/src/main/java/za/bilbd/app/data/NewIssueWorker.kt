package za.bilbd.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 定期看一眼「有没有新一轮榜单」，有就发通知。
 *
 * 只做**探测**，不负责下载数据：真正更新还是用户打开 App 时那套流程。
 * 原因很实在 —— 后台去解析 2.3MB 的内置快照、写增量文件，一旦出错很难救回来；
 * 而通知只需要一次十几 KB 的 issues 请求。
 */
class NewIssueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = SettingsStore(ctx)
        if (!settings.newIssueAlert.value) return Result.success()

        val store = LiveStore(ctx)
        val json = runCatching { BoardApi().issuesRaw(WEEKLY_BOARD_ID) }.getOrNull()
            ?: return Result.retry()

        val newest = NewIssueChecker.newest(json) ?: return Result.success()
        val last = store.lastNotifiedIssue

        // 第一次跑：只记住当前期号，不打扰用户
        if (last <= 0) {
            store.lastNotifiedIssue = newest.issueId
            return Result.success()
        }

        if (!NewIssueChecker.shouldNotify(newest, last)) return Result.success()

        store.lastNotifiedIssue = newest.issueId
        NewIssueNotifier.notify(ctx, newest)
        AppLog.add("提醒", "新一期周榜：第 ${newest.issueId} 期")
        return Result.success()
    }

    companion object {
        /** 周榜的 boardId */
        const val WEEKLY_BOARD_ID = 1
    }
}

/** 定时任务的登记/撤销。开关一关就撤掉，不留后台任务 */
object NewIssueScheduler {

    private const val WORK_NAME = "new_issue_check"

    /** 每 6 小时看一眼。周更的榜单绰绰有余，一天 4 次请求也不算负担 */
    private const val INTERVAL_HOURS = 6L

    fun sync(ctx: Context, enabled: Boolean) {
        val wm = runCatching { WorkManager.getInstance(ctx) }.getOrNull() ?: return
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<NewIssueWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
