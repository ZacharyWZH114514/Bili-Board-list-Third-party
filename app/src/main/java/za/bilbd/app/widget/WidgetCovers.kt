package za.bilbd.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import za.bilbd.app.data.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 小组件的封面缓存。
 *
 * 为什么要在 App 里先把封面下下来：
 *   小组件渲染时**不能联网** —— 桌面上的东西卡一下，用户感受比 App 卡还糟。
 *   Glance 的 Image 也只会读本地 URI，不会自己发 HTTP 请求。
 * 所以要提前把前几首的封面抓成小图存进 cacheDir，小组件只负责读文件。
 */
object WidgetCovers {

    /** 存进缓存的最长边。小组件上封面最大 40dp（420dpi ≈ 105px），160 已经足够清晰 */
    private const val TARGET_PX = 160

    /** 内存缓存（同一个进程里 Activity/小组件都会读，避免反复解码） */
    private val mem = LruCache<String, Bitmap>(16)

    fun dir(context: Context): File =
        File(context.applicationContext.cacheDir, "widget_covers").apply { mkdirs() }

    fun fileFor(context: Context, bvid: String): File = File(dir(context), "$bvid.jpg")

    /**
     * 把 [snapshot] 里用到的封面同步下来。已经是文件了就跳过。
     *
     * 放在 suspend 之外由调用方起协程，这里只做阻塞下载（最多 8 张、每张几十 KB）。
     */
    suspend fun ensure(context: Context, snapshot: WidgetSnapshot) {
        // 必须切到 IO 线程：调用方是 viewModelScope（主线程），
        // 直接在这里做 HttpURLConnection 会抛 NetworkOnMainThreadException，
        // 被 runCatching 一吞，封面就永远静悄悄地下不来（4.9 就是这个毛病）
        withContext(Dispatchers.IO) {
            for (row in snapshot.rows) {
                if (row.bvid.isBlank()) continue
                val f = fileFor(context, row.bvid)
                if (f.exists() && f.length() > 0) continue
                runCatching { download(row.bvid, f) }
            }
        }
    }

    private fun download(bvid: String, out: File) {
        // 和 App 里同一个图片接口：biliboard 的代理会 302 到 B 站图床
        val url = URL("https://biliboard.uk/api/provider/biliimg/bvid/$bvid")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BiliBoard-widget")
        }
        try {
            if (conn.responseCode !in 200..299) return
            val raw = conn.inputStream.use { it.readBytes() }
            val scaled = decodeScaled(raw) ?: return
            val tmp = File(out.parentFile, out.name + ".tmp")
            tmp.outputStream().use { os ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, os)
            }
            if (out.exists()) out.delete()
            tmp.renameTo(out)
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > TARGET_PX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return if (maxOf(bmp.width, bmp.height) <= TARGET_PX) bmp else {
            val ratio = TARGET_PX.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * ratio).toInt().coerceAtLeast(1),
                (bmp.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== bmp) bmp.recycle() }
        }
    }

    /** 小组件渲染时取图；读不到返回 null（宁可没有图，也别显示占位方块） */
    fun load(context: Context, bvid: String): Bitmap? {
        if (bvid.isBlank()) return null
        mem.get(bvid)?.let { return it }
        val f = fileFor(context, bvid)
        if (!f.exists() || f.length() == 0L) return null

        // 关键：按目标尺寸降采样再解码。
        // 之前直接 decodeFile 原图，8 张 bitmap 加起来 20MB，超过系统给 RemoteViews 的
        // 15MB 上限 —— 桌面只会显示「Can't show content」，日志里是
        // IllegalArgumentException: RemoteViews ... exceeds maximum bitmap memory usage
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > TARGET_PX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // 封面不需要 alpha，省一半内存
        }
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath, opts) }.getOrNull() ?: return null
        mem.put(bvid, bmp)
        return bmp
    }
}
