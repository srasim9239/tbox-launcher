package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared reveal for vehicle-settings overlay ↔ launcher left column.
 * [revealProgress] 0→1 drives window width, car camera/scale, settings slide-in.
 */
internal object LauncherVehicleSettingsUiState {
    var open by mutableStateOf(false)
        private set

    /** 0 = collapsed (sidebar), 1 = fully expanded (car + settings). */
    var revealProgress by mutableFloatStateOf(0f)

    fun markOpen() {
        open = true
    }

    fun markClosed() {
        open = false
        revealProgress = 0f
    }
}
