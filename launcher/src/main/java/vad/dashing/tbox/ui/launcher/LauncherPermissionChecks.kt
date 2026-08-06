package vad.dashing.tbox.ui.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/** True when [component] is present in Settings.Secure enabled_notification_listeners. */
internal fun isNotificationListenerEnabled(
    context: Context,
    component: ComponentName,
): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ).orEmpty()
    if (flat.isBlank()) return false
    return flat.split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .any { it == component }
}

/**
 * Opens a system settings screen (notification access, accessibility, unknown sources...)
 * inside the shared freeform zone so it floats next to other freeform apps instead of
 * covering the launcher fullscreen. Falls back to a plain fullscreen start when the
 * freeform launch is not possible.
 */
internal fun launchSystemSettingsInFreeform(context: Context, intent: Intent): Boolean {
    val freeform = runCatching {
        tryLaunchSettingsIntentInFreeform(context, intent)
    }.getOrDefault(false)
    if (freeform) return true
    Log.w("LauncherSettingsLaunch", "freeform settings launch failed, fallback fullscreen action=${intent.action}")
    return runCatching {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
