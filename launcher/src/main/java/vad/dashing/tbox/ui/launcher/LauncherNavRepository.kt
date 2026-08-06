package vad.dashing.tbox.ui.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds live navigation hints from NotificationListener / Accessibility.
 * Cleared when the source notification disappears or the feature is disabled.
 */
object LauncherNavRepository {
    private val _state = MutableStateFlow(LauncherNavState())
    val state: StateFlow<LauncherNavState> = _state.asStateFlow()

    @Volatile
    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) clear()
    }

    fun applyYandexNotificationPatch(patch: LauncherNavState.() -> LauncherNavState) {
        if (!enabled) return
        _state.update { current ->
            patch(
                current.copy(
                    active = true,
                    source = LauncherNavSource.YandexNotification,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun applyAccessibilityPatch(patch: LauncherNavState.() -> LauncherNavState) {
        if (!enabled) return
        _state.update { current ->
            patch(
                current.copy(
                    active = true,
                    source = LauncherNavSource.YandexAccessibility,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun applyTwoGis(distanceText: String?, street: String?) {
        if (!enabled) return
        _state.value = LauncherNavState(
            active = true,
            source = LauncherNavSource.TwoGis,
            distanceText = distanceText?.takeIf { it.isNotBlank() },
            street = street?.takeIf { it.isNotBlank() },
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun clear() {
        _state.value = LauncherNavState()
    }

    fun clearIfSource(source: LauncherNavSource) {
        _state.update { current ->
            if (current.source == source) LauncherNavState() else current
        }
    }
}
