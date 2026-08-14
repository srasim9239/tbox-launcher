package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.BuildConfig

/**
 * Remounts the home Filament [io.github.sceneview.SceneView] after a GPU-heavy
 * fullscreen app (360 AVM) steals the EGL surface. Without this the car either
 * stays invisible (`ModelNode` rebuilt with `isVisible=false` while Compose
 * still thinks it is ready) or the camera snaps back to the top-down default.
 */
internal object LauncherCarSurfaceRecovery {
    private const val TAG = "LauncherCar3D"
    private const val MIN_RECOVER_INTERVAL_MS = 2_500L

    private val coveringPackages = setOf(
        "com.mengbo.avm",
        "com.mengbo.avmconfig",
    )

    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    @Volatile
    private var coveredBy: String? = null
    @Volatile
    private var paused = false
    @Volatile
    private var lastRecoverAtMs = 0L

    fun isCoveringPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in coveringPackages) return true
        return pkg.contains(".avm") || pkg.endsWith("avm")
    }

    fun onWindowPackage(packageName: String) {
        if (packageName.isBlank()) return
        if (isCoveringPackage(packageName)) {
            if (coveredBy != packageName) {
                coveredBy = packageName
                Log.w(TAG, "3D surface covered by $packageName")
            }
            return
        }
        if (coveredBy != null && packageName == BuildConfig.APPLICATION_ID) {
            val previous = coveredBy
            coveredBy = null
            recover("launcher window after $previous")
        }
    }

    fun onHomePaused() {
        paused = true
    }

    fun onHomeResumed() {
        val wasPaused = paused
        paused = false
        val cover = coveredBy
        if (cover != null) coveredBy = null
        if (wasPaused || cover != null) {
            recover(
                if (cover != null) "resume after $cover"
                else "resume after pause",
            )
        }
    }

    fun onFramesStalled() {
        recover("frames stalled")
    }

    private fun recover(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoverAtMs < MIN_RECOVER_INTERVAL_MS) {
            Log.w(TAG, "recover skipped (throttle) reason=$reason")
            return
        }
        lastRecoverAtMs = now
        val next = _epoch.value + 1
        _epoch.value = next
        Log.w(TAG, "recover SceneView epoch=$next reason=$reason")
    }
}
