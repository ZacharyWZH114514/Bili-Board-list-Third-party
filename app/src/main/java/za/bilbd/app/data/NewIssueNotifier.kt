package za.bilbd.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.bilbd.app.R

/**
 * 「新一期周榜」通知。
 *
 * 不用无障碍服务保活：那是给辅助功能用的，拿来做后台常驻既不可靠（系统照样能杀）
 * 也会被应用商店判成恶意行为。这里走的是标准路径 —— WorkManager 定时唤醒一次、
 * 发一条普通通知，不需要任何特殊权限（Android 13+ 只要通知权限）。
 */
object NewIssueNotifier {

    const val CHANNEL_ID = "new_issue"
    private const val NOTIFICATION_ID = 1001

    /** Android 8+ 必须有 channel，重复建是幂等的 */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "新一期榜单",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "biliboard 周榜有新一期时提醒你"
            }
        )
    }

    fun notify(ctx: Context, ref: NewIssueChecker.IssueRef) {
        ensureChannel(ctx)

        // 点通知就是打开 App。这里用启动意图而不是写死 Activity 名：
        // 应用有 6 套图标别名，真正启用的那个每次都可能不一样。
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        val pi = launch?.let {
            PendingIntent.getActivity(
                ctx,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val period = when {
            ref.year > 0 && ref.week > 0 -> " · ${ref.year} 年第 ${ref.week} 周"
            ref.week > 0 -> " · 第 ${ref.week} 周"
            else -> ""
        }
        val title = "新一期周榜来了"
        val short = "第 ${ref.issueId} 期$period"
        val long = "$short\n点开看看这周谁上榜了。"

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_new_issue)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(long))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
        }
    }
}
