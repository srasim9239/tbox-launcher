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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Body-local marks: DOW = door leaf, RCTA = incoming bar, RCW = rear wash.
 * BSD is a chip in the ADAS strip, not a car sprite on the road.
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
    val bodyHalfW = w * 0.145f
    val bodyHalfH = h * 0.20f

    fun fillColor(level: LauncherRearThreatLevel): Color? = when (level) {
        LauncherRearThreatLevel.Alert -> Color(0xFFEF4444)
        LauncherRearThreatLevel.Caution -> Color(0xFFF59E0B)
        LauncherRearThreatLevel.Off -> null
    }

    listOf(-1f, 1f).forEach { side ->
        val dow = if (side < 0f) threats.dowLeft else threats.dowRight
        val rcta = if (side < 0f) threats.rctaLeft else threats.rctaRight
        fillColor(dow)?.let { drawDowDoor(cx, cy, bodyHalfW, bodyHalfH, side, it, dow) }
        if (rcta == LauncherRearThreatLevel.Alert) {
            fillColor(rcta)?.let { drawRctaBar(cx, cy, bodyHalfW, bodyHalfH, side, it) }
        }
    }

    fillColor(threats.rcw)?.let { base ->
        val rw = bodyHalfW * 1.55f
        val rh = h * 0.055f
        val top = cy + bodyHalfH * 0.92f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 0.55f),
                    base.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(cx - rw / 2f, top),
            size = Size(rw, rh),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}

private fun DrawScope.drawDowDoor(
    cx: Float,
    cy: Float,
    bodyHalfW: Float,
    bodyHalfH: Float,
    side: Float,
    color: Color,
    level: LauncherRearThreatLevel,
) {
    val hingeX = cx + side * bodyHalfW * 0.78f
    val leafW = bodyHalfW * 0.55f
    val leafH = bodyHalfH * 0.72f
    val top = cy - leafH * 0.15f
    val outerX = hingeX + side * leafW
    val path = Path().apply {
        moveTo(hingeX, top)
        lineTo(outerX, top + leafH * 0.08f)
        lineTo(outerX, top + leafH * 0.92f)
        lineTo(hingeX, top + leafH)
        close()
    }
    val alpha = if (level == LauncherRearThreatLevel.Alert) 0.50f else 0.34f
    drawPath(path, color = color.copy(alpha = alpha))
    drawPath(path, color = color.copy(alpha = 0.88f), style = Stroke(width = 2.4f))
    drawCircle(
        color = color.copy(alpha = 0.9f),
        radius = 3.2f,
        center = Offset(outerX - side * leafW * 0.22f, top + leafH * 0.52f),
    )
}

private fun DrawScope.drawRctaBar(
    cx: Float,
    cy: Float,
    bodyHalfW: Float,
    bodyHalfH: Float,
    side: Float,
    color: Color,
) {
    val barW = bodyHalfW * 0.42f
    val barH = bodyHalfH * 0.16f
    val left = cx + side * bodyHalfW * 1.05f - if (side < 0f) barW else 0f
    val top = cy + bodyHalfH * 0.78f
    drawRoundRect(
        color = color.copy(alpha = 0.88f),
        topLeft = Offset(left, top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(4f, 4f),
    )
}
