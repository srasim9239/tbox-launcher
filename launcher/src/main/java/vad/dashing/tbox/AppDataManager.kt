package vad.dashing.tbox

import android.content.Context

/** Unused by TeslaLauncherScreen but kept in the signature for source compatibility. */
class AppDataManager(context: Context) {
    init {
        // no-op: trip/app-data pipeline lives in TBox Monitor, not the standalone launcher
        context.applicationContext
    }
}
