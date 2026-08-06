package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption

/** Compact YNarrows-style nav hint: maneuver, distance, street, lights, alerts. */
@Composable
fun LauncherNavHintStrip(modifier: Modifier = Modifier) {
    val nav by LauncherNavRepository.state.collectAsStateWithLifecycle()
    if (!nav.hasHint) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.AccentCyan.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = maneuverGlyph(nav.maneuver, nav.exitNumber),
            color = LauncherColors.AccentCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            nav.distanceText?.let { distance ->
                Text(
                    text = distance,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            nav.street?.let { street ->
                Text(
                    text = street,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (nav.trafficLight != LauncherNavTrafficLight.None) {
            LauncherNavTrafficLightChip(
                color = nav.trafficLight,
                seconds = nav.trafficLightSec,
            )
        }
        nav.speedLimitKmh?.let { limit ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE11D48).copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = limit.toString(),
                    color = Color(0xFFE11D48),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (nav.alert != LauncherNavAlert.None) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = stringResource(nav.alert.labelRes),
                    color = Color(0xFFF59E0B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LauncherNavTrafficLightChip(
    color: LauncherNavTrafficLight,
    seconds: String?,
) {
    val tint = when (color) {
        LauncherNavTrafficLight.Red -> Color(0xFFEF4444)
        LauncherNavTrafficLight.Yellow -> Color(0xFFEAB308)
        LauncherNavTrafficLight.Green -> Color(0xFF22C55E)
        LauncherNavTrafficLight.None -> Color.Transparent
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tint),
        )
        seconds?.let {
            Text(
                text = it,
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val LauncherNavAlert.labelRes: Int
    get() = when (this) {
        LauncherNavAlert.None -> R.string.launcher_nav_alert_other
        LauncherNavAlert.Camera -> R.string.launcher_nav_alert_camera
        LauncherNavAlert.Accident -> R.string.launcher_nav_alert_accident
        LauncherNavAlert.RoadWorks -> R.string.launcher_nav_alert_works
        LauncherNavAlert.Other -> R.string.launcher_nav_alert_other
    }

private fun maneuverGlyph(maneuver: LauncherNavManeuver, exitNumber: String?): String {
    val base = when (maneuver) {
        LauncherNavManeuver.Unknown -> "•"
        LauncherNavManeuver.Straight -> "↑"
        LauncherNavManeuver.SlightLeft -> "↖"
        LauncherNavManeuver.Left, LauncherNavManeuver.ForkLeft, LauncherNavManeuver.ExitLeft -> "←"
        LauncherNavManeuver.HardLeft -> "↰"
        LauncherNavManeuver.SlightRight -> "↗"
        LauncherNavManeuver.Right, LauncherNavManeuver.ForkRight, LauncherNavManeuver.ExitRight -> "→"
        LauncherNavManeuver.HardRight -> "↱"
        LauncherNavManeuver.UTurnLeft -> "↶"
        LauncherNavManeuver.UTurnRight -> "↷"
        LauncherNavManeuver.EnterRoundabout, LauncherNavManeuver.LeaveRoundabout -> "⟳"
        LauncherNavManeuver.Ferry -> "⛴"
        LauncherNavManeuver.Finish -> "⚑"
    }
    return if (!exitNumber.isNullOrBlank()) "$base$exitNumber" else base
}
