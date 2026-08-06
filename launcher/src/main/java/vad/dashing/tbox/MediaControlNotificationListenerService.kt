package vad.dashing.tbox

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import vad.dashing.tbox.ui.launcher.LauncherNavNotificationBridge

/**
 * Shared notification listener:
 * - required for MediaSession / media controls binding
 * - optionally scrapes Yandex Navi / 2GIS turn-by-turn (YNarrows-style)
 */
class MediaControlNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        LauncherNavNotificationBridge.onNotificationPosted(applicationContext, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        LauncherNavNotificationBridge.onNotificationRemoved(sbn)
    }
}
