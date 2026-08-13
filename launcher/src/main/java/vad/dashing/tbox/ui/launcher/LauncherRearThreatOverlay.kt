package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Soft side/rear threat highlights around the 3D car (BSD / RCTA / DOW / RCW).
 * One filled glow per side — no double ring/stroke that looked like two circles.
 */
@Composable
fun LauncherRearThreatOverlay(
    threats: LauncherRearThreats,
    modifier: Modifier = Modifier,
) {
    if (!threats.hasAny) return
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRearThreats(threats)
    }
}

private fun DrawScope.drawRearThreats(threats: LauncherRearThreats) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.58f
    val bodyHalfW = w * 0.18f
    val bodyHalfH = h * 0.22f

    fun fillColor(level: LauncherRearThreatLevel): Color? = when (level) {
        LauncherRearThreatLevel.Alert -> Color(0xFFEF4444)
        LauncherRearThreatLevel.Caution -> Color(0xFFF59E0B)
        LauncherRearThreatLevel.Off -> null
    }

    listOf(
        threats.left to -1f,
        threats.right to 1f,
    ).forEach { (level, side) ->
        val base = fillColor(level) ?: return@forEach
        val zoneW = w * 0.18f
        val zoneH = h * 0.24f
        // BSD sits on the diagonal rear-quarter near the road edge: further out from
        // the body and a bit lower than the mid-body, so it reads as a side-rear
        // threat rather than a wall beside the car.
        val left = cx + side * (bodyHalfW * 1.55f) - if (side < 0f) zoneW else 0f
        val top = cy + zoneH * 0.55f
        val center = Offset(left + zoneW / 2f, top + zoneH / 2f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    base.copy(alpha = if (level == LauncherRearThreatLevel.Alert) 0.55f else 0.42f),
                    base.copy(alpha = 0.18f),
                    Color.Transparent,
                ),
                center = center,
                radius = maxOf(zoneW, zoneH) * 0.72f,
            ),
            topLeft = Offset(left, top),
            size = Size(zoneW, zoneH),
        )
        // Hard RCTA: small approaching-car marker inside the same zone.
        val rcta = if (side < 0f) threats.rctaLeft else threats.rctaRight
        if (rcta == LauncherRearThreatLevel.Alert) {
            val carW = w * 0.07f
            val carH = h * 0.05f
            drawRoundRect(
                color = base.copy(alpha = 0.9f),
                topLeft = Offset(center.x - carW / 2f, center.y + zoneH * 0.18f),
                size = Size(carW, carH),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
    }

    fillColor(threats.rcw)?.let { base ->
        val rw = bodyHalfW * 1.45f
        val rh = h * 0.07f
        val top = cy + bodyHalfH * 0.85f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 0.5f),
                    base.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(cx - rw / 2f, top),
            size = Size(rw, rh),
            cornerRadius = CornerRadius(10f, 10f),
        )
    }
}
