package vad.dashing.tbox

import android.app.Activity
import android.content.Context
import android.content.Intent

object MainActivityIntentHelper {
    fun createBringToFrontIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

    fun applyExternalAppLaunchFlags(intent: Intent, context: Context) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
