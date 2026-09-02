package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsOrbitStateTest {

    @Test
    fun wrapKeepsModestYawUnchanged() {
        assertEquals(45f, wrapSettingsYaw(45f), 0.001f)
        assertEquals(-90f, wrapSettingsYaw(-90f), 0.001f)
    }

    @Test
    fun wrapFoldsExtremeYaw() {
        assertEquals(40f, wrapSettingsYaw(3600f + 40f), 0.001f)
    }

    @Test
    fun pinchStaysInsideSettingsScaleRange() {
        val orbit = LauncherSettingsOrbitState()
        orbit.multiplyScale(10f)
        assertEquals(SETTINGS_USER_SCALE_MAX, orbit.scale, 0.001f)
        orbit.multiplyScale(0.01f)
        assertEquals(SETTINGS_USER_SCALE_MIN, orbit.scale, 0.001f)
    }

    @Test
    fun flingDecaysWithoutComposeState() {
        val orbit = LauncherSettingsOrbitState()
        orbit.applyFlingFromPxVelocity(SETTINGS_YAW_PX_PER_DEG * 200f)
        assertTrue(orbit.yawVelocityDegPerSec > 0f)
        val startYaw = orbit.yawDeg
        repeat(40) { orbit.tickFling(0.016f) }
        assertTrue(orbit.yawDeg > startYaw)
        assertTrue(kotlin.math.abs(orbit.yawVelocityDegPerSec) < 200f)
    }

    @Test
    fun weakFlickDoesNotFling() {
        val orbit = LauncherSettingsOrbitState()
        orbit.applyFlingFromPxVelocity(SETTINGS_YAW_PX_PER_DEG * 20f)
        assertEquals(0f, orbit.yawVelocityDegPerSec, 0.001f)
    }
}
