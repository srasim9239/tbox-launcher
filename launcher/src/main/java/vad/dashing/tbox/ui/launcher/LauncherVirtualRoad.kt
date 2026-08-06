package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

@Composable
fun LauncherVirtualRoad(
    speedKmh: Float,
    @Suppress("UNUSED_PARAMETER") steerAngleDeg: Float,
    adas: LauncherAdasState = LauncherAdasState(),
    modifier: Modifier = Modifier,
    steerPreview: Boolean = false,
    /** Show road only in Drive (D), not when the engine merely starts. */
    inDriveGear: Boolean = false,
) {
    val driveTarget = when {
        inDriveGear -> 1f
        steerPreview -> 1f
        else -> 0f
    }
    val driveBlend by animateFloatAsState(
        targetValue = driveTarget,
        animationSpec = tween(280),
        label = "roadDriveBlend",
    )

    var roadPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speedKmh, driveBlend) {
        while (true) {
            withFrameMillis {
                if (speedKmh > 0.5f && driveBlend > 0.02f) {
                    roadPhase += speedKmh * 0.03f
                }
            }
        }
    }

    if (driveBlend <= 0.01f) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = driveBlend },
    ) {
        drawVirtualRoad(
            roadPhase = roadPhase,
            speedKmh = speedKmh,
            adas = adas,
        )
    }
}

