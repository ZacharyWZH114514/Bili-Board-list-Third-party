package za.bilbd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 开源许可声明。
 *
 * 这个应用用了多个 Apache-2.0 / MIT 的库，按许可与惯例都应在应用内列出。
 * 只列**直接依赖**——传递依赖有几十个，全列出来对用户没有意义。
 */
private data class License(val name: String, val license: String, val url: String)

private val LIBRARIES = listOf(
    License("Jetpack Compose", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    License(
        "AndroidX（Core / Activity / Lifecycle）",
        "Apache-2.0",
        "https://developer.android.com/jetpack/androidx",
    ),
    License(
        "Material Components for Android",
        "Apache-2.0",
        "https://github.com/material-components/material-components-android",
    ),
    License("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    License("OkHttp", "Apache-2.0", "https://square.github.io/okhttp/"),
    License("Kotlin / kotlinx.coroutines", "Apache-2.0", "https://kotlinlang.org/"),
    License("Robolectric（仅测试用）", "MIT", "https://robolectric.org/"),
)

@Composable
fun LicensesCard(dark: Boolean, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    GlassSurface(
        dark = dark,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionTitle("开源许可")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "本应用使用了以下开源项目，在此致谢。点任意一项可打开它的主页。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LIBRARIES.forEach { lib ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { runCatching { uriHandler.openUri(lib.url) } }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = lib.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = lib.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = lib.license,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "榜单数据来自 biliboard.uk 的公开接口，版权归原作者与各 P主 所有；" +
                "本应用仅作浏览用途，不存储音频，也不提供下载。" +
                "封面图经 biliboard.uk 的图片代理取自 B 站。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
