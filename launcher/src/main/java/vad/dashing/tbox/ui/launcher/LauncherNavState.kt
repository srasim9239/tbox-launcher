package vad.dashing.tbox.ui.launcher

/**
 * Turn-by-turn hints scraped from Yandex Navi / 2GIS
 * (YNarrows-style NotificationListener + optional Accessibility).
 */
enum class LauncherNavManeuver {
    Unknown,
    Straight,
    SlightLeft,
    Left,
    HardLeft,
    SlightRight,
    Right,
    HardRight,
    UTurnLeft,
    UTurnRight,
    EnterRoundabout,
    LeaveRoundabout,
    ForkLeft,
    ForkRight,
    ExitLeft,
    ExitRight,
    Ferry,
    Finish,
}

enum class LauncherNavAlert {
    None,
    Camera,
    Accident,
    RoadWorks,
    Other,
}

enum class LauncherNavTrafficLight {
    None,
    Red,
    Yellow,
    Green,
}

enum class LauncherNavSource {
    None,
    YandexNotification,
    YandexAccessibility,
    TwoGis,
}

data class LauncherNavState(
    val active: Boolean = false,
    val source: LauncherNavSource = LauncherNavSource.None,
    val maneuver: LauncherNavManeuver = LauncherNavManeuver.Unknown,
    /** Raw distance text from navi, e.g. "250 м" / "1,2 км". */
    val distanceText: String? = null,
    val street: String? = null,
    val alert: LauncherNavAlert = LauncherNavAlert.None,
    val trafficLight: LauncherNavTrafficLight = LauncherNavTrafficLight.None,
    /** Countdown seconds for traffic light, if any. */
    val trafficLightSec: String? = null,
    /** Speed limit from Yandex accessibility node, if available. */
    val speedLimitKmh: Int? = null,
    /** Exit number for roundabout / fork when known (accessibility). */
    val exitNumber: String? = null,
    val updatedAtMs: Long = 0L,
) {
    val hasHint: Boolean
        get() = active && (
            maneuver != LauncherNavManeuver.Unknown ||
                !distanceText.isNullOrBlank() ||
                !street.isNullOrBlank() ||
                alert != LauncherNavAlert.None ||
                trafficLight != LauncherNavTrafficLight.None ||
                speedLimitKmh != null
            )
}
