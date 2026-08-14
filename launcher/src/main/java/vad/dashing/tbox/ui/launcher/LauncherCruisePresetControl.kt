package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption

private val CruiseChipWidth = 52.dp
private val CruiseChipHeight = 36.dp
private val CruiseChipGap = 6.dp

@Composable
fun LauncherCruisePresetControl(
    canViewModel: CanDataViewModel,
    adas: LauncherAdasState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val presetsRevision by LauncherAppConfigStore.cruisePresetsRevisionFlow
        .collectAsStateWithLifecycle()
    val presets = remember(context, presetsRevision) {
        LauncherAppConfigStore.cruisePresetsKmh(context)
    }
    val tboxCruise by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val setSpeed = adas.accSetSpeedKmh
        ?: tboxCruise?.toInt()?.takeIf { it > 0 }
    val engaged = adas.accActive || adas.accStandby || (setSpeed != null && setSpeed > 0)
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(engaged) {
        if (!engaged) expanded = false
    }
    if (!engaged) return
    val tint = LauncherColors.AccentCyan

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = CruiseChipWidth, height = CruiseChipHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.18f))
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            if (setSpeed != null) {
                Text(
                    text = setSpeed.toString(),
                    style = MaterialTheme.typography.tboxCaption,
                    color = tint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_cruise),
                    contentDescription = stringResource(R.string.launcher_cruise_presets),
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(tint),
                )
            }
        }
        if (expanded) {
            Popup(
                alignment = Alignment.CenterEnd,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true, clippingEnabled = false),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CruiseChipGap),
                ) {
                    presets.forEach { kmh ->
                        CruisePresetChip(
                            label = kmh.toString(),
                            selected = setSpeed == kmh,
                            onClick = {
                                LauncherCruisePresetController.applyPreset(kmh)
                                expanded = false
                            },
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .size(width = CruiseChipWidth, height = CruiseChipHeight)
                            .clickable { expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CruisePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) LauncherColors.AccentCyan else LauncherColors.LeftTextPrimary
    Box(
        modifier = Modifier
            .size(width = CruiseChipWidth, height = CruiseChipHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) tint.copy(alpha = 0.22f) else LauncherColors.LeftPanelCard,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
