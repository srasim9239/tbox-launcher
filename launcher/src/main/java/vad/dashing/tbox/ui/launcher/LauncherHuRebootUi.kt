package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.HuRebootAfterUpdate
import vad.dashing.tbox.HuRebootAfterUpdateSnapshot
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.rememberWrappedOnClick
import vad.dashing.tbox.ui.requestHeadUnitReboot
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxHeadline

internal data class HuRebootActionState(
    val enabled: Boolean,
    val showUnavailableHint: Boolean,
    val onUsed: () -> Unit,
)

@Composable
internal fun rememberHuRebootActionEnabled(): HuRebootActionState {
    val headUnitCanMode by UniversalCanRepository.mode.collectAsStateWithLifecycle()
    val mbCanAvailability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    LaunchedEffect(headUnitCanMode) {
        UniversalCanRepository.setMode(headUnitCanMode)
        UniversalCanRepository.warmUpAvailabilityForUi()
    }
    var cooldownOk by remember { mutableStateOf(true) }
    LaunchedEffect(cooldownOk) {
        if (!cooldownOk) {
            delay(300_000L)
            cooldownOk = true
        }
    }
    val android9 = headUnitCanMode == HeadUnitCanMode.Android9MbCan
    return HuRebootActionState(
        enabled = cooldownOk &&
            android9 &&
            mbCanAvailability is MbCanAvailability.Available,
        showUnavailableHint = !android9,
        onUsed = { cooldownOk = false },
    )
}

@Composable
internal fun LauncherHuRebootButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = rememberWrappedOnClick(onClick),
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.button_reboot_hu),
            style = MaterialTheme.typography.tboxButton,
        )
    }
}

@Composable
internal fun LauncherHuRebootStartupDialog(
    settingsManager: SettingsManager,
    currentVersionCode: Long,
) {
    val snapshot by settingsManager.huRebootAfterUpdateFlow.collectAsStateWithLifecycle(
        initialValue = HuRebootAfterUpdateSnapshot(),
    )
    val ui = remember(snapshot, currentVersionCode) {
        HuRebootAfterUpdate.uiState(snapshot, currentVersionCode)
    }
    var dialogVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rebootAction = rememberHuRebootActionEnabled()
    val rebootEnabled = rebootAction.enabled
    val onRebootUsed = rebootAction.onUsed

    LaunchedEffect(ui.shouldOpenStartupPrompt) {
        if (ui.shouldOpenStartupPrompt) {
            dialogVisible = true
            settingsManager.markHuRebootStartupPromptShown(currentVersionCode)
        }
    }

    if (!dialogVisible) return

    LauncherDarkAlertDialog(
        onDismissRequest = { dialogVisible = false },
        title = {
            Text(
                text = stringResource(R.string.update_reboot_hu_title),
                style = MaterialTheme.typography.tboxHeadline,
                color = LauncherColors.TextPrimary,
            )
        },
        confirmButton = {
            TextButton(
                onClick = rememberWrappedOnClick {
                    if (!rebootEnabled) return@rememberWrappedOnClick
                    onRebootUsed()
                    dialogVisible = false
                    scope.launch {
                        settingsManager.markHuRebootedAfterUpdate(currentVersionCode)
                        requestHeadUnitReboot(context)
                    }
                },
                enabled = rebootEnabled,
            ) {
                Text(
                    text = stringResource(R.string.button_reboot_hu),
                    color = LauncherColors.TextPrimary,
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = rememberWrappedOnClick { dialogVisible = false }) {
                Text(
                    text = stringResource(R.string.update_reboot_hu_later),
                    color = LauncherColors.TextSecondary,
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        },
    )
}
