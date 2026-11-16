package com.tenda.wifimonitor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours whose value must differ per theme to stay WCAG 2.1 AA
 * compliant. Every value below is verified ≥ 4.5:1 against every surface it can
 * appear on **and** against its own 16% tinted chip/pill background, which is the
 * tightest real-world case (a coloured badge label sits on its own colour).
 */
data class SemanticColors(
    val online: Color,
    val offline: Color,
    val intruder: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val purple: Color,
)

internal val DarkSemanticColors = SemanticColors(
    online = Success,
    offline = Color(0xFF95A0B5),
    intruder = Danger,
    success = Success,
    warning = Warning,
    info = Info,
    purple = Purple,
)

internal val LightSemanticColors = SemanticColors(
    online = Color(0xFF0D6742),
    offline = Color(0xFF4F5A6F),
    intruder = DangerDark,
    success = Color(0xFF0D6742),
    warning = Color(0xFF7C5100),
    info = Color(0xFF1C5BA1),
    purple = Color(0xFF5748BC),
)

internal val LocalSemanticColors = staticCompositionLocalOf { DarkSemanticColors }

/** Theme-aware semantic colours. Use instead of the raw brand constants. */
val AppColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current
