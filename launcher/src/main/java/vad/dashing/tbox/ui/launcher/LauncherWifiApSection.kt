package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R

/**
 * SoftAP credentials + connection QR, inside the System section of vehicle settings.
 * Data comes from mbCAN string properties written by AIService (see [LauncherWifiApRepository]).
 */
@Composable
internal fun LauncherWifiApBlock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wifiAp by LauncherWifiApRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        LauncherWifiApRepository.init(context)
        LauncherWifiApRepository.refresh(context)
    }
    val info = wifiAp ?: return
    if (info.ssid.isBlank()) return

    val payload = remember(info) { LauncherWifiApRepository.wifiQrPayload(info) }
    val qr = rememberLauncherQrCode(payload, 156)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.CardDark)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LauncherSettingsValueRow(stringResource(R.string.launcher_wifi_ap_ssid), info.ssid)
        if (info.password.isNotEmpty()) {
            LauncherSettingsValueRow(stringResource(R.string.launcher_wifi_ap_password), info.password)
        }
        if (info.mac.isNotEmpty()) {
            LauncherSettingsValueRow(stringResource(R.string.launcher_wifi_ap_mac), info.mac)
        }
        if (qr != null) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(164.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = qr,
                    contentDescription = stringResource(R.string.launcher_wifi_ap_qr_cd),
                    modifier = Modifier.size(156.dp),
                )
            }
            Text(
                text = stringResource(R.string.launcher_wifi_ap_qr_hint),
                color = LauncherColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
}
