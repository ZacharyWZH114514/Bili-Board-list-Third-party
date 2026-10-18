package za.bilbd.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** 从封面里抽出来的主题色 */
data class CoverAccent(
    val primary: Color,
    val secondary: Color,
)

/**
 * 封面主题色抽取。
 *
 * 做法：用 Coil 取一张 48×48 的小图（Coil 有缓存，不会重复下载），
 * 把像素量化到 4 位/通道做直方图，按「鲜艳度 × 明度适中」加权选出主色，
 * 再挑一个色相离得够远的作为次色；颜色会做一次鲜艳度增强，保证当背景够看。
 */
object CoverAccentExtractor {

    private class Box(val value: CoverAccent?)

    private val cache = ConcurrentHashMap<String, Box>()

    fun cached(url: String): CoverAccent? = cache[url]?.value

    suspend fun extract(context: Context, url: String): CoverAccent? {
        cache[url]?.let { return it.value }
        val accent = runCatching { load(context, url) }.getOrNull()
        cache[url] = Box(accent)
        return accent
    }

    private suspend fun load(context: Context, url: String): CoverAccent? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SAMPLE, SAMPLE)
            .allowHardware(false) // 需要读像素，硬件位图读不了
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val image = (result as? SuccessResult)?.image as? BitmapImage ?: return null
        return fromBitmap(image.bitmap)
    }

    private fun fromBitmap(bitmap: Bitmap): CoverAccent? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // key 是量化后的颜色，value = [权重, r, g, b 的加权和]
        val buckets = HashMap<Int, FloatArray>()
        val hsv = FloatArray(3)
        for (p in pixels) {
            if (((p ushr 24) and 0xFF) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            if (value < 0.10f) continue // 死黑不要
            // 越鲜艳、明度越接近 0.6 的像素越有代表性
            val satWeight = sat * sat
            val lumWeight = (1f - abs(value - 0.62f) / 0.62f).coerceAtLeast(0f)
            val weight = (0.12f + satWeight) * (0.25f + lumWeight)
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = buckets.getOrPut(key) { FloatArray(4) }
            acc[0] += weight
            acc[1] += r * weight
            acc[2] += g * weight
            acc[3] += b * weight
        }
        if (buckets.isEmpty()) return null

        val ranked = buckets.values.sortedByDescending { it[0] }
        val primaryRaw = average(ranked.first())
        val primary = vivid(primaryRaw)

        // 次色：色相和主色差 40° 以上、权重最高的那个；找不到就由主色转个色相
        val primaryHue = hueOf(primaryRaw)
        val secondaryRaw = ranked.drop(1).firstOrNull {
            hueDistance(hueOf(average(it)), primaryHue) > 40f
        }
        val secondary = if (secondaryRaw != null) vivid(average(secondaryRaw)) else shiftHue(primary, 42f)

        return CoverAccent(
            primary = clampForBackground(primary),
            secondary = clampForBackground(secondary),
        )
    }

    private fun average(acc: FloatArray): Color {
        val w = acc[0].coerceAtLeast(0.0001f)
        return Color(
            red = (acc[1] / w / 255f).coerceIn(0f, 1f),
            green = (acc[2] / w / 255f).coerceIn(0f, 1f),
            blue = (acc[3] / w / 255f).coerceIn(0f, 1f),
        )
    }

    /** 提高饱和度，让颜色当背景时更「有颜色」 */
    private fun vivid(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb(c), hsv)
        hsv[1] = (hsv[1] * 1.35f).coerceIn(0f, 0.95f)
        if (hsv[1] < 0.22f) hsv[1] = 0.30f // 近乎灰的封面也给一点颜色
        hsv[2] = (hsv[2] * 1.05f).coerceIn(0.35f, 0.92f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** 背景用色：别太亮，否则压不住上面的白字 */
    private fun clampForBackground(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb(c), hsv)
        hsv[2] = hsv[2].coerceIn(0.30f, 0.78f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun shiftHue(c: Color, degrees: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb(c), hsv)
        hsv[0] = (hsv[0] + degrees) % 360f
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun hueOf(c: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb(c), hsv)
        return hsv[0]
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun argb(c: Color): Int = android.graphics.Color.argb(
        255,
        (c.red * 255f).toInt().coerceIn(0, 255),
        (c.green * 255f).toInt().coerceIn(0, 255),
        (c.blue * 255f).toInt().coerceIn(0, 255),
    )

    private const val SAMPLE = 48
}

/**
 * 取某个封面 URL 的主题色；url 为 null（不在曲目详情页）时返回 null。
 * 结果在进程内缓存，同一首歌来回切不会重复解码。
 */
@Composable
fun rememberCoverAccent(url: String?): CoverAccent? {
    val context = LocalContext.current
    var accent by remember(url) { mutableStateOf(url?.let { CoverAccentExtractor.cached(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            accent = null
        } else {
            accent = CoverAccentExtractor.cached(url) ?: CoverAccentExtractor.extract(context, url)
        }
    }
    return accent
}
