package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vad.dashing.tbox.mbcan.VehicleBodyState

/** Debug overrides for launcher vehicle animation when not in the car. */
object LauncherDevVehicleState {
    var simulateEnabled by mutableStateOf(false)
    /**
     * Preview steer animation from the physical wheel while stationary (real CAN steer, speed visual = 0).
     */
    var motionPreviewEnabled by mutableStateOf(false)
    var speedKmh by mutableFloatStateOf(0f)
    var steerAngleDeg by mutableFloatStateOf(0f)
    var doorFlOpen by mutableStateOf(false)
    var doorFrOpen by mutableStateOf(false)
    var doorRlOpen by mutableStateOf(false)
    var doorRrOpen by mutableStateOf(false)
    var tailgateOpen by mutableStateOf(false)
    var seatBeltDriver by mutableStateOf(false)
    var seatBeltPassenger by mutableStateOf(false)
    var tirePressureOverride by mutableStateOf<Map<LauncherWheelCorner, Float>>(emptyMap())
    var batteryVoltageOverride by mutableStateOf<Float?>(null)
    /** 'P'/'R'/'N'/'D' override; null = real gearbox state. */
    var gearSlotOverride by mutableStateOf<Char?>(null)

    fun bodyState(): VehicleBodyState = VehicleBodyState(
        doorFlOpen = doorFlOpen,
        doorFrOpen = doorFrOpen,
        doorRlOpen = doorRlOpen,
        doorRrOpen = doorRrOpen,
        tailgateOpen = tailgateOpen,
    )

    fun toggleSimulate() {
        simulateEnabled = !simulateEnabled
        if (simulateEnabled) motionPreviewEnabled = false
    }

    fun toggleMotionPreview() {
        motionPreviewEnabled = !motionPreviewEnabled
        if (motionPreviewEnabled) simulateEnabled = false
    }

    fun bumpSpeed(delta: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        speedKmh = (speedKmh + delta).coerceIn(0f, 180f)
    }

    fun bumpSteer(delta: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        steerAngleDeg = (steerAngleDeg + delta).coerceIn(-540f, 540f)
    }

    fun toggleDoorFl() {
        simulateEnabled = true
        doorFlOpen = !doorFlOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleDoorFr() {
        simulateEnabled = true
        doorFrOpen = !doorFrOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleTailgate() {
        simulateEnabled = true
        tailgateOpen = !tailgateOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleSeatBeltDriver() {
        simulateEnabled = true
        seatBeltDriver = !seatBeltDriver
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleSeatBeltPassenger() {
        simulateEnabled = true
        seatBeltPassenger = !seatBeltPassenger
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleDoorRl() {
        simulateEnabled = true
        doorRlOpen = !doorRlOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleDoorRr() {
        simulateEnabled = true
        doorRrOpen = !doorRrOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun setSpeed(value: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        speedKmh = value.coerceIn(0f, 220f)
    }

    fun setSteer(value: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        steerAngleDeg = value.coerceIn(-540f, 540f)
    }

    fun setTirePressure(corner: LauncherWheelCorner, bar: Float?) {
        simulateEnabled = true
        tirePressureOverride = tirePressureOverride.toMutableMap().apply {
            if (bar == null) remove(corner) else put(corner, bar.coerceIn(0f, 4.5f))
        }
        LauncherVehicleAlertsRepository.refresh()
    }

    fun setBatteryVoltage(value: Float?) {
        simulateEnabled = true
        batteryVoltageOverride = value?.coerceIn(9f, 16f)
    }

    fun setGearSlot(slot: Char?) {
        simulateEnabled = true
        motionPreviewEnabled = false
        gearSlotOverride = slot?.takeIf { it in SIM_GEAR_SLOTS }
    }

    fun tireStateOrNull(): LauncherTireState? {
        if (tirePressureOverride.isEmpty()) return null
        fun wheel(c: LauncherWheelCorner): LauncherTireWheelState {
            val bar = tirePressureOverride[c]
            return LauncherTireWheelState(
                pressureBar = bar,
                temperatureC = null,
                warningSts = if (bar != null && bar < LauncherTireWheelState.LOW_PRESSURE_BAR) 1 else 0,
                calibrating = false,
                available = bar != null,
            )
        }
        return LauncherTireState(
            fl = wheel(LauncherWheelCorner.FL),
            fr = wheel(LauncherWheelCorner.FR),
            rl = wheel(LauncherWheelCorner.RL),
            rr = wheel(LauncherWheelCorner.RR),
        )
    }

    fun resetSimulation() {
        simulateEnabled = false
        motionPreviewEnabled = false
        speedKmh = 0f
        steerAngleDeg = 0f
        doorFlOpen = false
        doorFrOpen = false
        doorRlOpen = false
        doorRrOpen = false
        tailgateOpen = false
        seatBeltDriver = false
        seatBeltPassenger = false
        tirePressureOverride = emptyMap()
        batteryVoltageOverride = null
        gearSlotOverride = null
        LauncherVehicleAlertsRepository.refresh()
    }
}

/** Selectable gearbox slots for the simulation tab. */
val SIM_GEAR_SLOTS: List<Char> = listOf('P', 'R', 'N', 'D')
