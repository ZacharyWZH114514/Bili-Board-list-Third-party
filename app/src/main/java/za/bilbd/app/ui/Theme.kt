package za.bilbd.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6FB5),
    onPrimary = Color(0xFF3A0021),
    primaryContainer = Color(0xFF5B1F3F),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFC4B4FF),
    onSecondary = Color(0xFF23134F),
    secondaryContainer = Color(0xFF3A2A6B),
    onSecondaryContainer = Color(0xFFE7DEFF),
    tertiary = Color(0xFFFFD166),
    onTertiary = Color(0xFF3D2E00),
    background = Color(0xFF14121C),
    onBackground = Color(0xFFE8E3F0),
    surface = Color(0xFF1B1826),
    onSurface = Color(0xFFE8E3F0),
    surfaceVariant = Color(0xFF2A2635),
    onSurfaceVariant = Color(0xFFBFB8CC),
    surfaceContainer = Color(0xFF221E2E),
    surfaceContainerHigh = Color(0xFF2A2635),
    outline = Color(0xFF4A4458),
    outlineVariant = Color(0xFF332E3F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3025F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3E0020),
    secondary = Color(0xFF5B4B9E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7DEFF),
    onSecondaryContainer = Color(0xFF170045),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    background = Color(0xFFFDF7FB),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEDE0E6),
    onSurfaceVariant = Color(0xFF4E4449),
    surfaceContainer = Color(0xFFF5EDF2),
    surfaceContainerHigh = Color(0xFFEFE5EB),
    outline = Color(0xFF7F7478),
    outlineVariant = Color(0xFFD0C4C9),
)

/** 名次/热度相关的强调色 */
object Accent {
    val gold = Color(0xFFFFC53D)
    val silver = Color(0xFFC9D2DE)
    val bronze = Color(0xFFD08B5B)
    val up = Color(0xFF4CD07D)
    val down = Color(0xFFFF6B6B)
    val `new` = Color(0xFF64B5F6)
}

@Composable
fun BiliBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
