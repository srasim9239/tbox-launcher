package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Slim TBox façade — connection/theme/net are idle without the UDP proxy. */
class TboxViewModel : ViewModel() {
    val tboxConnected: StateFlow<Boolean> = TboxRepository.tboxConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val netState: StateFlow<NetState> = TboxRepository.netState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetState())

    val locValues: StateFlow<LocValues> = TboxRepository.locValues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocValues())

    val currentTheme: StateFlow<Int> = TboxRepository.currentTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)
}

fun valueToString(
    value: Any?,
    accuracy: Int = 1,
    booleanTrue: String = "да",
    booleanFalse: String = "нет",
    default: String = "",
): String {
    if (value == null) return default
    return when (value) {
        is Int -> value.toString()
        is UInt -> value.toString()
        is Float, is Double -> when (accuracy) {
            0 -> String.format(Locale.getDefault(), "%.0f", value)
            1 -> String.format(Locale.getDefault(), "%.1f", value)
            2 -> String.format(Locale.getDefault(), "%.2f", value)
            3 -> String.format(Locale.getDefault(), "%.3f", value)
            4 -> String.format(Locale.getDefault(), "%.4f", value)
            5 -> String.format(Locale.getDefault(), "%.5f", value)
            6 -> String.format(Locale.getDefault(), "%.6f", value)
            else -> String.format(Locale.getDefault(), "%.1f", value)
        }
        is Boolean -> if (value) booleanTrue else booleanFalse
        is String -> value
        else -> ""
    }
}
