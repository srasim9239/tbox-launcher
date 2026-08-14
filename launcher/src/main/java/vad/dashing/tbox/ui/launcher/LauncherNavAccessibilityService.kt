package vad.dashing.tbox.ui.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional foreground scrape of Yandex Navi UI nodes (YNarrows NodeInfoForNavi).
 * Enable in system Accessibility settings when richer hints are needed.
 *
 * Freeform Back is handled in [goLauncherBack] (dock) — do not filter KEYCODE_BACK here:
 * consuming it broke overlay close and in-app navigation on single-activity apps.
 */
class LauncherNavAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: LauncherNavAccessibilityService? = null

        /**
         * Hardware-like Back via the accessibility global action. Works without INJECT_EVENTS,
         * which is why the dock Back button prefers it when the service is enabled.
         */
        fun dispatchGlobalBack(): Boolean {
            val service = instance ?: return false
            return runCatching {
                service.performGlobalAction(GLOBAL_ACTION_BACK)
            }.getOrDefault(false)
        }

        fun isConnected(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString().orEmpty()
                if (pkg.isNotEmpty()) {
                    LauncherCarSurfaceRecovery.onWindowPackage(pkg)
                }
                if (!LauncherNavRepository.enabled) return
                if (pkg.isNotEmpty() && pkg != NaviNotificationParser.YANDEX_NAVI_PACKAGE) {
                    LauncherNavRepository.clearIfSource(LauncherNavSource.YandexAccessibility)
                }
                return
            }

            if (!LauncherNavRepository.enabled) return

            val nodeInfo = event.source ?: return
            try {
                if (nodeInfo.packageName?.toString() != NaviNotificationParser.YANDEX_NAVI_PACKAGE) {
                    return
                }
                scrapeYandex(nodeInfo)
            } finally {
                nodeInfo.recycle()
            }
        } catch (_: Exception) {
            // Accessibility trees can be unstable; ignore per-event failures.
        }
    }

    private fun scrapeYandex(root: AccessibilityNodeInfo) {
        var maneuverDesc: String? = null
        var exitNumber: String? = null
        var distance: String? = null
        var street: String? = null
        var speedLimit: Int? = null
        var found = false

        root.findAccessibilityNodeInfosByViewId(
            "${NaviNotificationParser.YANDEX_NAVI_PACKAGE}:id/image_maneuverballoon_maneuver",
        )?.firstOrNull()?.let { node ->
            found = true
            maneuverDesc = node.contentDescription?.toString()
            node.recycle()
        }

        root.findAccessibilityNodeInfosByViewId(
            "${NaviNotificationParser.YANDEX_NAVI_PACKAGE}:id/exit_number_text",
        )?.firstOrNull()?.let { node ->
            exitNumber = node.text?.toString()
            node.recycle()
        }

        root.findAccessibilityNodeInfosByViewId(
            "${NaviNotificationParser.YANDEX_NAVI_PACKAGE}:id/text_maneuverballoon_distance",
        )?.firstOrNull()?.let { node ->
            found = true
            var dist = node.text?.toString().orEmpty()
            root.findAccessibilityNodeInfosByViewId(
                "${NaviNotificationParser.YANDEX_NAVI_PACKAGE}:id/text_maneuverballoon_metrics",
            )?.firstOrNull()?.let { metrics ->
                dist += metrics.text?.toString().orEmpty()
                metrics.recycle()
            }
            distance = dist.trim().takeIf { it.isNotEmpty() }
            node.recycle()
        }

        root.findAccessibilityNodeInfosByViewId(
            "${NaviNotificationParser.YANDEX_NAVI_PACKAGE}:id/text_nextstreet",
        )?.firstOrNull()?.let { node ->
            found = true
            street = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            node.recycle()
        }

        root.findAccessibilityNodeInfosByViewId(
            "${NaviNotificationParser.YANDEX_NAVI_PACKAGE}:id/text_speedlimit",
        )?.firstOrNull()?.let { node ->
            found = true
            speedLimit = NaviNotificationParser.parseSpeedLimit(node.text?.toString())
            node.recycle()
        }

        if (!found) return

        val maneuver = NaviNotificationParser.maneuverFromAccessibilityDescription(maneuverDesc)
        LauncherNavRepository.applyAccessibilityPatch {
            copy(
                maneuver = if (maneuver != LauncherNavManeuver.Unknown) maneuver else this.maneuver,
                distanceText = distance ?: distanceText,
                street = street ?: if (maneuver != LauncherNavManeuver.Unknown || distance != null) null else street,
                speedLimitKmh = speedLimit,
                exitNumber = exitNumber?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    override fun onInterrupt() = Unit
}
