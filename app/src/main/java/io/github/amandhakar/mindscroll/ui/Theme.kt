package io.github.amandhakar.mindscroll.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Violet = Color(0xFF6C5CE7)

/** Rounder than the Material defaults: the app is about a soft little character. */
private val MindscrollShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MindscrollTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Without dynamic colour: a violet accent on softly tinted surfaces, not stark white/black.
        dark -> darkColorScheme(
            primary = Color(0xFFB4A9FF),
            background = Color(0xFF13111A),
            surface = Color(0xFF13111A),
            surfaceContainerLow = Color(0xFF1C1925),
            outlineVariant = Color(0xFF3A3648),
        )
        else -> lightColorScheme(
            primary = Violet,
            background = Color(0xFFFBF9FF),
            surface = Color(0xFFFBF9FF),
            surfaceContainerLow = Color(0xFFF3EFFA),
            outlineVariant = Color(0xFFDCD6E8),
        )
    }
    MaterialTheme(colorScheme = colors, shapes = MindscrollShapes, content = content)
}
