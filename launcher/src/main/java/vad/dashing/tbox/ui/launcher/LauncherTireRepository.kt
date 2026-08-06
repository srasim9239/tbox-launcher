package vad.dashing.tbox.ui.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live TPMS for the launcher overlay.
 * Fed from [vad.dashing.tbox.mbcan.MbCanEngineFacade] settings telemetry
 * (`onCanVehicleTires`) — same push path as OEM carsettings widgets.
 */
object LauncherTireRepository {
    private val _state = MutableStateFlow(LauncherTireState())
    val state: StateFlow<LauncherTireState> = _state.asStateFlow()

    fun applyFromMbCan(tires: Any?) {
        _state.value = parseLauncherTireState(tires)
        LauncherVehicleAlertsRepository.refresh()
    }

    fun clear() {
        _state.value = LauncherTireState()
        LauncherVehicleAlertsRepository.refresh()
    }
}
