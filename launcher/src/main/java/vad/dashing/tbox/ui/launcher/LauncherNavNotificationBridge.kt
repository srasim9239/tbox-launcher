package vad.dashing.tbox.ui.launcher

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Applies YNarrows-style navigation extraction from a StatusBarNotification.
 */
object LauncherNavNotificationBridge {
    private const val TAG = "LauncherNavBridge"

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        if (!LauncherNavRepository.enabled) return
        when (sbn.packageName) {
            NaviNotificationParser.YANDEX_NAVI_PACKAGE -> handleYandex(context, sbn)
            NaviNotificationParser.TWO_GIS_PACKAGE -> handleTwoGis(sbn)
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        when (sbn.packageName) {
            NaviNotificationParser.YANDEX_NAVI_PACKAGE ->
                LauncherNavRepository.clearIfSource(LauncherNavSource.YandexNotification)
            NaviNotificationParser.TWO_GIS_PACKAGE ->
                LauncherNavRepository.clearIfSource(LauncherNavSource.TwoGis)
        }
    }

    private fun handleYandex(context: Context, sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification ?: return
            val remoteViews = RemoteViewsNavExtractor.extractRemoteViews(notification, "contentView")
                ?: RemoteViewsNavExtractor.extractRemoteViews(notification, "bigContentView")
            if (remoteViews != null) {
                val actions = RemoteViewsNavExtractor.getRemoteViewActions(
                    context,
                    remoteViews,
                    NaviNotificationParser.YANDEX_NAVI_PACKAGE,
                )
                val pairs = actions.map { it.viewIdName to it.prefixedValue }
                applyYandexPatch(NaviNotificationParser.parseYandexActions(pairs))
                return
            }
            // Fallback: plain extras (less detail, still useful).
            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (!title.isNullOrBlank() || !text.isNullOrBlank()) {
                LauncherNavRepository.applyYandexNotificationPatch {
                    copy(
                        distanceText = title?.trim()?.takeIf { it.isNotEmpty() } ?: distanceText,
                        street = text?.trim()?.takeIf { it.isNotEmpty() } ?: street,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Yandex nav parse failed: ${e.message}")
        }
    }

    private fun applyYandexPatch(parsed: NaviNotificationParser.ParsedYandexPatch) {
        LauncherNavRepository.applyYandexNotificationPatch {
            copy(
                maneuver = parsed.maneuver ?: maneuver,
                distanceText = parsed.distanceText ?: distanceText,
                street = when {
                    parsed.clearStreet -> null
                    parsed.street != null -> parsed.street
                    else -> street
                },
                alert = parsed.alert ?: alert,
                trafficLight = parsed.trafficLight ?: trafficLight,
                trafficLightSec = when {
                    parsed.trafficLight == LauncherNavTrafficLight.None -> null
                    parsed.trafficLightSec != null -> parsed.trafficLightSec
                    else -> trafficLightSec
                },
            )
        }
    }

    private fun handleTwoGis(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val (distance, street) = NaviNotificationParser.parseTwoGis(title, text)
            LauncherNavRepository.applyTwoGis(distance, street)
        } catch (e: Exception) {
            Log.w(TAG, "2GIS nav parse failed: ${e.message}")
        }
    }
}
