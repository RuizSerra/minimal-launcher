package dev.jaime.launcher.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

object Palette {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceRaised = Color(0xFF1B1B1B)
    val Primary = Color(0xFFFFFFFF) // pure white
    val Secondary = Color(0xFF9A9A9A)
    val Tertiary = Color(0xFF5C5C5C)
}

/** Colours, and all text in [font] (picked in launcher settings). */
@Composable
fun LauncherTheme(font: LauncherFont = LauncherFont.System, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember(font) { typographyIn(font.family(context)) }
    MaterialTheme(
        typography = typography,
        colorScheme = darkColorScheme(
            primary = Palette.Primary,
            onPrimary = Palette.Background,
            background = Palette.Background,
            onBackground = Palette.Primary,
            surface = Palette.Surface,
            onSurface = Palette.Primary,
            onSurfaceVariant = Palette.Secondary,
            surfaceContainerLow = Palette.SurfaceRaised,
        ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.Primary,
            LocalLauncherFont provides font,
            content = content,
        )
    }
}
