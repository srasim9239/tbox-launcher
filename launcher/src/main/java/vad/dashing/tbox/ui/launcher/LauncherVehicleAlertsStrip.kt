package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R

/**
 * Telltale icons (seat belts, doors, ICM faults) — icon-only, only when active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherVehicleAlertsStrip(
    modifier: Modifier = Modifier,
) {
    val state by LauncherVehicleAlertsRepository.state.collectAsStateWithLifecycle()
    // Body open statuses are shown as badges on the car, not next to ADAS.
    val alerts = state.alerts.filterNot { it.id.isBodyOpenAlert }
    if (alerts.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        alerts.forEach { alert ->
            LauncherVehicleAlertIcon(alert)
        }
    }
}

private val LauncherAlertId.isBodyOpenAlert: Boolean
    get() = when (this) {
        LauncherAlertId.DoorDriver,
        LauncherAlertId.DoorPassenger,
        LauncherAlertId.DoorRearLeft,
        LauncherAlertId.DoorRearRight,
        LauncherAlertId.HoodOpen,
        LauncherAlertId.TrunkOpen,
        -> true
        else -> false
    }

@Composable
private fun LauncherVehicleAlertIcon(alert: LauncherVehicleAlert) {
    val tint = when (alert.severity) {
        LauncherAlertSeverity.Critical -> LauncherColors.WarningRed
        LauncherAlertSeverity.Warning -> LauncherColors.WarningAmber
    }
    val label = stringResource(alert.id.labelRes)
    Image(
        painter = painterResource(alert.id.iconRes),
        contentDescription = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(6.dp)
            .size(18.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

private val LauncherAlertId.labelRes: Int
    get() = when (this) {
        LauncherAlertId.SeatBeltDriver -> R.string.launcher_alert_seatbelt_driver
        LauncherAlertId.SeatBeltPassenger -> R.string.launcher_alert_seatbelt_passenger
        LauncherAlertId.SeatBeltRearLeft -> R.string.launcher_alert_seatbelt_rl
        LauncherAlertId.SeatBeltRearMid -> R.string.launcher_alert_seatbelt_rm
        LauncherAlertId.SeatBeltRearRight -> R.string.launcher_alert_seatbelt_rr
        LauncherAlertId.DoorDriver -> R.string.launcher_alert_door_fl
        LauncherAlertId.DoorPassenger -> R.string.launcher_alert_door_fr
        LauncherAlertId.DoorRearLeft -> R.string.launcher_alert_door_rl
        LauncherAlertId.DoorRearRight -> R.string.launcher_alert_door_rr
        LauncherAlertId.HoodOpen -> R.string.launcher_alert_hood
        LauncherAlertId.TrunkOpen -> R.string.launcher_alert_trunk
        LauncherAlertId.TirePressure -> R.string.launcher_alert_tire
        LauncherAlertId.LowFuel -> R.string.launcher_alert_fuel
        LauncherAlertId.Speeding -> R.string.launcher_alert_speeding
        LauncherAlertId.HighTemperature -> R.string.launcher_alert_high_temp
        LauncherAlertId.PressBrake -> R.string.launcher_alert_press_brake
        LauncherAlertId.SysFault -> R.string.launcher_alert_sys_fault
        LauncherAlertId.BattFault -> R.string.launcher_alert_batt_fault
        LauncherAlertId.ChargeFault -> R.string.launcher_alert_charge_fault
        LauncherAlertId.HvFaultStop -> R.string.launcher_alert_hv_fault
        LauncherAlertId.PowerModeFail -> R.string.launcher_alert_power_mode
        LauncherAlertId.LowBatterySoc -> R.string.launcher_alert_low_soc
    }

private val LauncherAlertId.iconRes: Int
    get() = when (this) {
        LauncherAlertId.SeatBeltDriver,
        LauncherAlertId.SeatBeltPassenger,
        LauncherAlertId.SeatBeltRearLeft,
        LauncherAlertId.SeatBeltRearMid,
        LauncherAlertId.SeatBeltRearRight,
        -> R.drawable.ic_launcher_seatbelt
        LauncherAlertId.DoorDriver,
        LauncherAlertId.DoorPassenger,
        LauncherAlertId.DoorRearLeft,
        LauncherAlertId.DoorRearRight,
        -> R.drawable.ic_launcher_door
        LauncherAlertId.HoodOpen -> R.drawable.ic_launcher_hood
        LauncherAlertId.TrunkOpen -> R.drawable.ic_launcher_trunk
        LauncherAlertId.TirePressure -> R.drawable.ic_refuel_price_warning
        LauncherAlertId.LowFuel -> R.drawable.ic_launcher_fuel
        LauncherAlertId.LowBatterySoc,
        LauncherAlertId.BattFault,
        -> R.drawable.ic_launcher_battery
        else -> R.drawable.ic_notification
    }
