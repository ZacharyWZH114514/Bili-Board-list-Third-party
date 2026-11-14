package za.bilbd.app.data

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 「后台常驻」用的无障碍服务。
 *
 * 说明白它是干什么的：**它不读屏幕、不动任何别的应用**，唯一作用是让系统认为
 * 本应用一直在提供服务，从而不容易在后台被清掉 —— 也就是俗称的保活。
 *
 * 这是用户明确要求加的可选项（默认关闭）。代价要讲清楚：
 *   · 需要用户自己去「设置 → 无障碍」里手动打开，这个页面本身就会劝退大部分人；
 *   · 系统仍然随时可以杀进程，保活只是降低概率；
 *   · 部分厂商 ROM 会把"非辅助用途的无障碍"当作可疑行为提示或自动关闭。
 *
 * 常规路径（不需要这个服务）是 WorkManager 定时检查，见 NewIssueWorker。
 * 打开本服务后，两条路会同时工作：无障碍服务维持进程存活，WorkManager 负责
 * 到点检查（存活时不再受 Doze 拖延）。
 */
class KeepAliveService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        AppLog.add("保活", "无障碍服务已连接（后台常驻优先）")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 刻意什么都不做：这只是保活，不监听用户操作
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        AppLog.add("保活", "无障碍服务已断开")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    companion object {
        /**
         * 判断本服务是否已在系统里启用。
         *
         * 实测踩了两个坑（Redmi / Android 14）：
         *   1. `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 对普通应用读出来是 null；
         *   2. `getEnabledAccessibilityServiceList` 有时拿到的条目 `resolveInfo` 为 null，
         *      只能靠 `id`（形如 "包名/类名"）来认。
         * 所以三条路都试一遍，任一命中就算开启，并把结果记进日志方便排查。
         */
        /**
         * 服务是否已连接。它跑在本进程里，`onServiceConnected` 一响就说明系统认可了，
         * 这比查系统设置/AccessibilityManager 都直接 —— 实测那两条路在 Redmi 上
         * 一条读不到（secure 设置返回 null）、一条返回空列表（resolveInfo 为 null）。
         */
        @Volatile
        private var connected = false

        fun isEnabled(ctx: android.content.Context): Boolean {
            if (connected) return true
            val pkg = ctx.packageName
            val cls = KeepAliveService::class.java.name
            val me = android.content.ComponentName(ctx, KeepAliveService::class.java)

            // 1) 系统设置里的白名单（多数 ROM 上应用读不到，读到了最准）
            val raw = runCatching {
                android.provider.Settings.Secure.getString(
                    ctx.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull().orEmpty()
            if (raw.isNotBlank()) {
                val listed = raw.split(':').any { part ->
                    val c = runCatching {
                        android.content.ComponentName.unflattenFromString(part.trim())
                    }.getOrNull()
                    c != null && c.packageName == pkg && c.className == cls
                }
                if (listed) return true
                // 有值但不含我们：说明确实没开，不必再问管理器
                return false
            }

            // 2) AccessibilityManager（对比 id 或 resolveInfo）
            val am = ctx.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                ?: return false
            val list = runCatching { am.getEnabledAccessibilityServiceList(0) }
                .getOrNull().orEmpty()
            return list.any { info ->
                val byId = runCatching { info.id }.getOrNull()
                    ?.let { it == "$pkg/$cls" || it.startsWith("$pkg/") } == true
                val byResolve = runCatching {
                    info.resolveInfo?.serviceInfo?.let { it.packageName == pkg && it.name == cls }
                }.getOrNull() == true
                byId || byResolve
            }
        }
    }
}
