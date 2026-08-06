package vad.dashing.tbox.ui.launcher

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

/** Shared QR bitmap generator (donations, Wi-Fi AP, ...). Null when encoding fails. */
@Composable
internal fun rememberLauncherQrCode(content: String?, sizeDp: Int): ImageBitmap? {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val sizePx = remember(content, sizeDp, density) { (sizeDp * density).toInt().coerceAtLeast(64) }
    return remember(content, sizePx) {
        if (content.isNullOrBlank()) return@remember null
        runCatching {
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).also { bmp ->
                for (x in 0 until sizePx) {
                    for (y in 0 until sizePx) {
                        bmp.setPixel(
                            x, y,
                            if (matrix[x, y]) android.graphics.Color.BLACK
                            else android.graphics.Color.WHITE,
                        )
                    }
                }
            }.asImageBitmap()
        }.getOrNull()
    }
}
