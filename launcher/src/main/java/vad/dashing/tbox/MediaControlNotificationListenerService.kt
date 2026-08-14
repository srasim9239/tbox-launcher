package vad.dashing.tbox

import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Required for MediaSession / media controls binding on the home player.
 */
class MediaControlNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.w(TAG, "notification listener connected")
    }

    private companion object {
        const val TAG = "LauncherMediaNls"
    }
}
