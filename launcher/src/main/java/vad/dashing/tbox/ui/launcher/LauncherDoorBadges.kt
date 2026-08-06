package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.VehicleBodyState

/**
 * Icon markers for open doors / hood / trunk beside the 3D car.
 * Shown only when the corresponding body part is open (normal closed = hidden).
 */
@Composable
fun LauncherDoorBadges(
    body: VehicleBodyState,
    modifier: Modifier = Modifier,
) {
    if (!body.anyDoorOpen() && !body.hoodOpen) return

    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        if (body.doorFlOpen) {
            DoorBadge(
                iconRes = R.drawable.ic_launcher_door,
                cd = R.string.launcher_alert_door_fl,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = w * 0.04f, y = h * -0.06f),
            )
        }
        if (body.doorFrOpen) {
            DoorBadge(
                iconRes = R.drawable.ic_launcher_door,
                cd = R.string.launcher_alert_door_fr,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = w * -0.04f, y = h * -0.06f),
            )
        }
        if (body.doorRlOpen) {
            DoorBadge(
                iconRes = R.drawable.ic_launcher_door,
                cd = R.string.launcher_alert_door_rl,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = w * 0.04f, y = h * 0.14f),
            )
        }
        if (body.doorRrOpen) {
            DoorBadge(
                iconRes = R.drawable.ic_launcher_door,
                cd = R.string.launcher_alert_door_rr,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = w * -0.04f, y = h * 0.14f),
            )
        }
        if (body.hoodOpen) {
            DoorBadge(
                iconRes = R.drawable.ic_launcher_hood,
                cd = R.string.launcher_alert_hood,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = h * 0.08f),
            )
        }
        if (body.tailgateOpen) {
            DoorBadge(
                iconRes = R.drawable.ic_launcher_trunk,
                cd = R.string.launcher_alert_trunk,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = h * -0.10f),
            )
        }
    }
}

@Composable
private fun DoorBadge(
    iconRes: Int,
    cd: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = stringResource(cd),
        modifier = modifier
            .size(34.dp)
            .background(LauncherColors.WarningAmber.copy(alpha = 0.92f), CircleShape)
            .padding(7.dp),
        colorFilter = ColorFilter.tint(LauncherColors.CanvasDark),
    )
}
