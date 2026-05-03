package com.fmz.spenit.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = OnPrimary,
    primaryContainer = PurplePrimaryDark,
    secondary = TealSecondary,
    secondaryContainer = TealSecondaryDark,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainer,
    error = ErrorRed,
    onError = DarkBackground,
    outline = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = OnPrimary,
    primaryContainer = PurplePrimaryLight,
    secondary = TealSecondary,
    secondaryContainer = PurplePrimaryLight,
    background = LightBackground,
    onBackground = DarkBackground,
    surface = LightSurface,
    onSurface = DarkBackground,
    surfaceVariant = LightSurfaceVariant,
    error = Color(0xFFB00020),
    outline = Color(0xFF79747E)
)

@Composable
fun SpenItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
