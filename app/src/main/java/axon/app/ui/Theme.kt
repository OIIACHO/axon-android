package axon.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6C4CF1),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF7C4DFF),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFE4E2EC),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFE4E2EC),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFB7B4C4),
    error = Color(0xFFFF5370)
)

@Composable
fun AxonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
