package vad.dashing.tbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import vad.dashing.tbox.ui.launcher.LauncherSettingsScreen
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.update.InstallPermissionHelper
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.update.UpdateViewModelFactory

/**
 * About / OTA console opened from the launcher «Настроить» button.
 */
class MainActivity : ComponentActivity() {
    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val updateViewModel: UpdateViewModel by viewModels {
        UpdateViewModelFactory(application, settingsManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TboxAppTheme(theme = 2) {
                LauncherSettingsScreen(
                    updateViewModel = updateViewModel,
                    onOpenInstallPermissionSettings = {
                        startActivity(
                            InstallPermissionHelper.createUnknownSourcesSettingsIntent(this),
                        )
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}
