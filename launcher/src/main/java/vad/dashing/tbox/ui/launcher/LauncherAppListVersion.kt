package vad.dashing.tbox.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf

/**
 * Process-wide version of the installed-app list. Bumped on package install/remove so
 * Compose app lists (drawer, shortcut picker) reload instead of serving the stale cache.
 */
internal object LauncherAppListVersion {
    private const val TAG = "LauncherAppListVersion"

    private val _version = mutableIntStateOf(0)
    val version: Int get() = _version.intValue

    @Volatile
    private var receiverRegistered = false

    fun bump() {
        _version.intValue += 1
        Log.w(TAG, "app list version bumped to ${_version.intValue}")
    }

    fun ensurePackageChangeReceiver(context: Context) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            val appContext = context.applicationContext
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val pkg = intent?.data?.schemeSpecificPart.orEmpty()
                    if (pkg == appContext.packageName) return
                    bump()
                }
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                    appContext.registerReceiver(receiver, filter)
                }
                receiverRegistered = true
                Log.w(TAG, "package change receiver registered")
            }.onFailure { Log.w(TAG, "registerReceiver failed", it) }
        }
    }
}
