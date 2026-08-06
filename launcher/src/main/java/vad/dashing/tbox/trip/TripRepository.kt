package vad.dashing.tbox.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stub for standalone launcher — trips live in TBox Monitor.
 * [CanFramesProcess] gates fuel calibration on [activeTrip]; leaving it null skips that path.
 */
object TripRepository {
    private val _activeTrip = MutableStateFlow<Any?>(null)
    val activeTrip: StateFlow<Any?> = _activeTrip.asStateFlow()
}
