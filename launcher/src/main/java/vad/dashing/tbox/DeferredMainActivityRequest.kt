package vad.dashing.tbox

import android.content.Context
import android.content.Intent

object DeferredMainActivityRequest {
    const val AFTER_MUSIC_WIDGET_PLAYER_LAUNCH_MS = 10_000L

    fun scheduleReturnAfterExternalPlayerLaunchIfMainWasVisible(
        context: Context,
        delayMs: Long = AFTER_MUSIC_WIDGET_PLAYER_LAUNCH_MS,
    ) {
        val app = context.applicationContext
        val intent = Intent(app, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_OPEN_MAIN_ACTIVITY
            putExtra(BackgroundService.EXTRA_OPEN_MAIN_DELAY_MS, delayMs.coerceAtLeast(0L))
        }
        try {
            app.startService(intent)
        } catch (_: Exception) {
            // ignore
        }
    }
}
