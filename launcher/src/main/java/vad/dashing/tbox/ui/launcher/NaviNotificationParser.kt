package vad.dashing.tbox.ui.launcher

/**
 * Maps YNarrows-style RemoteViews action pairs from Yandex Navi notifications
 * into [LauncherNavState] fields. Pure logic — unit-tested without Android.
 */
object NaviNotificationParser {

    const val YANDEX_NAVI_PACKAGE = "ru.yandex.yandexnavi"
    const val TWO_GIS_PACKAGE = "ru.dublgis.dgismobile"

    private val STREET_NOISE = setOf(
        "Камера контроля скорости",
        "Направо",
        "Налево",
        "Почти на месте",
        "Кольцевое движение",
        "Turn right",
        "Turn left",
        "Almost there",
        "Roundabout",
    )

    data class ParsedYandexPatch(
        val maneuver: LauncherNavManeuver? = null,
        val distanceText: String? = null,
        val street: String? = null,
        val clearStreet: Boolean = false,
        val alert: LauncherNavAlert? = null,
        val trafficLight: LauncherNavTrafficLight? = null,
        val trafficLightSec: String? = null,
        val trafficLightVisible: Boolean? = null,
    )

    fun parseYandexActions(pairs: List<Pair<String, String>>): ParsedYandexPatch {
        var maneuver: LauncherNavManeuver? = null
        var distanceText: String? = null
        var street: String? = null
        var clearStreet = false
        var alert: LauncherNavAlert? = null
        var trafficLight: LauncherNavTrafficLight? = null
        var trafficLightSec: String? = null
        var trafficLightVisible: Boolean? = null
        var distanceExists = false
        var streetExists = false

        for ((viewId, prefixed) in pairs) {
            if (prefixed.length < 8) continue
            val prefix = prefixed.substring(0, 8)
            val value = if (prefixed.length > 8) prefixed.substring(8) else ""

            when (viewId) {
                "primaryIconTinted" -> {
                    if (prefix == "setImRe:") {
                        maneuver = maneuverFromYandexRes(value)
                    }
                }
                "primaryIcon" -> {
                    if (prefix == "setImRe:") {
                        alert = alertFromYandexRes(value)
                    }
                }
                "titleView" -> {
                    if (prefix == "setText:") {
                        distanceExists = true
                        distanceText = value.trim().takeIf { it.isNotEmpty() }
                    }
                }
                "descriptionView" -> {
                    if (prefix == "setText:") {
                        streetExists = true
                        val trimmed = value.trim()
                        street = when {
                            trimmed.isEmpty() || trimmed in STREET_NOISE -> null
                            else -> trimmed
                        }
                    }
                }
                "traffic_light_view" -> {
                    if (prefix == "SetVisi:") {
                        trafficLightVisible = value != View.GONE.toString() && value != "8"
                    }
                }
                "traffic_light_data", "traffic_light_data_expanded" -> {
                    when (prefix) {
                        "setBRes:" -> trafficLight = trafficLightFromYandexRes(value)
                        "setText:" -> {
                            trafficLightSec = value.trim().takeIf { it.isNotEmpty() && it != "0" }
                        }
                    }
                }
                "traffic_light_expanded" -> {
                    if (prefix == "setBRes:") {
                        trafficLight = trafficLightFromYandexRes(value)
                    }
                }
                "traffic_light_expanded_yellow" -> {
                    if (prefix == "SetVisi:" && value == "0") {
                        trafficLight = LauncherNavTrafficLight.Yellow
                    }
                }
            }
        }

        if (distanceExists && !streetExists) {
            clearStreet = true
            street = null
        }

        if (trafficLightVisible == false) {
            trafficLight = LauncherNavTrafficLight.None
            trafficLightSec = null
        }

        return ParsedYandexPatch(
            maneuver = maneuver,
            distanceText = distanceText,
            street = street,
            clearStreet = clearStreet,
            alert = alert,
            trafficLight = trafficLight,
            trafficLightSec = trafficLightSec,
            trafficLightVisible = trafficLightVisible,
        )
    }

    fun parseTwoGis(title: String?, text: String?): Pair<String?, String?> {
        val distance = title?.trim()?.takeIf { it.isNotEmpty() }
        val street = text?.trim()?.takeIf { it.isNotEmpty() }
        return distance to street
    }

