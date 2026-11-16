package com.tenda.wifimonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One shape scale for the whole app. Components should read these from
 * [MaterialTheme.shapes] instead of hardcoding a corner radius.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully-rounded radius for pills/badges. */
val PillShape = RoundedCornerShape(percent = 50)
