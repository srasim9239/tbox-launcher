package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel

private val StatusIconTint = LauncherColors.TextSecondary

@Composable
internal fun LauncherHeaderStatusIcons(
    tboxViewModel: TboxViewModel,
    modifier: Modifier = Modifier,
) {
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val netState by tboxViewModel.netState.collectAsStateWithLifecycle()
    val locValues by tboxViewModel.locValues.collectAsStateWithLifecycle()
    val wifiConnected = rememberWifiConnected()
    val wifiLevel = rememberWifiLevel(wifiConnected)
    val networkType = mobileNetworkTypeLabel(netState.netStatus)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wifiConnected) {
            WifiArcsIcon(level = wifiLevel.coerceIn(1, 4), tint = StatusIconTint)
        }
        if (tboxConnected) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (networkType != null) {
                    Text(
                        text = networkType,
                        color = StatusIconTint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val signalRes = when (netState.signalLevel.coerceIn(0, 4)) {
                    4 -> R.drawable.signal_4
                    3 -> R.drawable.signal_3
                    2 -> R.drawable.signal_2
                    1 -> R.drawable.signal_1
                    else -> R.drawable.signal_0
                }
                Image(
                    painter = painterResource(signalRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(StatusIconTint),
                )
            }
            if (locValues.updateTime != null && !locValues.hasGpsFix) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_gps_off),
                    contentDescription = stringResource(R.string.launcher_gps_no_fix),
                    modifier = Modifier.size(17.dp),
                    colorFilter = ColorFilter.tint(Color(0xFFEF4444)),
                )
            }
        }
    }
}

@Composable
private fun WifiArcsIcon(
    level: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.86f
        val stroke = size.minDimension * 0.13f
        drawCircle(
            color = tint,
            radius = stroke * 0.55f,
            center = Offset(cx, cy),
        )
        listOf(
            0.30f to 2,
            0.52f to 3,
            0.74f to 4,
        ).forEach { (frac, need) ->
            val radius = size.minDimension * frac
            val active = level >= need
            drawArc(
                color = tint.copy(alpha = if (active) 1f else 0.22f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private fun mobileNetworkTypeLabel(netStatus: String): String? = when (netStatus.uppercase()) {
    "2G", "3G", "4G", "5G", "LTE" -> netStatus.uppercase()
    else -> null
}

@Composable
private fun rememberWifiConnected(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var wifiConnected by remember { mutableStateOf(readWifiConnected(connectivityManager)) }
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                wifiConnected = readWifiConnected(connectivityManager)
            }

            override fun onLost(network: android.net.Network) {
                wifiConnected = readWifiConnected(connectivityManager)
            }

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                wifiConnected = readWifiConnected(connectivityManager)
            }
        }
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }
    return wifiConnected
}

@Composable
private fun rememberWifiLevel(wifiConnected: Boolean): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    DisposableEffect(wifiConnected) {
        if (!wifiConnected) {
            level = 0
            onDispose { }
        } else {
            level = readWifiSignalLevel(context)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    level = readWifiSignalLevel(context)
                    handler.postDelayed(this, 3000L)
                }
            }
            handler.postDelayed(runnable, 3000L)
            onDispose { handler.removeCallbacks(runnable) }
        }
    }
    return if (wifiConnected) level else 0
}

private fun readWifiConnected(connectivityManager: ConnectivityManager): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

@Suppress("DEPRECATION")
private fun readWifiSignalLevel(context: Context): Int {
    return runCatching {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return 0
        val info = wifiManager.connectionInfo ?: return 0
        if (info.networkId == -1) return 0
        WifiManager.calculateSignalLevel(info.rssi, 5).coerceIn(0, 4)
    }.getOrDefault(0)
}
