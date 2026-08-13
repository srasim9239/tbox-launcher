package vad.dashing.tbox.ui.launcher

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import vad.dashing.tbox.DonationLinks
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxTitle

/**
 * Minimal QR block for the About console: title + QR per entry (symmetric columns).
 */
@Composable
fun LauncherDonationsSection(
    entries: List<DonationLinks.Entry>,
    modifier: Modifier = Modifier,
    @StringRes titleRes: Int = R.string.launcher_donate_title,
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.tboxTitle,
            color = LauncherColors.TextPrimary,
        )
        entries.forEach { entry ->
            val qr = rememberQrCode(entry.url, 120)
            if (qr != null) {
                Image(
                    bitmap = qr,
                    contentDescription = entry.title,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberQrCode(content: String, sizeDp: Int): ImageBitmap? {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val sizePx = remember(content, sizeDp, density) { (sizeDp * density).toInt().coerceAtLeast(64) }
    return remember(content, sizePx) {
        runCatching {
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).also { bmp ->
                for (x in 0 until sizePx) {
                    for (y in 0 until sizePx) {
                        bmp[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    }
                }
            }.asImageBitmap()
        }.getOrNull()
    }
}
