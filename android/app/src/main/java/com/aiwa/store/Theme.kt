package com.aiwa.store
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE08A6C), onPrimary = Color(0xFF1F0F09), primaryContainer = Color(0xFF4A2A20), onPrimaryContainer = Color(0xFFFFDCCF),
    secondary = Color(0xFF26C6B0), surface = Color(0xFF17171E), onSurface = Color(0xFFEDEDF4), surfaceVariant = Color(0xFF242430),
    onSurfaceVariant = Color(0xFFB4B4C4), outline = Color(0xFF8C8CA0), error = Color(0xFFFF8A80),
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFFB5593E), onPrimary = Color.White, primaryContainer = Color(0xFFFFE1D6), onPrimaryContainer = Color(0xFF3A1409),
    secondary = Color(0xFF138A7B), surface = Color(0xFFFAFAFD), onSurface = Color(0xFF1B1B22), surfaceVariant = Color(0xFFEDEDF4),
    onSurfaceVariant = Color(0xFF55556A), outline = Color(0xFF8C8CA0),
)

/** The look of every sheet of the app (menus, lists, the video and proposal windows): one accent (the orange of Claude), dark or light as the phone is. */
@Composable
fun AiwaTheme(content: @Composable () -> Unit) {
    DarkSheetTheme(content)      // dark and compact whatever the phone's theme, like the menus of the widget (the old light / dark scheme above is not used any more)
}
