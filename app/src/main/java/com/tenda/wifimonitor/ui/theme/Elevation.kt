package com.tenda.wifimonitor.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Soft elevation scale. Deliberately restrained: a 1dp hairline already carries
 * the card edges, so shadows only need to add gentle depth on light surfaces
 * (in the dark theme they are intentionally almost invisible).
 */
object AppElevation {
    /** Standard content card. */
    val card = 1.dp

    /** Raised surface such as the KPI row or a dialog. */
    val raised = 4.dp
}
