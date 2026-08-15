package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxCaption

/** Compact ADAS status chips aligned with launcher palette. */
@Composable
fun LauncherAdasStrip(
    canViewModel: CanDataViewModel,
    modifier: Modifier = Modifier,
) {
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val adas by LauncherAdasRepository.state.collectAsStateWithLifecycle()

    val pasOn = parkingRadar is MbCanBinaryState.On
    val cruiseActive = (cruiseSpeed ?: 0u) > 0u
    val showAcc = adas.accActive || adas.accStandby
    val showAlerts = adas.fcwActive || adas.distanceWarning || adas.aebHint ||
        adas.accTakeOver || adas.adasTakeOver || adas.accOverride || adas.speedLimitWarning
    val showLanes = adas.laneAssistEngaged
    val showSla = adas.speedLimitKmh != null
    val showHma = adas.hma != LauncherAdasAssistIcon.Hidden
    val showTja = adas.tja != LauncherAdasAssistIcon.Hidden
    val showSrr = adas.srrSystem != LauncherSrrSystemState.Hidden
    if (!pasOn && !cruiseActive && !showAcc && !showAlerts &&
        !showLanes && !showSla && !showHma && !showTja && !showSrr
    ) {
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pasOn) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_pas_on),
                tint = LauncherColors.AccentBlue,
                iconRes = R.drawable.ic_widget_parking_radar,
            )
        }
        if (showHma) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_hma),
                tint = assistTint(adas.hma),
                iconRes = R.drawable.ic_adas_hma,
            )
        }
        if (showTja) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_tja),
                tint = assistTint(adas.tja),
                iconRes = R.drawable.ic_adas_tja,
            )
        }
        if (adas.srrSystem == LauncherSrrSystemState.Fault) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_srr_fault),
                tint = Color(0xFFEF4444),
                iconRes = R.drawable.ic_adas_fcw,
            )
        }
        if (showAcc) {
            val accTint = when {
                adas.accOverride || adas.accTakeOver -> Color(0xFFF59E0B)
                adas.accStandby -> Color(0xFFEAB308)
                adas.accMode == LauncherAdasAccMode.ActiveBlue -> LauncherColors.AccentCyan
                else -> LauncherColors.AccentBlue
            }
            adas.accSetSpeedKmh?.let { speed ->
                LauncherAdasSpeedBadge(speed = speed.toString(), tint = accTint)
            } ?: LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_acc_standby),
                tint = accTint,
                iconRes = R.drawable.ic_launcher_cruise,
            )
        } else if (cruiseActive) {
            LauncherAdasSpeedBadge(speed = cruiseSpeed.toString())
        }
        adas.speedLimitKmh?.let { limit ->
            LauncherAdasSpeedBadge(
                speed = limit.toString(),
                tint = if (adas.speedLimitWarning) Color(0xFFEF4444) else Color(0xFFE11D48),
            )
        }
        // Camera TSR is drawn as the round sign over the 3D area, not as a "TSR" chip.
        if (adas.fcwActive || adas.distanceWarning) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_fcw),
                tint = Color(0xFFEF4444),
                iconRes = R.drawable.ic_adas_fcw,
            )
        }
        if (adas.aebHint) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_aeb),
                tint = Color(0xFFEF4444),
                iconRes = R.drawable.ic_adas_aeb,
            )
        }
        if (adas.accTakeOver || adas.adasTakeOver) {
            LauncherAdasAlertChip(text = stringResource(R.string.launcher_adas_takeover))
        }
        if (adas.laneDepartureLeft || adas.laneDepartureRight || showLanes) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_lka),
                tint = when {
                    adas.laneDepartureLeft || adas.laneDepartureRight -> Color(0xFFF59E0B)
                    else -> LauncherColors.AccentBlue
                },
                iconRes = R.drawable.ic_adas_lka,
            )
        }
    }
}

@Composable
private fun LauncherRearThreatChips(threats: LauncherRearThreats) {
    fun tint(level: LauncherRearThreatLevel): Color = when (level) {
        LauncherRearThreatLevel.Alert -> Color(0xFFEF4444)
        LauncherRearThreatLevel.Caution -> Color(0xFFF59E0B)
        LauncherRearThreatLevel.Off -> LauncherColors.TextSecondary
    }
    if (threats.bsdLeft != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_bsd_left), tint(threats.bsdLeft))
    }
    if (threats.bsdRight != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_bsd_right), tint(threats.bsdRight))
    }
    if (threats.rctaLeft != LauncherRearThreatLevel.Off) {
        LauncherAdasAlertChip(stringResource(R.string.launcher_adas_rcta_left))
    }
    if (threats.rctaRight != LauncherRearThreatLevel.Off) {
        LauncherAdasAlertChip(stringResource(R.string.launcher_adas_rcta_right))
    }
    if (threats.dowLeft != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_dow_left), tint(threats.dowLeft))
    }
    if (threats.dowRight != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_dow_right), tint(threats.dowRight))
    }
    if (threats.rcw != LauncherRearThreatLevel.Off) {
        LauncherAdasChip(stringResource(R.string.launcher_adas_rcw), tint(threats.rcw))
    }
}

