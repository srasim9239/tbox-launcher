package vad.dashing.tbox.ui.launcher

internal const val SETTINGS_USER_SCALE_MIN = 1.20f
internal const val SETTINGS_USER_SCALE_MAX = 1.45f
/** Pixels of horizontal pan per degree of model yaw. */
internal const val SETTINGS_YAW_PX_PER_DEG = 4f
private const val SETTINGS_FLING_DECAY = 3.6f
internal const val SETTINGS_FLING_STOP_DEG_S = 14f
private const val SETTINGS_FLING_START_DEG_S = 80f
private const val SETTINGS_FLING_MAX_DEG_S = 720f

/**
 * Orbit/pinch state for the settings 3D car.
 * Written from the gesture detector and read from Filament's onFrame so a swipe
 * does not recompose the SceneView (that hitch is what made rotation feel sticky).
 */
class LauncherSettingsOrbitState(
    yawDeg: Float = 0f,
    scale: Float = SETTINGS_USER_SCALE_MIN,
) {
    @Volatile
    var yawDeg: Float = yawDeg

    @Volatile
    var scale: Float = scale

    @Volatile
    var yawVelocityDegPerSec: Float = 0f

    @Volatile
    var interacting: Boolean = false

    fun addYaw(deltaDeg: Float) {
        yawDeg = wrapSettingsYaw(yawDeg + deltaDeg)
    }

    fun multiplyScale(zoom: Float) {
        if (!zoom.isFinite() || zoom <= 0f) return
        scale = (scale * zoom).coerceIn(SETTINGS_USER_SCALE_MIN, SETTINGS_USER_SCALE_MAX)
    }

    fun tickFling(dt: Float) {
        if (interacting) return
        val speed = yawVelocityDegPerSec
        if (kotlin.math.abs(speed) < SETTINGS_FLING_STOP_DEG_S) {
            yawVelocityDegPerSec = 0f
            return
        }
        addYaw(speed * dt)
        yawVelocityDegPerSec = speed * kotlin.math.exp(-dt * SETTINGS_FLING_DECAY)
    }

    fun applyFlingFromPxVelocity(vxPxPerSec: Float) {
        if (!vxPxPerSec.isFinite()) {
            yawVelocityDegPerSec = 0f
            return
        }
        val degPerSec = (vxPxPerSec / SETTINGS_YAW_PX_PER_DEG)
            .coerceIn(-SETTINGS_FLING_MAX_DEG_S, SETTINGS_FLING_MAX_DEG_S)
        yawVelocityDegPerSec =
            if (kotlin.math.abs(degPerSec) < SETTINGS_FLING_START_DEG_S) 0f else degPerSec
    }

    fun beginInteraction() {
        interacting = true
        yawVelocityDegPerSec = 0f
    }

    fun endInteraction() {
        interacting = false
    }
}

internal fun wrapSettingsYaw(yaw: Float): Float =
    if (yaw > 3600f || yaw < -3600f) yaw % 360f else yaw
