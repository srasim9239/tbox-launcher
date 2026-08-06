package vad.dashing.tbox

/**
 * Minimal widget config shape used by [SharedMediaControlService] helpers.
 * Full floating-dashboard config lives in TBox Monitor only.
 */
data class FloatingDashboardWidgetConfig(
    val dataKey: String = "",
    val mediaPlayers: List<String> = emptyList(),
    val mediaSelectedPlayer: String = "",
)
