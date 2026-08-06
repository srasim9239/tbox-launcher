package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.HvacTempZone
import vad.dashing.tbox.ui.LIGHT_CONTROL_AUTO
import vad.dashing.tbox.ui.LIGHT_CONTROL_LOW_BEAM
import vad.dashing.tbox.ui.LIGHT_CONTROL_OFF
import vad.dashing.tbox.ui.LIGHT_CONTROL_POSITION
import vad.dashing.tbox.ui.refreshHvacTemperaturesFromMbCan
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendCycleHeadlightsSwitch
import vad.dashing.tbox.ui.sendOpenCloseTrunk
import vad.dashing.tbox.ui.sendSetMbCanProperty
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleHvacDefrosterFront
import vad.dashing.tbox.ui.sendToggleRearWindowMirrorsDefrost
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString
import kotlin.math.abs

private val HvacOnColor = Color(0xFF4FC3F7)
private val SeatHeatOnColor = Color(0xFFF59E0B)
private val HvacOffColor = LauncherColors.TextSecondary
private const val LAUNCHER_BOTTOM_BAR_CAN_SOURCE = "launcher-bottom-bar"
private const val SEAT_COMMAND_CONFIRM_TIMEOUT_MS = 2_500L
private const val LIGHT_COMMAND_CONFIRM_TIMEOUT_MS = 1_200L

internal data class LauncherPendingSeatCommand(
    val raw: Int,
    val sequence: Long,
)

