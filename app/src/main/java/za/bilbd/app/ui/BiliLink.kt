package za.bilbd.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 「B站观看」：优先**直接打开 B 站 App**，没装才退回浏览器。
 *
 * 之前直接用 `openUri(https://www.bilibili.com/video/...)`，系统会按默认处理程序分流，
 * 结果多数机型都进了浏览器 —— 用户要的是点一下直接进 B 站看视频。
 *
 * 做法：先按 B 站 App 的 deeplink 协议 `bilibili://video/{bvid}` 显式指定包名去启动；
 * 包名试两个（国内版 / Google Play 国际版），都不行才退回 https。
 * 不去用 `queryIntentActivities` 预判是否安装 —— Android 11 起的包可见性会把它过滤掉，
 * 直接 try/catch 更可靠。
 */
object BiliLink {

    /** 国内手机版 */
    private const val PKG_CN = "tv.danmaku.bili"

    /** 平板 HD 版（很多人装的是这个，包名和手机版不同） */
    private const val PKG_HD = "tv.danmaku.bilibilihd"

    /** Google Play 国际版 */
    private const val PKG_GLOBAL = "com.bilibili.app.in"

    /**
     * 按优先级排：HD 版放最前 —— 装 HD 的用户（平板）显然想用 HD 看，
     * 手机上一般没装 HD，顺序对它就无影响。
     */
    val packages: List<String> = listOf(PKG_HD, PKG_CN, PKG_GLOBAL)

    /**
     * 候选包名，供测试与调试用。
     */
    fun candidates(): List<String> = packages

    /** B 站 App 的深链 */
    fun appUri(bvid: String): String = "bilibili://video/$bvid"

    /** 浏览器兜底用的网页地址 */
    fun webUri(bvid: String): String = "https://www.bilibili.com/video/$bvid"

    /**
     * 打开某个视频。
     *
     * @return true = 成功拉起（App 或浏览器），false = 连浏览器都没有
     */
    fun open(context: Context, bvid: String): Boolean {
        if (bvid.isBlank()) return false

        // 1) 先试 B 站 App：显式 setPackage，避免被别的应用接下
        for (pkg in packages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri(bvid))).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }

        // 2) 有些版本不认 bilibili:// 协议，但会接管 bilibili.com 的网页链接 ——
        //    这时仍然指定 B 站包名，把视频留在 App 里看，而不是丢给浏览器
        for (pkg in packages) {
            val webInApp = Intent(Intent.ACTION_VIEW, Uri.parse(webUri(bvid))).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(webInApp) }.isSuccess) return true
        }

        // 3) 都没装 → 退回网页（浏览器）
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(webUri(bvid))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(web) }.isSuccess
    }
}