    fun maneuverFromYandexRes(resName: String): LauncherNavManeuver = when (resName) {
        "notification_straight_sdl" -> LauncherNavManeuver.Straight
        "notification_slight_left_sdl" -> LauncherNavManeuver.SlightLeft
        "notification_left_sdl" -> LauncherNavManeuver.Left
        "notification_hard_left_sdl" -> LauncherNavManeuver.HardLeft
        "notification_slight_right_sdl" -> LauncherNavManeuver.SlightRight
        "notification_right_sdl" -> LauncherNavManeuver.Right
        "notification_hard_right_sdl" -> LauncherNavManeuver.HardRight
        "notification_uturn_left_sdl" -> LauncherNavManeuver.UTurnLeft
        "notification_uturn_right_sdl" -> LauncherNavManeuver.UTurnRight
        "notification_enter_roundabout_sdl" -> LauncherNavManeuver.EnterRoundabout
        "notification_leave_roundabout_sdl" -> LauncherNavManeuver.LeaveRoundabout
        "notification_fork_left_sdl" -> LauncherNavManeuver.ForkLeft
        "notification_fork_right_sdl" -> LauncherNavManeuver.ForkRight
        "notification_exit_left_sdl" -> LauncherNavManeuver.ExitLeft
        "notification_exit_right_sdl" -> LauncherNavManeuver.ExitRight
        "notification_board_ferry_sdl" -> LauncherNavManeuver.Ferry
        "notification_finish_sdl" -> LauncherNavManeuver.Finish
        else -> LauncherNavManeuver.Unknown
    }

    fun alertFromYandexRes(resName: String): LauncherNavAlert = when (resName) {
        "road_alerts_camera_32" -> LauncherNavAlert.Camera
        "road_alerts_accident_32" -> LauncherNavAlert.Accident
        "road_alerts_road_works_32" -> LauncherNavAlert.RoadWorks
        "road_alerts_other_32" -> LauncherNavAlert.Other
        else -> LauncherNavAlert.None
    }

    fun trafficLightFromYandexRes(resName: String): LauncherNavTrafficLight = when (resName) {
        "traffic_light_background_red_main",
        "traffic_light_background_red_counter",
        -> LauncherNavTrafficLight.Red
        "traffic_light_background_green_main",
        "traffic_light_background_green_counter",
        -> LauncherNavTrafficLight.Green
        "traffic_light_background_yellow_main",
        "traffic_light_background_yellow_counter",
        -> LauncherNavTrafficLight.Yellow
        else -> LauncherNavTrafficLight.None
    }

    fun maneuverFromAccessibilityDescription(description: String?): LauncherNavManeuver {
        val d = description?.trim()?.lowercase().orEmpty()
        if (d.isEmpty()) return LauncherNavManeuver.Unknown
        return when {
            "прямо" in d || "straight" in d -> LauncherNavManeuver.Straight
            "резко налево" in d || "hard left" in d || "sharp left" in d -> LauncherNavManeuver.HardLeft
            "почти налево" in d || "slight left" in d -> LauncherNavManeuver.SlightLeft
            "налево" in d || "turn left" in d || "left" == d -> LauncherNavManeuver.Left
            "резко направо" in d || "hard right" in d || "sharp right" in d -> LauncherNavManeuver.HardRight
            "почти направо" in d || "slight right" in d -> LauncherNavManeuver.SlightRight
            "направо" in d || "turn right" in d || "right" == d -> LauncherNavManeuver.Right
            "разворот" in d || "u-turn" in d || "uturn" in d -> {
                if ("прав" in d || "right" in d) LauncherNavManeuver.UTurnRight
                else LauncherNavManeuver.UTurnLeft
            }
            "кольц" in d || "roundabout" in d -> LauncherNavManeuver.EnterRoundabout
            "паром" in d || "ferry" in d -> LauncherNavManeuver.Ferry
            "финиш" in d || "finish" in d || "прибыл" in d || "arrived" in d -> LauncherNavManeuver.Finish
            "съезд" in d && ("лев" in d || "left" in d) -> LauncherNavManeuver.ExitLeft
            "съезд" in d && ("прав" in d || "right" in d) -> LauncherNavManeuver.ExitRight
            else -> LauncherNavManeuver.Unknown
        }
    }

    fun parseSpeedLimit(text: String?): Int? {
        val digits = text?.filter { it.isDigit() }.orEmpty()
        return digits.toIntOrNull()?.takeIf { it in 5..250 }
    }
}

/** Mirror of android.view.View.GONE without Android dependency in unit tests. */
private object View {
    const val GONE = 8
}