private fun formatHvacSetTemp(celsius: Float?): String {
    if (celsius == null) return "—"
    val rounded = (celsius * 2f).toInt() / 2f
    return if (abs(rounded - rounded.toInt()) < 0.01f) {
        rounded.toInt().toString()
    } else {
        valueToString(rounded, 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherBottomBar(
    canViewModel: CanDataViewModel,
    onOpenApps: () -> Unit,
    onCloseVehicleSettings: () -> Unit = {},
    onCloseAppDrawer: () -> Unit = {},
    onOpenVehicleSettings: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") configRevision: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val climateTempDriver by canViewModel.climateSetTemperature1.collectAsStateWithLifecycle()
    val climateTempPassenger by canViewModel.climateSetTemperature2.collectAsStateWithLifecycle()
    val hvacAuto by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val hvacRecirc by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val hvacDefrostRear by UniversalCanRepository.hvacDefrosterState.collectAsStateWithLifecycle()
    val hvacDefrostFront by UniversalCanRepository.hvacDefrosterFrontState.collectAsStateWithLifecycle()
    val steeringHeat by UniversalCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val driverSeat by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val passengerSeat by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val vehicleControls = rememberLauncherVehicleControlSnapshot(enabled = true)
    var driverSeatPending by remember { mutableStateOf<LauncherPendingSeatCommand?>(null) }
    var passengerSeatPending by remember { mutableStateOf<LauncherPendingSeatCommand?>(null) }
    var seatCommandSequence by remember { mutableStateOf(0L) }
    var lightControlRaw by remember { mutableStateOf(LIGHT_CONTROL_OFF) }
    var rearFogActive by remember { mutableStateOf(false) }
    var rearFogConfirmed by remember { mutableStateOf<Boolean?>(null) }
    var rearFogPendingUntilMs by remember { mutableStateOf(0L) }
    val driverSeatConfirmedRaw = launcherSeatModeToRaw(driverSeat) ?: 1
    val passengerSeatConfirmedRaw = launcherSeatModeToRaw(passengerSeat) ?: 1
    val driverSeatRaw = driverSeatPending?.raw ?: driverSeatConfirmedRaw
    val passengerSeatRaw = passengerSeatPending?.raw ?: passengerSeatConfirmedRaw

    LaunchedEffect(Unit) {
        UniversalCanRepository.setSourceSignals(
            LAUNCHER_BOTTOM_BAR_CAN_SOURCE,
            setOf(
                MbCanSignal.HvacAirRecirculation,
                MbCanSignal.HvacAcPower,
                MbCanSignal.HvacAutoState,
                MbCanSignal.HvacDefrosterFront,
                MbCanSignal.HvacDefroster,
                MbCanSignal.SteeringWheelHeat,
                MbCanSignal.FrontLeftSeatMode,
                MbCanSignal.FrontRightSeatMode,
            ),
        )
        while (isActive) {
            withContext(Dispatchers.IO) { refreshHvacTemperaturesFromMbCan() }
            delay(2_500L)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            UniversalCanRepository.enqueueClearSource(LAUNCHER_BOTTOM_BAR_CAN_SOURCE)
        }
    }
    LaunchedEffect(driverSeat) {
        if (launcherSeatModeToRaw(driverSeat) != null) driverSeatPending = null
    }
    LaunchedEffect(passengerSeat) {
        if (launcherSeatModeToRaw(passengerSeat) != null) passengerSeatPending = null
    }
    LaunchedEffect(driverSeatPending) {
        val pending = driverSeatPending ?: return@LaunchedEffect
        delay(SEAT_COMMAND_CONFIRM_TIMEOUT_MS)
        if (driverSeatPending == pending) driverSeatPending = null
    }
    LaunchedEffect(passengerSeatPending) {
        val pending = passengerSeatPending ?: return@LaunchedEffect
        delay(SEAT_COMMAND_CONFIRM_TIMEOUT_MS)
        if (passengerSeatPending == pending) passengerSeatPending = null
    }
    LaunchedEffect(vehicleControls.rearFogLight) {
        vehicleControls.rearFogLight?.let { confirmed ->
            rearFogConfirmed = confirmed
            if (android.os.SystemClock.uptimeMillis() >= rearFogPendingUntilMs) {
                rearFogActive = confirmed
            }
        }
    }
    LaunchedEffect(rearFogPendingUntilMs) {
        if (rearFogPendingUntilMs > 0L) {
            val remaining = rearFogPendingUntilMs - android.os.SystemClock.uptimeMillis()
            if (remaining > 0) delay(remaining)
            rearFogConfirmed?.let { rearFogActive = it }
        }
    }
    LaunchedEffect(vehicleControls.lightControlRaw) {
        vehicleControls.lightControlRaw?.let { lightControlRaw = it }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.bottomBarTopPx = rect.top.toInt()
            }
            .background(LauncherColors.BottomBarBg)
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherDockIcon(
                onClick = {
                    goLauncherHome(
                        context = context,
                        onCloseOverlays = {
                            onCloseVehicleSettings()
                            onCloseAppDrawer()
                        },
                    )
                },
                onLongClick = onOpenVehicleSettings,
            ) {
                Icon(Icons.Filled.Home, stringResource(R.string.launcher_home_cd), tint = LauncherColors.AccentCyan)
            }
            LauncherDockIcon(onClick = {
                // Same as hardware Back: close overlays / focus foreign freeform + KEYCODE_BACK.
                goLauncherBack(
                    context = context,
                    vehicleSettingsOpen = LauncherVehicleSettingsUiState.open,
                    appDrawerOpen = false,
                    onCloseVehicleSettings = onCloseVehicleSettings,
                    onCloseAppDrawer = onCloseAppDrawer,
                )
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.launcher_back_cd),
                    tint = LauncherColors.TextSecondary,
                )
            }
        }

        // This group is pinned to the physical screen center, independent of side widths.
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LauncherDockIcon(onClick = { sendToggleHvacAirRecirculation(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_hvac_air_recirculation, hvacRecirc)
            }
            LauncherDockIcon(
                onClick = {
                    val next = nextSeatHeatRaw(driverSeatRaw)
                    seatCommandSequence += 1
                    driverSeatPending = LauncherPendingSeatCommand(next, seatCommandSequence)
                    sendSetMbCanProperty(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
                        next,
                    )
                },
            ) {
                LauncherSeatHeatIcon(driverSeatRaw)
            }
            LauncherDockIcon(
                onClick = {
                    val next = nextSeatVentRaw(driverSeatRaw)
                    seatCommandSequence += 1
                    driverSeatPending = LauncherPendingSeatCommand(next, seatCommandSequence)
                    sendSetMbCanProperty(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
                        next,
                    )
                },
            ) {
                LauncherSeatVentIcon(driverSeatRaw)
            }
            LauncherTempStepper(
                tempText = formatHvacSetTemp(climateTempDriver),
                onDown = {
                    sendAdjustHvacTemperature(context, climateTempDriver, -0.5f, HvacTempZone.Driver)
                },
                onUp = {
                    sendAdjustHvacTemperature(context, climateTempDriver, 0.5f, HvacTempZone.Driver)
                },
            )
            LauncherDockIcon(
                onClick = { sendToggleHvacAuto(context) },
                onLongClick = { launchClimateApp(context) },
            ) {
                LauncherHvacIcon(R.drawable.ic_widget_hvac_auto, hvacAuto)
            }
            LauncherTempStepper(
                tempText = formatHvacSetTemp(climateTempPassenger),
                onDown = {
                    sendAdjustHvacTemperature(
                        context, climateTempPassenger, -0.5f, HvacTempZone.Passenger,
                    )
                },
                onUp = {
                    sendAdjustHvacTemperature(
                        context, climateTempPassenger, 0.5f, HvacTempZone.Passenger,
                    )
                },
            )
            LauncherDockIcon(
                onClick = {
                    val next = nextSeatVentRaw(passengerSeatRaw)
                    seatCommandSequence += 1
                    passengerSeatPending = LauncherPendingSeatCommand(next, seatCommandSequence)
                    sendSetMbCanProperty(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
                        next,
                    )
                },
            ) {
                LauncherSeatVentIcon(passengerSeatRaw, mirrored = true)
            }
            LauncherDockIcon(
                onClick = {
                    val next = nextSeatHeatRaw(passengerSeatRaw)
                    seatCommandSequence += 1
                    passengerSeatPending = LauncherPendingSeatCommand(next, seatCommandSequence)
                    sendSetMbCanProperty(
                        context,
                        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
                        next,
                    )
                },
            ) {
                LauncherSeatHeatIcon(passengerSeatRaw, mirrored = true)
            }
            LauncherDockIcon(onClick = { sendToggleHvacDefrosterFront(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_hvac_defroster_front, hvacDefrostFront)
            }
            LauncherDockIcon(onClick = { sendToggleRearWindowMirrorsDefrost(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_rear_window_mirrors_defrost, hvacDefrostRear)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherDockIcon(onClick = { sendToggleSteeringWheelHeat(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_steering_wheel_heat, steeringHeat)
            }
            LauncherDockIcon(onClick = {
                val current = lightControlRaw
                lightControlRaw = nextLightControlRaw(current)
                sendCycleHeadlightsSwitch(context, current)
            }) {
                LauncherHeadlightsModeIcon(lightControlRaw)
            }
            LauncherDockIcon(onClick = {
                val next = !(rearFogConfirmed ?: rearFogActive)
                rearFogActive = next
                rearFogPendingUntilMs = android.os.SystemClock.uptimeMillis() + LIGHT_COMMAND_CONFIRM_TIMEOUT_MS
                sendSetMbCanProperty(
                    context,
                    MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
                    if (next) 2 else 1,
                )
            }) {
                LauncherBinaryTintIcon(
                    drawableRes = R.drawable.ic_widget_rear_fog,
                    active = rearFogActive,
                )
            }
            LauncherDockIcon(
                onClick = {},
                onLongClick = { sendOpenCloseTrunk(context) },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_trunk),
                    contentDescription = stringResource(R.string.launcher_trunk_long_press_cd),
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(LauncherColors.TextSecondary),
                )
            }
            LauncherDockIcon(onClick = { launchLauncherApp(context, "com.autopai.car.dialer") }) {
                Icon(Icons.Filled.Phone, null, tint = LauncherColors.AccentCyan)
            }
            LauncherDockIcon(onClick = onOpenApps) {
                Icon(Icons.Filled.Menu, stringResource(R.string.launcher_footer_apps), tint = LauncherColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun LauncherTempStepper(
    tempText: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        LauncherDockIcon(onClick = onDown) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = LauncherColors.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = tempText,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 1.dp),
        )
        LauncherDockIcon(onClick = onUp) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                null,
                tint = LauncherColors.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LauncherHvacIcon(drawableRes: Int, state: MbCanBinaryState) {
    val tint = when (state) {
        is MbCanBinaryState.On -> HvacOnColor
        is MbCanBinaryState.Off -> HvacOffColor
        else -> HvacOffColor.copy(alpha = 0.55f)
    }
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(26.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun LauncherSeatModeIcon(
    drawableRes: Int,
    active: Boolean,
    level: Int,
    onColor: Color,
) {
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(if (active) onColor else HvacOffColor),
        )
        if (level > 0) {
            Text(
                text = level.toString(),
                color = LauncherColors.CanvasDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(5.dp))
                    .background(onColor)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * Seat heat: base seat + OEM heat waves (ic_widget_seat_heat_1..3), amber.
 * Level lights progressively more waves, like ventilation blades.
 */
@Composable
private fun LauncherSeatHeatIcon(raw: Int, mirrored: Boolean = false) {
    val level = heatLevel(raw)
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .size(26.dp)
                .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            colorFilter = ColorFilter.tint(if (level > 0) SeatHeatOnColor else HvacOffColor),
        )
        listOf(
            R.drawable.ic_widget_seat_heat_1 to (level >= 1),
            R.drawable.ic_widget_seat_heat_2 to (level >= 2),
            R.drawable.ic_widget_seat_heat_3 to (level >= 3),
        ).forEach { (drawable, enabled) ->
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                colorFilter = ColorFilter.tint(if (enabled) SeatHeatOnColor else HvacOffColor),
            )
        }
    }
}

@Composable
private fun LauncherSeatVentIcon(raw: Int, mirrored: Boolean = false) {
    val level = ventLevel(raw)
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .size(26.dp)
                .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            colorFilter = ColorFilter.tint(if (level > 0) HvacOnColor else HvacOffColor),
        )
        listOf(
            R.drawable.ic_widget_seat_vent_0 to (level >= 1),
            R.drawable.ic_widget_seat_vent_1 to (level >= 1),
            R.drawable.ic_widget_seat_vent_2 to (level >= 2),
            R.drawable.ic_widget_seat_vent_3 to (level >= 3),
        ).forEach { (drawable, enabled) ->
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                colorFilter = ColorFilter.tint(if (enabled) HvacOnColor else HvacOffColor),
            )
        }
    }
}

@Composable
private fun LauncherBinaryTintIcon(drawableRes: Int, active: Boolean) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(26.dp),
        colorFilter = ColorFilter.tint(if (active) HvacOnColor else HvacOffColor),
    )
}

@Composable
private fun LauncherHeadlightsModeIcon(raw: Int) {
    val active = raw != LIGHT_CONTROL_OFF
    val mode = when (raw) {
        LIGHT_CONTROL_POSITION -> "Г"
        LIGHT_CONTROL_LOW_BEAM -> "Б"
        LIGHT_CONTROL_AUTO -> "A"
        else -> "0"
    }
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_widget_headlights),
            contentDescription = stringResource(R.string.launcher_vs_headlights),
            modifier = Modifier.size(27.dp),
            colorFilter = ColorFilter.tint(if (active) HvacOnColor else HvacOffColor),
        )
        Text(
            text = mode,
            color = if (active) LauncherColors.CanvasDark else LauncherColors.TextPrimary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(5.dp))
                .background(if (active) HvacOnColor else LauncherColors.CardDarkElevated)
                .padding(horizontal = 3.dp),
        )
    }
}

