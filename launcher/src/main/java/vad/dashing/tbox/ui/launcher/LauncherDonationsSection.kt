package vad.dashing.tbox.ui.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import vad.dashing.tbox.DonationLinks
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxTitle

/**
 * «Поддержать проект» block for the About console. On a head unit the practical flow is
 * scanning the QR with a phone; the link can also be copied or opened in a browser (if any).
 */
@Composable
fun LauncherDonationsSection(entries: List<DonationLinks.Entry>) {
    if (entries.isEmpty()) return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.launcher_donate_copied)

    Text(
        text = stringResource(R.string.launcher_donate_title),
        style = MaterialTheme.typography.tboxTitle,
        color = LauncherColors.TextPrimary,
    )
    Text(
        text = stringResource(R.string.launcher_donate_subtitle),
        style = MaterialTheme.typography.tboxBody,
        color = LauncherColors.TextSecondary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LauncherColors.CardDark.copy(alpha = 0.55f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val qr = rememberQrCode(entry.url, 84)
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = entry.title,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(6.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.tboxBody,
                        color = LauncherColors.TextPrimary,
                    )
                    if (entry.subtitle.isNotBlank()) {
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.tboxBody,
                            color = LauncherColors.TextSecondary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(entry.url))
                                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.launcher_donate_copy),
                                style = MaterialTheme.typography.tboxButton,
                                color = LauncherColors.AccentCyan,
                            )
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.launcher_donate_open),
                                style = MaterialTheme.typography.tboxButton,
                                color = LauncherColors.AccentCyan,
                            )
                        }
                    }
                }
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
