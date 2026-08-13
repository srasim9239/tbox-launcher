package vad.dashing.tbox.ui.launcher

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Dock Back needs [LauncherNavAccessibilityService] so the system can inject
 * KEYCODE_BACK (apps cannot hold INJECT_EVENTS). With [WRITE_SECURE_SETTINGS]
 * we enable the service ourselves — same as the ADB step in docs/ADB_PERMISSIONS_RU.md.
 */
internal object LauncherAccessibilityHelper {
    private const val TAG = "LauncherA11yHelper"

    fun navBackServiceComponent(context: Context): ComponentName =
        ComponentName(context, LauncherNavAccessibilityService::class.java)

    fun isNavBackServiceEnabled(context: Context): Boolean {
        val flat = navBackServiceComponent(context).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    /**
     * Ensure the nav-back accessibility service is listed and accessibility is on.
     * Safe to call repeatedly; preserves other already-enabled services.
     */
    fun ensureNavBackServiceEnabled(context: Context): Boolean {
        val flat = navBackServiceComponent(context).flattenToString()
        val cr = context.contentResolver
        return runCatching {
            val current = Settings.Secure.getString(
                cr,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val parts = current.split(':').filter { it.isNotBlank() }
            val already = parts.any { it.equals(flat, ignoreCase = true) }
            if (!already) {
                val next = if (parts.isEmpty()) flat else parts.joinToString(":") + ":" + flat
                val ok = Settings.Secure.putString(
                    cr,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    next,
                )
                Log.w(TAG, "enable a11y service ok=$ok list=$next")
            } else {
                Log.w(TAG, "a11y service already enabled: $flat")
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            isNavBackServiceEnabled(context)
        }.onFailure {
            Log.w(TAG, "ensureNavBackServiceEnabled failed (need WRITE_SECURE_SETTINGS?)", it)
        }.getOrDefault(false)
    }
}