private fun DrawScope.drawVirtualRoad(
    roadPhase: Float,
    speedKmh: Float,
    adas: LauncherAdasState,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.24f
    // Keep road straight — car body no longer yaws with steering in drive mode.
    val vanishX = w / 2f
    fun halfWidthAt(t: Float): Float =
        (w * 0.075f) * (1f - t).pow(1.25f) + (w * 0.42f) * t.pow(1.05f)
    fun laneOffsetAt(t: Float): Float = halfWidthAt(t) * 0.34f
    fun yAt(t: Float): Float = horizonY + (h - horizonY) * t
    fun centerXAt(t: Float): Float = vanishX

    drawAdasLaneAssist(
        adas = adas,
        centerXAt = ::centerXAt,
        halfWidthAt = ::halfWidthAt,
        yAt = ::yAt,
    )

    val dashSpacing = (32f + speedKmh * 0.28f).coerceIn(20f, 72f)
    val phase = roadPhase % dashSpacing
    var y = horizonY + phase
    val laneColor = LauncherColors.TextSecondary.copy(alpha = 0.42f)
    while (y < h) {
        val t = ((y - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        val dashLen = (10f + t * 18f).coerceAtLeast(7f)
        val nextT = (((y + dashLen) - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                color = laneColor,
                start = Offset(centerXAt(t) + side * laneOffsetAt(t), y),
                end = Offset(centerXAt(nextT) + side * laneOffsetAt(nextT), y + dashLen),
                strokeWidth = 2f,
            )
        }
        y += dashSpacing * (0.4f + 0.6f * t)
    }

    val frontDistance = adas.frontObject.displayDistanceM
    if (adas.frontObject.valid && frontDistance != null) {
        if ((adas.accActive || adas.accStandby) && adas.timeGapLevel != null) {
            drawTimeGapBars(
                gapLevel = adas.timeGapLevel,
                objectDistanceM = frontDistance,
                centerXAt = ::centerXAt,
                halfWidthAt = ::halfWidthAt,
                yAt = ::yAt,
                alert = adas.fcwActive || adas.distanceWarning,
            )
        }
        drawFrontObject(
            adas = adas,
            distanceM = frontDistance,
            centerXAt = ::centerXAt,
            halfWidthAt = ::halfWidthAt,
            yAt = ::yAt,
        )
    } else if ((adas.accActive || adas.accStandby) && adas.timeGapLevel != null) {
        // No target object — still show the selected following distance near ego.
        drawTimeGapBars(
            gapLevel = adas.timeGapLevel,
            objectDistanceM = timeGapFallbackDistanceM(adas.timeGapLevel),
            centerXAt = ::centerXAt,
            halfWidthAt = ::halfWidthAt,
            yAt = ::yAt,
            alert = false,
        )
    }

    adas.speedLimitKmh?.let { limit ->
        drawSpeedLimitSign(
            limitKmh = limit,
            warning = adas.speedLimitWarning,
            canvasWidth = size.width,
            offsetFromRight = 14f,
            accent = if (adas.speedLimitWarning) Color(0xFFEF4444) else Color(0xFFE11D48),
            caption = null,
        )
    }
    adas.tsr.speedLimitKmh?.takeIf { adas.tsr.valid }?.let { limit ->
        // TSR sits left of SLA so both are visible when they differ (or match).
        drawSpeedLimitSign(
            limitKmh = limit,
            warning = false,
            canvasWidth = size.width,
            offsetFromRight = if (adas.speedLimitKmh != null) 64f else 14f,
            accent = Color(0xFF2563EB),
            caption = "TSR",
        )
    }

    // Rear/side threats (BSD/RCTA/DOW/RCW) are drawn only on LauncherRearThreatOverlay
    // over the 3D car — avoids a second set of circles on the road.
}

private fun DrawScope.drawAdasLaneAssist(
    adas: LauncherAdasState,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    fun laneColor(side: LauncherAdasLaneVisualization, warning: Boolean): Color = when {
        warning -> Color(0xFFEF4444).copy(alpha = 0.72f)
        side == LauncherAdasLaneVisualization.Intervention -> LauncherColors.AccentCyan.copy(alpha = 0.55f)
        side == LauncherAdasLaneVisualization.Tracking -> LauncherColors.AccentBlue.copy(alpha = 0.42f)
        else -> Color.Transparent
    }
    listOf(
        adas.leftLane to -1f,
        adas.rightLane to 1f,
    ).forEach { (lane, side) ->
        if (lane == LauncherAdasLaneVisualization.Hidden) return@forEach
        val warning = lane == LauncherAdasLaneVisualization.Warning
        val color = laneColor(lane, warning)
        if (color == Color.Transparent) return@forEach
        var prev = Offset(
            centerXAt(1f) + side * (halfWidthAt(1f) - 6f),
            yAt(1f),
        )
        for (i in 8 downTo 0) {
            val t = i / 8f
            val next = Offset(
                centerXAt(t) + side * (halfWidthAt(t) - 6f),
                yAt(t),
            )
            drawLine(color = color, start = prev, end = next, strokeWidth = if (warning) 3.5f else 2.5f)
            prev = next
        }
    }
}

/** OEM-like ACC following-distance chevrons between ego and the lead target. */
private fun DrawScope.drawTimeGapBars(
    gapLevel: Int,
    objectDistanceM: Int,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
    alert: Boolean,
) {
    val bars = (gapLevel + 1).coerceIn(1, 3)
    val objectDepth = distanceToRoadDepth(objectDistanceM)
    // Ego reference just below the object band (still above the 3D car).
    val egoDepth = (objectDepth + 0.18f).coerceAtMost(0.52f)
    val color = if (alert) Color(0xFFEF4444).copy(alpha = 0.70f) else LauncherColors.AccentCyan.copy(alpha = 0.55f)
    for (i in 0 until bars) {
        val t = egoDepth - (egoDepth - objectDepth) * ((i + 1f) / (bars + 1f))
        val cx = centerXAt(t)
        val cy = yAt(t)
        val half = halfWidthAt(t) * 0.22f
        val path = Path().apply {
            moveTo(cx - half, cy)
            lineTo(cx, cy - half * 0.55f)
            lineTo(cx + half, cy)
        }
        drawPath(path, color = color, style = Stroke(width = 2.4f))
    }
}

private fun timeGapFallbackDistanceM(gapLevel: Int): Int = when (gapLevel.coerceIn(0, 2)) {
    0 -> 18
    1 -> 28
    else -> 40
}

private fun DrawScope.drawFrontObject(
    adas: LauncherAdasState,
    distanceM: Int,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    val depth = distanceToRoadDepth(distanceM)
    val cx = centerXAt(depth)
    val cy = yAt(depth)
    val roadHalf = halfWidthAt(depth)
    val alert = adas.fcwActive || adas.distanceWarning || adas.aebHint || adas.accTakeOver
    val baseColor = if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan
    val fillColor = baseColor.copy(alpha = if (alert) 0.58f else 0.40f)
    val strokeColor = baseColor.copy(alpha = 0.92f)

    val (objW, objH) = objectSizeForType(adas.frontObject.type, roadHalf)
    drawFrontObjectSilhouette(
        type = adas.frontObject.type,
        cx = cx,
        cy = cy,
        objW = objW,
        objH = objH,
        fill = fillColor,
        stroke = strokeColor,
    )

    // Distance label under the silhouette.
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = strokeColor.toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = (11f + depth * 6f).coerceIn(11f, 15f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        "${distanceM}m",
        cx,
        cy + paint.textSize + 2f,
        paint,
    )
}

private fun DrawScope.drawFrontObjectSilhouette(
    type: LauncherAdasFrontObjectType,
    cx: Float,
    cy: Float,
    objW: Float,
    objH: Float,
    fill: Color,
    stroke: Color,
) {
    when (type) {
        LauncherAdasFrontObjectType.Bus -> {
            // Tall coach with window strip.
            val body = Size(objW, objH)
            val topLeft = Offset(cx - objW / 2f, cy - objH)
            drawRoundRect(fill, topLeft, body, CornerRadius(objW * 0.12f, objW * 0.12f))
            drawRoundRect(stroke, topLeft, body, CornerRadius(objW * 0.12f, objW * 0.12f), Stroke(2f))
            val winY = cy - objH * 0.72f
            drawRoundRect(
                color = stroke.copy(alpha = 0.55f),
                topLeft = Offset(cx - objW * 0.38f, winY),
                size = Size(objW * 0.76f, objH * 0.18f),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
        LauncherAdasFrontObjectType.Truck -> {
            // Cab + cargo box.
            val cabW = objW * 0.55f
            val cabH = objH * 0.55f
            val boxW = objW
            val boxH = objH * 0.72f
            drawRoundRect(
                fill,
                Offset(cx - boxW / 2f, cy - boxH),
                Size(boxW, boxH),
                CornerRadius(objW * 0.08f, objW * 0.08f),
            )
            drawRoundRect(
                fill,
                Offset(cx - cabW / 2f, cy - objH),
                Size(cabW, cabH),
                CornerRadius(objW * 0.10f, objW * 0.10f),
            )
            drawRoundRect(
                stroke,
                Offset(cx - boxW / 2f, cy - boxH),
                Size(boxW, boxH),
                CornerRadius(objW * 0.08f, objW * 0.08f),
                Stroke(2f),
            )
            drawRoundRect(
                stroke,
                Offset(cx - cabW / 2f, cy - objH),
                Size(cabW, cabH),
                CornerRadius(objW * 0.10f, objW * 0.10f),
                Stroke(2f),
            )
        }
        LauncherAdasFrontObjectType.Car -> {
            // Sedan: body + cabin trapezoid.
            val bodyH = objH * 0.55f
            val cabinH = objH * 0.48f
            drawRoundRect(
                fill,
                Offset(cx - objW / 2f, cy - bodyH),
                Size(objW, bodyH),
                CornerRadius(objW * 0.22f, objW * 0.22f),
            )
            val cabinPath = Path().apply {
                moveTo(cx - objW * 0.28f, cy - bodyH)
                lineTo(cx - objW * 0.18f, cy - bodyH - cabinH)
                lineTo(cx + objW * 0.18f, cy - bodyH - cabinH)
                lineTo(cx + objW * 0.28f, cy - bodyH)
                close()
            }
            drawPath(cabinPath, color = fill)
            drawPath(cabinPath, color = stroke, style = Stroke(width = 2f))
            drawRoundRect(
                stroke,
                Offset(cx - objW / 2f, cy - bodyH),
                Size(objW, bodyH),
                CornerRadius(objW * 0.22f, objW * 0.22f),
                Stroke(2f),
            )
        }
        LauncherAdasFrontObjectType.Motorcycle, LauncherAdasFrontObjectType.Bicycle -> {
            val wheelR = objW * 0.16f
            drawCircle(fill, wheelR, Offset(cx - objW * 0.28f, cy - wheelR))
            drawCircle(fill, wheelR, Offset(cx + objW * 0.28f, cy - wheelR))
            drawCircle(stroke, wheelR, Offset(cx - objW * 0.28f, cy - wheelR), style = Stroke(2f))
            drawCircle(stroke, wheelR, Offset(cx + objW * 0.28f, cy - wheelR), style = Stroke(2f))
            drawLine(
                color = stroke,
                start = Offset(cx - objW * 0.28f, cy - wheelR * 1.6f),
                end = Offset(cx + objW * 0.28f, cy - wheelR * 1.6f),
                strokeWidth = 2.5f,
            )
            drawCircle(stroke, objW * 0.12f, Offset(cx, cy - objH * 0.85f))
        }
        LauncherAdasFrontObjectType.Pedestrian -> {
            drawCircle(fill, objW * 0.22f, Offset(cx, cy - objH * 0.82f))
            drawCircle(stroke, objW * 0.22f, Offset(cx, cy - objH * 0.82f), style = Stroke(2f))
            drawLine(
                color = stroke,
                start = Offset(cx, cy - objH * 0.60f),
                end = Offset(cx, cy - objH * 0.18f),
                strokeWidth = 2.5f,
            )
            drawLine(
                color = stroke,
                start = Offset(cx - objW * 0.28f, cy - objH * 0.45f),
                end = Offset(cx + objW * 0.28f, cy - objH * 0.45f),
                strokeWidth = 2.2f,
            )
            drawLine(
                color = stroke,
                start = Offset(cx, cy - objH * 0.18f),
                end = Offset(cx - objW * 0.22f, cy),
                strokeWidth = 2.2f,
            )
            drawLine(
                color = stroke,
                start = Offset(cx, cy - objH * 0.18f),
                end = Offset(cx + objW * 0.22f, cy),
                strokeWidth = 2.2f,
            )
        }
        else -> {
            val topLeft = Offset(cx - objW / 2f, cy - objH)
            drawRoundRect(fill, topLeft, Size(objW, objH), CornerRadius(objW * 0.18f, objW * 0.18f))
            drawRoundRect(
                stroke,
                topLeft,
                Size(objW, objH),
                CornerRadius(objW * 0.18f, objW * 0.18f),
                Stroke(2f),
            )
        }
    }
}

private fun DrawScope.drawSpeedLimitSign(
    limitKmh: Int,
    warning: Boolean,
    canvasWidth: Float,
    offsetFromRight: Float = 14f,
    accent: Color = if (warning) Color(0xFFEF4444) else Color(0xFFE11D48),
    caption: String? = null,
) {
    val radius = 22f
    val cx = canvasWidth - radius - offsetFromRight
    val cy = radius + 18f
    drawCircle(color = Color.White.copy(alpha = 0.92f), radius = radius, center = Offset(cx, cy))
    drawCircle(
        color = accent,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 4.5f),
    )
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color(0xFF111827).toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        limitKmh.toString(),
        cx,
        cy - (paint.ascent() + paint.descent()) / 2f,
        paint,
    )
    if (!caption.isNullOrBlank()) {
        val cap = android.graphics.Paint().apply {
            isAntiAlias = true
            color = accent.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(
            caption,
            cx,
            cy + radius + 12f,
            cap,
        )
    }
}

private fun objectSizeForType(type: LauncherAdasFrontObjectType, roadHalf: Float): Pair<Float, Float> {
    val base = roadHalf * 0.38f
    return when (type) {
        LauncherAdasFrontObjectType.Bus ->
            base * 1.15f to base * 1.55f
        LauncherAdasFrontObjectType.Truck ->
            base * 1.05f to base * 1.45f
        LauncherAdasFrontObjectType.Car ->
            base * 0.95f to base * 0.95f
        LauncherAdasFrontObjectType.Motorcycle, LauncherAdasFrontObjectType.Bicycle ->
            base * 0.62f to base * 0.90f
        LauncherAdasFrontObjectType.Pedestrian ->
            base * 0.45f to base * 1.05f
        else -> base * 0.80f to base * 0.95f
    }
}