private fun assistTint(icon: LauncherAdasAssistIcon): Color = when (icon) {
    LauncherAdasAssistIcon.Warning -> Color(0xFFF59E0B)
    LauncherAdasAssistIcon.Active -> LauncherColors.AccentCyan
    LauncherAdasAssistIcon.Dark -> LauncherColors.AccentBlue
    LauncherAdasAssistIcon.Hidden -> LauncherColors.TextSecondary
}

private val LauncherAdasFrontObjectType.labelRes: Int
    get() = when (this) {
        LauncherAdasFrontObjectType.None -> R.string.launcher_adas_obj_unknown
        LauncherAdasFrontObjectType.Car -> R.string.launcher_adas_obj_car
        LauncherAdasFrontObjectType.Truck -> R.string.launcher_adas_obj_truck
        LauncherAdasFrontObjectType.Motorcycle -> R.string.launcher_adas_obj_moto
        LauncherAdasFrontObjectType.Pedestrian -> R.string.launcher_adas_obj_pedestrian
        LauncherAdasFrontObjectType.Bicycle -> R.string.launcher_adas_obj_bicycle
        LauncherAdasFrontObjectType.Bus -> R.string.launcher_adas_obj_bus
        LauncherAdasFrontObjectType.Unknown -> R.string.launcher_adas_obj_unknown
    }

@Composable
private fun LauncherAdasIcon(
    contentDescription: String,
    tint: Color,
    iconRes: Int,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun LauncherAdasSpeedBadge(
    speed: String,
    tint: Color = LauncherColors.AccentCyan,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speed,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LauncherAdasChip(
    text: String,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Transient ACC following-distance indicator centered over the left panel car area
 * (like stock cluster): only while the user is changing it, auto-hides after ~5 s.
 */
@Composable
fun BoxScope.LauncherAdasTimeGapFlash(
    timeGapLevel: Int?,
    timeGapFlashUntilMs: Long,
    tint: Color = LauncherColors.AccentCyan,
) {
    val level = timeGapLevel ?: return
    var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(timeGapFlashUntilMs) {
        while (android.os.SystemClock.uptimeMillis() < timeGapFlashUntilMs) {
            delay(160)
            now = android.os.SystemClock.uptimeMillis()
        }
        now = android.os.SystemClock.uptimeMillis()
    }
    if (timeGapFlashUntilMs <= now) return
    val bars = (level + 1).coerceIn(1, 3)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark.copy(alpha = 0.90f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .width(132.dp)
                .height(52.dp),
        ) {
            drawTimeGapSchema(bars = bars, tint = tint)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeGapSchema(
    bars: Int,
    tint: Color,
) {
    val w = size.width
    val h = size.height
    val carW = w * 0.20f
    val carH = h * 0.42f
    fun car(cx: Float, cy: Float) {
        val path = Path().apply {
            moveTo(cx - carW * 0.38f, cy + carH * 0.42f)
            lineTo(cx - carW * 0.42f, cy - carH * 0.05f)
            lineTo(cx - carW * 0.22f, cy - carH * 0.48f)
            lineTo(cx + carW * 0.22f, cy - carH * 0.48f)
            lineTo(cx + carW * 0.42f, cy - carH * 0.05f)
            lineTo(cx + carW * 0.38f, cy + carH * 0.42f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.92f), style = Stroke(width = 2.4f))
    }
    car(w * 0.16f, h * 0.58f)
    car(w * 0.84f, h * 0.58f)
    val gapLeft = w * 0.30f
    val gapRight = w * 0.70f
    val step = (gapRight - gapLeft) / 4f
    repeat(3) { i ->
        val x = gapLeft + step * (i + 1)
        val on = i < bars
        val color = tint.copy(alpha = if (on) 0.95f else 0.22f)
        drawLine(
            color = color,
            start = Offset(x, h * 0.34f),
            end = Offset(x, h * 0.78f),
            strokeWidth = if (on) 5.5f else 3.5f,
            cap = StrokeCap.Round,
        )
    }
}

/** Visual ACC following-distance like stock ADAS (1–3 chevrons). */
@Composable
private fun LauncherAdasTimeGapChip(
    level: Int,
    tint: Color,
) {
    val bars = (level + 1).coerceIn(1, 3)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_adas_time_gap_label),
                style = MaterialTheme.typography.tboxCaption,
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            repeat(3) { index ->
                Text(
                    text = "˄",
                    color = if (index < bars) tint else tint.copy(alpha = 0.28f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LauncherAdasAlertChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFFEF4444),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.tboxCaption,
                color = Color(0xFFEF4444),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
