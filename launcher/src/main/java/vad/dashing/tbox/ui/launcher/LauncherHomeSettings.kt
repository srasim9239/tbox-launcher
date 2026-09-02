package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import vad.dashing.tbox.R

@Composable
internal fun LauncherHomeSettingsContent() {
    val context = LocalContext.current
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_vs_dark_theme),
        active = LauncherThemeState.darkTheme,
        onClick = { LauncherThemeState.setDarkTheme(context, !LauncherThemeState.darkTheme) },
    )
    val miniPlayerRevision by LauncherAppConfigStore.mediaMiniPlayerRevisionFlow
        .collectAsStateWithLifecycle()
    val miniPlayerVisible = remember(context, miniPlayerRevision) {
        LauncherAppConfigStore.mediaMiniPlayerVisible(context)
    }
    LauncherSettingsToggleRow(
        label = stringResource(R.string.launcher_media_mini_player_title),
        active = miniPlayerVisible,
        onClick = {
            LauncherAppConfigStore.setMediaMiniPlayerVisible(context, !miniPlayerVisible)
        },
    )
    Text(
        text = stringResource(R.string.launcher_media_mini_player_desc),
        color = LauncherColors.TextMuted,
        fontSize = 12.sp,
    )
    MediaCardOpacitySlider()
    DockIconScaleSlider()
    CruisePresetsSettings()
}

@Composable
private fun MediaCardOpacitySlider() {
    val context = LocalContext.current
    var alpha by remember {
        mutableFloatStateOf(LauncherAppConfigStore.mediaCardAlpha(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_media_card_opacity_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(alpha * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_media_card_opacity_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = alpha,
            onValueChange = { next ->
                alpha = next
                LauncherAppConfigStore.setMediaCardAlpha(context, next)
            },
            valueRange = MEDIA_CARD_ALPHA_MIN..MEDIA_CARD_ALPHA_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun DockIconScaleSlider() {
    val context = LocalContext.current
    var scale by remember {
        mutableFloatStateOf(LauncherAppConfigStore.dockIconScale(context))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_dock_icon_scale_title),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = "${(scale * 100f).roundToInt()}%",
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Text(
            text = stringResource(R.string.launcher_dock_icon_scale_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        Slider(
            value = scale,
            onValueChange = { next ->
                scale = next
                LauncherAppConfigStore.setDockIconScale(context, next)
            },
            valueRange = DOCK_ICON_SCALE_MIN..DOCK_ICON_SCALE_MAX,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun CruisePresetsSettings() {
    val context = LocalContext.current
    val revision by LauncherAppConfigStore.cruisePresetsRevisionFlow.collectAsStateWithLifecycle()
    val presets = remember(context, revision) {
        LauncherAppConfigStore.cruisePresetsKmh(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.launcher_cruise_presets_title),
            color = LauncherColors.TextPrimary,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.launcher_cruise_presets_desc),
            color = LauncherColors.TextMuted,
            fontSize = 12.sp,
        )
        presets.forEachIndexed { index, kmh ->
            CruisePresetSliderRow(
                index = index,
                kmh = kmh,
                onChange = { next ->
                    LauncherAppConfigStore.setCruisePresetKmh(context, index, next)
                },
            )
        }
    }
}

@Composable
private fun CruisePresetSliderRow(
    index: Int,
    kmh: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.launcher_cruise_preset_n, index + 1),
                color = LauncherColors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                text = stringResource(R.string.launcher_cruise_preset_value, kmh),
                color = LauncherColors.TextSecondary,
                fontSize = 16.sp,
            )
        }
        Slider(
            value = kmh.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / CRUISE_PRESET_STEP_KMH).roundToInt() * CRUISE_PRESET_STEP_KMH
                onChange(snapped.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH))
            },
            valueRange = CRUISE_PRESET_MIN_KMH.toFloat()..CRUISE_PRESET_MAX_KMH.toFloat(),
            steps = ((CRUISE_PRESET_MAX_KMH - CRUISE_PRESET_MIN_KMH) / CRUISE_PRESET_STEP_KMH) - 1,
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.AccentCyan,
                activeTrackColor = LauncherColors.AccentCyan,
                inactiveTrackColor = LauncherColors.TextMuted,
            ),
        )
    }
}
