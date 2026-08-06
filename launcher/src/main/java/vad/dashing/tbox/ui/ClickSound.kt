package vad.dashing.tbox.ui

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** When true, play [SoundEffectConstants.CLICK] for wrapped taps (see [rememberWrappedOnClick]). */
val LocalClickSoundEnabled = staticCompositionLocalOf { false }

@Composable
fun rememberPlaySystemClickSound(): () -> Unit {
    val enabled = LocalClickSoundEnabled.current
    val view = LocalView.current
    return remember(enabled, view) {
        {
            if (enabled && view.isSoundEffectsEnabled) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
            }
        }
    }
}

@Composable
fun rememberWrappedOnClick(onClick: () -> Unit): () -> Unit {
    val play = rememberPlaySystemClickSound()
    return remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
}
