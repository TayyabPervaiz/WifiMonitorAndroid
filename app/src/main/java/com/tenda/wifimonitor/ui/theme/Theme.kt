package com.tenda.wifimonitor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF00312C),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Accent,
    onSecondary = Color(0xFF3D2800),
    secondaryContainer = Color(0xFF4E3600),
    onSecondaryContainer = Color(0xFFFFDEA3),
    tertiary = Purple,
    onTertiary = Color(0xFF17005A),
    error = Danger,
    onError = Color(0xFF4A0018),
    errorContainer = DangerDark,
    onErrorContainer = Color(0xFFFFDADF),
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceVariant,
    surfaceContainerHighest = SurfaceElevated,
    outline = Hairline,
    outlineVariant = Color(0xFF1A2436),
    inverseSurface = OnBackground,
    inverseOnSurface = Background,
    inversePrimary = PrimaryDark,
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F5EA),
    onPrimaryContainer = Color(0xFF00372F),
    secondary = AccentDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7B8),
    onSecondaryContainer = Color(0xFF3D2800),
    tertiary = Color(0xFF6B5BD6),
    onTertiary = Color.White,
    error = DangerDark,
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF400013),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE4EAF3),
    outline = Color(0xFFB9C3D4),
    outlineVariant = LightHairline,
    inverseSurface = LightOnBackground,
    inverseOnSurface = LightBackground,
    inversePrimary = Primary,
    scrim = Color(0xFF000000),
)

@Composable
fun WifiMonitorTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalView.current.context)
            else dynamicLightColorScheme(LocalView.current.context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(
                LocalSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
                content = content,
            )
        },
    )
}
