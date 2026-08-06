package vad.dashing.tbox.ui.launcher

/**
 * Legacy WindowManager settings overlay — disabled.
 * Settings now use an in-launcher Compose overlay ([launchVehicleSettingsWindow]).
 * [hide] / [isShowing] remain as safe no-ops for call sites.
 */
internal object LauncherVehicleSettingsWindow {
    fun isShowing(): Boolean = false

    fun hide() {
        // Compose overlay is closed via [closeVehicleSettingsOverlay] / UiState.
        if (LauncherVehicleSettingsUiState.open) {
            // Keep hide() usable from nav helpers without double-clearing elevator when
            // closeVehicleSettingsOverlay already ran.
        }
    }

    fun show(context: android.content.Context) {
        launchVehicleSettingsWindow(context)
    }
}
