package vad.dashing.tbox.ui.launcher

/**
 * TPMS wheel index matches OEM [MBTiresView] / CAN 0x51B:
 * 0=FL, 1=FR, 2=RL, 3=RR.
 */
enum class LauncherWheelCorner(val index: Int) {
    FL(0),
    FR(1),
    RL(2),
    RR(3),
}

/**
 * Per-wheel TPMS snapshot from mbCAN [MBCanTireInfo].
 *
 * OEM “abnormal” = [warningSts] != 0; pressure `-1` means calibrating / invalid
 * (shown as `---/--` in carsettings).
 */
data class LauncherTireWheelState(
    val pressureBar: Float? = null,
    val temperatureC: Int? = null,
    val warningSts: Int = 0,
    val calibrating: Boolean = false,
    val available: Boolean = false,
) {
    /** Show callout next to the 3D car — OEM abnormal or clearly low pressure (<2.0 bar). */
    val needsAttention: Boolean
        get() = warningSts != 0 || calibrating || (available && pressureBar == null) ||
            (pressureBar != null && pressureBar < LOW_PRESSURE_BAR)

    companion object {
        /** User-requested visual threshold for low-pressure callout. */
        const val LOW_PRESSURE_BAR = 2.0f
    }
}

data class LauncherTireState(
    val fl: LauncherTireWheelState = LauncherTireWheelState(),
    val fr: LauncherTireWheelState = LauncherTireWheelState(),
    val rl: LauncherTireWheelState = LauncherTireWheelState(),
    val rr: LauncherTireWheelState = LauncherTireWheelState(),
) {
    fun wheel(corner: LauncherWheelCorner): LauncherTireWheelState = when (corner) {
        LauncherWheelCorner.FL -> fl
        LauncherWheelCorner.FR -> fr
        LauncherWheelCorner.RL -> rl
        LauncherWheelCorner.RR -> rr
    }

    val hasAttention: Boolean
        get() = fl.needsAttention || fr.needsAttention || rl.needsAttention || rr.needsAttention
}

internal fun parseLauncherTireWheel(info: Any?): LauncherTireWheelState {
    if (info == null) return LauncherTireWheelState()
    val pressure = runCatching {
        (info.javaClass.getMethod("getPressure").invoke(info) as? Number)?.toFloat()
    }.getOrNull()
    val warning = runCatching {
        (info.javaClass.getMethod("getWarningSts").invoke(info) as? Number)?.toInt() ?: 0
    }.getOrDefault(0)
    val temperature = runCatching {
        (info.javaClass.getMethod("getTemperature").invoke(info) as? Number)?.toInt()
    }.getOrNull()
    val calibrating = pressure == -1f
    val pressureValid = pressure != null && pressure.isFinite() && pressure > 0f
    val tempValid = temperature != null && temperature != -100 && temperature in -80..150
    return LauncherTireWheelState(
        pressureBar = pressure.takeIf { pressureValid },
        temperatureC = if (tempValid) temperature else null,
        warningSts = warning,
        calibrating = calibrating,
        available = true,
    )
}

internal fun parseLauncherTireState(tires: Any?): LauncherTireState {
    if (tires == null) return LauncherTireState()
    val arr = runCatching {
        tires.javaClass.getMethod("getVstTire").invoke(tires) as? Array<*>
    }.getOrNull()
    if (arr == null || arr.size < 4) return LauncherTireState()
    return LauncherTireState(
        fl = parseLauncherTireWheel(arr[0]),
        fr = parseLauncherTireWheel(arr[1]),
        rl = parseLauncherTireWheel(arr[2]),
        rr = parseLauncherTireWheel(arr[3]),
    )
}
