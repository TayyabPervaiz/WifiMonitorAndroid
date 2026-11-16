package com.tenda.wifimonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush

/**
 * Top-of-screen gradient. Reads from the active color scheme so it stays
 * readable in both light and dark themes (previously these screens hardcoded a
 * dark navy, which made light mode unreadable).
 */
@Composable
fun headerBrush(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.background,
    ),
)

/** Soft gradient used for feature/summary cards. */
@Composable
fun cardBrush(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainer,
    ),
)
