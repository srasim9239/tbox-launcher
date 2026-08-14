package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherLeftPanel(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    onOpenVehicleSettings: () -> Unit,
    modelRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    paintRevision: Int = 0,
    onPaintChanged: (String) -> Unit = {},
    onCarBoundsChanged: (Rect) -> Unit = {},
    colorPickerVisible: Boolean = false,
    roadVisible: Boolean = true,
    carHidden: Boolean = false,
    settingsTransitionProgress: Float = 0f,
    settingsUserYawDeg: Float = 0f,
    onColorPickerOpen: () -> Unit = {},
    onColorPickerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val fuelPctFiltered by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val fuelPctRaw by canViewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    // Filtered % считается только в активной поездке; вне поездки показываем сырой процент.
    val fuelPct = fuelPctFiltered ?: fuelPctRaw
    val voltage by canViewModel.voltage.collectAsStateWithLifecycle()
    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val adasLive by LauncherAdasRepository.state.collectAsStateWithLifecycle()
    val tires by LauncherTireRepository.state.collectAsStateWithLifecycle()
    val motion = rememberLauncherVehicleMotion(tboxConnected, canViewModel)

    val racing = LauncherEggRace.active
    val raceLane = LauncherEggRace.playerLane
    val raceCars = LauncherEggRace.cars
    LauncherEggRaceTicker()
    val carLaneShift = if (racing) eggRaceCarShiftDp(raceLane) else 0.dp

    val simulateEnabled = LauncherDevVehicleState.simulateEnabled
    val effectiveSpeed = if (racing) LauncherEggRace.speedKmh else motion.speedKmh
    val effectiveSteer = rememberLauncherVisualSteer(motion)
    val steerPreview = motion.steerPreviewActive

    val effectiveBody = if (simulateEnabled) {
        LauncherDevVehicleState.bodyState()
    } else {
        vehicleBody
    }
    // Simulation overrides (hidden settings tab) take precedence over live TPMS.
    val effectiveTires = LauncherDevVehicleState.tireStateOrNull() ?: tires
    // Same for ADAS: cruise/BSD/PDC sim replaces the live mbCAN state.
    val adas = LauncherDevVehicleState.adasStateOrNull() ?: adasLive

    val rigState = LauncherCarRigState(
        doorFlOpen = effectiveBody.doorFlOpen,
        doorFrOpen = effectiveBody.doorFrOpen,
        doorRlOpen = effectiveBody.doorRlOpen,
        doorRrOpen = effectiveBody.doorRrOpen,
        tailgateOpen = effectiveBody.tailgateOpen,
        speedKmh = effectiveSpeed,
        steeringDeg = effectiveSteer,
    )
    var bodyRigAvailable by remember { mutableStateOf(false) }
    var wheelAnchors by remember {
        mutableStateOf<Map<LauncherWheelCorner, Offset>>(emptyMap())
    }
    var pdcRings by remember { mutableStateOf<LauncherPdcRingFrame?>(null) }

    // Simulation gear override (hidden settings tab) takes precedence over live gearbox.
    val activeGear = LauncherDevVehicleState.gearSlotOverride
        ?: resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
    val inDriveGear = racing || activeGear == 'D'
    val fuelText = fuelPct?.toInt()?.let { "$it%" } ?: "—"
    val speedText = valueToString(effectiveSpeed, 0, default = "0")
    val voltageValue = voltage
    val voltageLow = voltageValue != null && voltageValue < 12f
    val voltageText = voltageValue?.let {
        "${valueToString(it, 1)} ${stringResource(R.string.unit_volt)}"
    } ?: "— ${stringResource(R.string.unit_volt)}"
    val voltageColor = when {
        voltageValue == null -> LauncherColors.LeftTextSecondary
        voltageLow -> LauncherColors.WarningRed
        else -> LauncherColors.LeftTextPrimary
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.leftPanelBounds = android.graphics.Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                )
                onCarBoundsChanged(rect)
            }
            .background(LauncherColors.LeftPanelBg),
    ) {
        if (roadVisible) {
            LauncherVirtualRoad(
                speedKmh = if (racing) effectiveSpeed * 0.45f else effectiveSpeed,
                steerAngleDeg = effectiveSteer,
                adas = if (racing) LauncherAdasState() else adas,
                steerPreview = steerPreview,
                inDriveGear = inDriveGear,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (racing) {
            // Past the bumper: draw under the 3D so a dodge continues beside/behind the body.
            LauncherEggRaceCarsLayer(
                cars = raceCars,
                minDepth = LauncherEggRace.PASS_UNDER_DEPTH,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (racing) {
                LauncherEggRaceCloseBar()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherGearSelector(
                    activeSlot = if (racing) 'D' else activeGear,
                    onDClick = { LauncherEggRace.onDTapped() },
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_fuel),
                            contentDescription = stringResource(R.string.launcher_vs_fuel),
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(LauncherColors.LeftTextPrimary),
                        )
                        Text(
                            text = fuelText,
                            style = MaterialTheme.typography.tboxCaption,
                            color = LauncherColors.LeftTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (voltageLow) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_battery),
                                contentDescription = stringResource(R.string.data_title_voltage),
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(LauncherColors.WarningRed),
                            )
                        }
                        Text(
                            text = voltageText,
                            style = MaterialTheme.typography.tboxCaption,
                            color = voltageColor,
                            fontSize = 13.sp,
                            fontWeight = if (voltageLow) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$speedText ${stringResource(R.string.unit_kmh)}",
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.LeftTextSecondary,
                    fontSize = 13.sp,
                )
            if (!racing) {
                LauncherCruisePresetControl(
                    canViewModel = canViewModel,
                    adas = adas,
                )
            }
            }
            if (!tboxConnected && !racing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(LauncherColors.GearInactive),
                    )
                    Text(
                        text = stringResource(R.string.value_disconnected),
                        fontSize = 12.sp,
                        color = LauncherColors.LeftTextSecondary,
                    )
                }
            }
            if (!racing) {
                LauncherAdasStrip(canViewModel = canViewModel)
                LauncherVehicleAlertsStrip(modifier = Modifier.fillMaxWidth())
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!carHidden) {
                val settingsProgress = settingsTransitionProgress.coerceIn(0f, 1f)
                LauncherCar3DModel(
                    rigState = rigState,
                    speedKmh = effectiveSpeed,
                    steeringDeg = effectiveSteer,
                    steerPreview = steerPreview,
                    inDriveGear = inDriveGear,
                    modelRevision = modelRevision,
                    paintRevision = paintRevision,
                    paintId = paintId,
                    showRoad = false,
                    settingsView = settingsProgress > 0.02f,
                    settingsProgress = settingsProgress,
                    settingsUserYawDeg = settingsUserYawDeg,
                    onWheelAnchorsChanged = { wheelAnchors = it },
                    onPdcRingsChanged = { pdcRings = it },
                    onBodyRigAvailabilityChanged = { bodyRigAvailable = it },
                    textureSurface = racing,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = with(density) { carLaneShift.toPx() }
                        },
                )
                if (settingsProgress < 0.15f && !racing) {
                    LauncherTireBadges(
                        state = effectiveTires,
                        // ADAS strip shows a small pressure-only pill next to the wheel.
                        compact = true,
                        wheelAnchorsPx = wheelAnchors,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!bodyRigAvailable) {
                        LauncherDoorBadges(
                            body = effectiveBody,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    LauncherAdasTimeGapFlash(
                        timeGapLevel = adas.timeGapLevel,
                        timeGapFlashUntilMs = adas.timeGapFlashUntilMs,
                    )
                    LauncherRearThreatOverlay(
                        threats = adas.rearThreats,
                        modifier = Modifier.fillMaxSize(),
                    )
                    LauncherPdcOverlay(
                        pdc = adas.pdc,
                        rings = pdcRings,
                        driving = inDriveGear || steerPreview,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!racing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenVehicleSettings,
                            onLongClick = onColorPickerOpen,
                        ),
                )
            }
            if (colorPickerVisible) {
                LauncherCarColorPicker(
                    selectedId = paintId,
                    onSelect = { id ->
                        onPaintChanged(id)
                        LauncherAppConfigStore.setCarPaintId(context, id)
                    },
                    onDismiss = onColorPickerDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }

        if (racing) {
            LauncherEggRaceControls()
        } else {
            LauncherMediaMiniPlayer()
        }
        }
        if (racing) {
            LauncherEggRaceCarsLayer(
                cars = raceCars,
                maxDepth = LauncherEggRace.PASS_UNDER_DEPTH,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