internal fun launcherSeatModeToRaw(mode: MbCanSeatModeState): Int? = when (mode) {
    is MbCanSeatModeState.Off -> 1
    is MbCanSeatModeState.Heat -> when (mode.level) {
        1 -> 2
        2 -> 3
        3 -> 4
        else -> null
    }
    is MbCanSeatModeState.Vent -> when (mode.level) {
        1 -> 5
        2 -> 6
        3 -> 7
        else -> null
    }
    else -> null
}

internal fun nextSeatHeatRaw(current: Int): Int = when (current) {
    4 -> 3
    3 -> 2
    2 -> 1
    else -> 4
}

internal fun nextSeatVentRaw(current: Int): Int = when (current) {
    7 -> 6
    6 -> 5
    5 -> 1
    else -> 7
}

private fun nextLightControlRaw(current: Int): Int = when (current) {
    LIGHT_CONTROL_OFF -> LIGHT_CONTROL_POSITION
    LIGHT_CONTROL_POSITION -> LIGHT_CONTROL_LOW_BEAM
    LIGHT_CONTROL_LOW_BEAM -> LIGHT_CONTROL_AUTO
    else -> LIGHT_CONTROL_OFF
}

internal fun heatLevel(raw: Int): Int = when (raw) {
    4 -> 3
    3 -> 2
    2 -> 1
    else -> 0
}

internal fun ventLevel(raw: Int): Int = when (raw) {
    7 -> 3
    6 -> 2
    5 -> 1
    else -> 0
}

internal fun seatVentDrawable(raw: Int): Int = when (ventLevel(raw)) {
    3 -> R.drawable.ic_widget_seat_vent_3
    2 -> R.drawable.ic_widget_seat_vent_2
    1 -> R.drawable.ic_widget_seat_vent_1
    else -> R.drawable.ic_widget_seat_vent_0
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherDockIcon(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
