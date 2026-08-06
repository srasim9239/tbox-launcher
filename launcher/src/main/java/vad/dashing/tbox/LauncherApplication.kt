package vad.dashing.tbox

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.launcher.LauncherNavRepository

/**
 * Standalone HOME launcher process — no TBox UDP / trips / fuel pipeline.
 */
class LauncherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        val settings = SettingsManager(this)
        appScope.launch {
            runCatching {
                UniversalCanRepository.autoResolveModeOnStartup(settings, appScope)
            }.onFailure {
                Log.w(TAG, "CAN auto-bind failed", it)
            }
            runCatching {
                LauncherNavRepository.setEnabled(settings.launcherNavHintsEnabledFlow.first())
            }
        }
    }

    companion object {
        private const val TAG = "LauncherApp"
    }
}
