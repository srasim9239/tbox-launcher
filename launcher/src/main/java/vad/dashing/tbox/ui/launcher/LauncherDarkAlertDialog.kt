package vad.dashing.tbox.ui.launcher

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/**
 * AlertDialog styled for the dark right-panel / dock UI.
 * Material theme alone is not enough: option rows use [LauncherColors.TextPrimary]
 * (light), so the container must stay dark.
 */
@Composable
internal fun LauncherDarkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        containerColor = LauncherColors.CardDark,
        titleContentColor = LauncherColors.TextPrimary,
        textContentColor = LauncherColors.TextPrimary,
        properties = properties,
    )
}
