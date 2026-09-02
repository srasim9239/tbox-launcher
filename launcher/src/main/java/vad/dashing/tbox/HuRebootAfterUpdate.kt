package vad.dashing.tbox

/**
 * After an APK replace the HU often needs a reboot (mbCAN / HOME / 3D).
 * Persist upgrade vs. already-rebooted so HOME does not nag on every start.
 */
data class HuRebootAfterUpdateSnapshot(
    val lastSeenVersionCode: Long = 0L,
    val pendingRebootVersionCode: Long = 0L,
    val upgradeDetectedElapsedMs: Long = 0L,
    val startupPromptShownForVersion: Long = 0L,
    val awaitingInstallVersionCode: Long = 0L,
)

data class HuRebootAfterUpdateUiState(
    val recommendReboot: Boolean,
    val shouldOpenStartupPrompt: Boolean,
)

data class HuRebootAfterUpdateStep(
    val snapshot: HuRebootAfterUpdateSnapshot,
    val ui: HuRebootAfterUpdateUiState,
)

object HuRebootAfterUpdate {
    /** Fallback when DataStore has no last-seen code (first launch of this feature). */
    const val RECENT_UPDATE_WINDOW_MS: Long = 48L * 60L * 60L * 1000L

    fun uiState(
        snapshot: HuRebootAfterUpdateSnapshot,
        currentVersionCode: Long,
    ): HuRebootAfterUpdateUiState {
        val recommend = snapshot.pendingRebootVersionCode == currentVersionCode &&
            snapshot.pendingRebootVersionCode > 0L
        return HuRebootAfterUpdateUiState(
            recommendReboot = recommend,
            shouldOpenStartupPrompt = recommend &&
                snapshot.startupPromptShownForVersion < currentVersionCode,
        )
    }

    fun onAppStart(
        currentVersionCode: Long,
        elapsedRealtimeMs: Long,
        lastUpdateTimeMs: Long,
        firstInstallTimeMs: Long,
        nowWallClockMs: Long,
        previous: HuRebootAfterUpdateSnapshot,
    ): HuRebootAfterUpdateStep {
        var lastSeen = previous.lastSeenVersionCode
        var pending = previous.pendingRebootVersionCode
        var detectedElapsed = previous.upgradeDetectedElapsedMs
        var promptShownFor = previous.startupPromptShownForVersion
        var awaiting = previous.awaitingInstallVersionCode

        val recentlyUpdated = lastUpdateTimeMs != firstInstallTimeMs &&
            nowWallClockMs >= lastUpdateTimeMs &&
            nowWallClockMs - lastUpdateTimeMs < RECENT_UPDATE_WINDOW_MS
        val upgraded = when {
            lastSeen > 0L && currentVersionCode > lastSeen -> true
            awaiting > 0L && currentVersionCode >= awaiting &&
                (lastSeen == 0L || currentVersionCode > lastSeen) -> true
            lastSeen == 0L && recentlyUpdated -> true
            else -> false
        }

        if (upgraded) {
            lastSeen = currentVersionCode
            pending = currentVersionCode
            detectedElapsed = elapsedRealtimeMs
            awaiting = 0L
        } else if (lastSeen == 0L) {
            lastSeen = currentVersionCode
        }

        // elapsedRealtime restarts at 0 after a HU reboot; DataStore survives.
        if (pending == currentVersionCode &&
            detectedElapsed > 0L &&
            elapsedRealtimeMs < detectedElapsed
        ) {
            pending = 0L
        }

        val snapshot = HuRebootAfterUpdateSnapshot(
            lastSeenVersionCode = lastSeen,
            pendingRebootVersionCode = pending,
            upgradeDetectedElapsedMs = detectedElapsed,
            startupPromptShownForVersion = promptShownFor,
            awaitingInstallVersionCode = awaiting,
        )
        return HuRebootAfterUpdateStep(
            snapshot = snapshot,
            ui = uiState(snapshot, currentVersionCode),
        )
    }

    fun markAwaitingInstall(
        previous: HuRebootAfterUpdateSnapshot,
        installVersionCode: Long,
    ): HuRebootAfterUpdateSnapshot = previous.copy(
        awaitingInstallVersionCode = installVersionCode,
    )

    fun markStartupPromptShown(
        previous: HuRebootAfterUpdateSnapshot,
        currentVersionCode: Long,
    ): HuRebootAfterUpdateSnapshot = previous.copy(
        startupPromptShownForVersion = currentVersionCode,
    )

    fun markRebooted(
        previous: HuRebootAfterUpdateSnapshot,
        currentVersionCode: Long,
    ): HuRebootAfterUpdateSnapshot = previous.copy(
        pendingRebootVersionCode = 0L,
        startupPromptShownForVersion = currentVersionCode,
        awaitingInstallVersionCode = 0L,
    )
}
