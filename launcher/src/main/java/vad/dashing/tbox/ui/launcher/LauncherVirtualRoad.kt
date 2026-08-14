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
import androidx.compose.ui.graphics.Brush
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

@Composable
fun LauncherEggRaceCarsLayer(
    cars: List<LauncherEggRaceCar>,
    modifier: Modifier = Modifier,
    minDepth: Float = 0f,
    maxDepth: Float = 1f,
) {
    val visible = cars.filter { it.depth >= minDepth && it.depth < maxDepth }
    if (visible.isEmpty()) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.24f
        val vanishX = w / 2f
        fun halfWidthAt(t: Float): Float =
            (w * 0.14f) * (1f - t).pow(1.25f) + (w * 1.10f) * t.pow(1.05f)
        fun laneOffsetAt(t: Float): Float = halfWidthAt(t) * 0.30f
        fun yAt(t: Float): Float = horizonY + (h - horizonY) * t
        fun centerXAt(t: Float): Float = vanishX
        drawRaceCars(
            cars = visible,
            centerXAt = ::centerXAt,
            laneOffsetAt = ::laneOffsetAt,
            halfWidthAt = ::halfWidthAt,
            yAt = ::yAt,
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
    // The road deliberately overflows the panel at the bottom: neighbouring lanes run
    // off both edges, which is what keeps the ego car from looking oversized.
    fun halfWidthAt(t: Float): Float =
        (w * 0.14f) * (1f - t).pow(1.25f) + (w * 1.10f) * t.pow(1.05f)
    // Ego (center) lane half-width — ACC beam and LKA sit on this strip, not the
    // outer road shoulders.
    fun laneOffsetAt(t: Float): Float = halfWidthAt(t) * 0.30f
    fun yAt(t: Float): Float = horizonY + (h - horizonY) * t
    fun centerXAt(t: Float): Float = vanishX

    drawRoadSurface(
        horizonY = horizonY,
        centerXAt = ::centerXAt,
        halfWidthAt = ::halfWidthAt,
        yAt = ::yAt,
    )

    drawAdasLaneAssist(
        adas = adas,
        centerXAt = ::centerXAt,
        laneOffsetAt = ::laneOffsetAt,
        yAt = ::yAt,
    )

    val dashSpacing = (58f + speedKmh * 0.42f).coerceIn(48f, 128f)
    val phase = roadPhase % dashSpacing
    var y = horizonY + phase
    while (y < h) {
        val t = ((y - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        val dashLen = (22f + t * 40f).coerceAtLeast(16f)
        val nextT = (((y + dashLen) - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        // Stronger in the mid-road, dissolving into the horizon haze and
        // fading out again at the bottom edge (under the player).
        val fade = distanceFade(t) * bottomEdgeFade(t)
        // Ego lane borders plus the two neighbouring lanes, as on a real HMI.
        listOf(
            -1f to 1f,
            1f to 1f,
            -3f to 0.55f,
            3f to 0.55f,
        ).forEach { (side, weight) ->
            val x1 = centerXAt(t) + side * laneOffsetAt(t)
            val x2 = centerXAt(nextT) + side * laneOffsetAt(nextT)
            if (x1 < -w * 0.7f || x1 > w * 1.7f) return@forEach
            val alpha = 0.50f * fade * weight
            if (alpha < 0.02f) return@forEach
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(x1, y),
                end = Offset(x2, y + dashLen),
                strokeWidth = 1.6f + 1.6f * t,
            )
        }
        y += dashSpacing * (0.55f + 0.85f * t)
    }

    drawHorizonHaze(horizonY = horizonY, canvasHeight = h)

    val frontDistance = adas.frontObject.displayDistanceM
    if (adas.frontObject.valid && frontDistance != null) {
        if ((adas.accActive || adas.accStandby) && adas.timeGapLevel != null) {
            drawAccBeam(
                objectDistanceM = frontDistance,
                centerXAt = ::centerXAt,
                laneOffsetAt = ::laneOffsetAt,
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
        drawAccBeam(
            objectDistanceM = timeGapFallbackDistanceM(adas.timeGapLevel),
            centerXAt = ::centerXAt,
            laneOffsetAt = ::laneOffsetAt,
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

/**
 * Distance falloff for everything painted on the road: full strength near ego,
 * dissolving into the haze towards the horizon.
 */
private fun distanceFade(t: Float): Float = (t.coerceIn(0f, 1f)).pow(0.62f)

/** Dissolve lane dashes in the last third so they do not hit the panel edge. */
private fun bottomEdgeFade(t: Float): Float {
    val start = 0.62f
    if (t <= start) return 1f
    val u = ((t - start) / (1f - start)).coerceIn(0f, 1f)
    return (1f - u).pow(1.45f)
}

/** Asphalt wedge from the horizon down to ego, dark and fading out with distance. */
private fun DrawScope.drawRoadSurface(
    horizonY: Float,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    val surface = Path().apply {
        moveTo(centerXAt(0f) - halfWidthAt(0f), yAt(0f))
        lineTo(centerXAt(0f) + halfWidthAt(0f), yAt(0f))
        for (i in 1..10) {
            val t = i / 10f
            lineTo(centerXAt(t) + halfWidthAt(t), yAt(t))
        }
        for (i in 10 downTo 1) {
            val t = i / 10f
            lineTo(centerXAt(t) - halfWidthAt(t), yAt(t))
        }
        close()
    }
    drawPath(
        path = surface,
        brush = Brush.verticalGradient(
            0f to Color(0xFF1B2634).copy(alpha = 0f),
            0.35f to Color(0xFF1B2634).copy(alpha = 0.55f),
            1f to Color(0xFF232F3E).copy(alpha = 0.92f),
            startY = horizonY,
            endY = size.height,
        ),
    )
}

/** Fog band at the horizon that swallows the road and the markings. */
private fun DrawScope.drawHorizonHaze(horizonY: Float, canvasHeight: Float) {
    val bg = LauncherColors.LeftPanelBg
    val hazeBottom = horizonY + (canvasHeight - horizonY) * 0.34f
    drawRect(
        brush = Brush.verticalGradient(
            0f to bg,
            0.45f to bg.copy(alpha = 0.72f),
            1f to bg.copy(alpha = 0f),
            startY = horizonY - canvasHeight * 0.06f,
            endY = hazeBottom,
        ),
        topLeft = Offset(0f, horizonY - canvasHeight * 0.06f),
        size = Size(size.width, hazeBottom - horizonY + canvasHeight * 0.06f),
    )
}

/** ACC following beam: a glowing wedge from ego up to the tracked target,
 *  confined to the center (ego) lane — not the outer road shoulders. */
private fun DrawScope.drawAccBeam(
    objectDistanceM: Int,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    yAt: (Float) -> Float,
    alert: Boolean,
) {
    val targetDepth = distanceToRoadDepth(objectDistanceM)
    // Ego end sits just below the target band and above the 3D car.
    val egoDepth = (targetDepth + 0.30f).coerceIn(targetDepth + 0.08f, 0.70f)
    val nearHalf = laneOffsetAt(egoDepth) * 0.88f
    val farHalf = laneOffsetAt(targetDepth) * 0.88f
    val beam = Path().apply {
        moveTo(centerXAt(egoDepth) - nearHalf, yAt(egoDepth))
        lineTo(centerXAt(targetDepth) - farHalf, yAt(targetDepth))
        lineTo(centerXAt(targetDepth) + farHalf, yAt(targetDepth))
        lineTo(centerXAt(egoDepth) + nearHalf, yAt(egoDepth))
        close()
    }
    val tint = if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan
    drawPath(
        path = beam,
        brush = Brush.verticalGradient(
            0f to tint.copy(alpha = 0.10f),
            1f to tint.copy(alpha = 0.42f),
            startY = yAt(targetDepth),
            endY = yAt(egoDepth),
        ),
    )
    drawPath(path = beam, color = tint.copy(alpha = 0.30f), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawAdasLaneAssist(
    adas: LauncherAdasState,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
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
        // Track the borders of the center (ego) lane, not the outer road edges.
        var prev = Offset(
            centerXAt(1f) + side * laneOffsetAt(1f),
            yAt(1f),
        )
        for (i in 8 downTo 0) {
            val t = i / 8f
            val next = Offset(
                centerXAt(t) + side * laneOffsetAt(t),
                yAt(t),
            )
            drawLine(color = color, start = prev, end = next, strokeWidth = if (warning) 3.5f else 2.5f)
            prev = next
        }
    }
}

private fun timeGapFallbackDistanceM(gapLevel: Int): Int = when (gapLevel.coerceIn(0, 2)) {
    0 -> 18
    1 -> 28
    else -> 40
}

private fun DrawScope.drawRaceCars(
    cars: List<LauncherEggRaceCar>,
    centerXAt: (Float) -> Float,
    laneOffsetAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    cars.sortedBy { it.depth }.forEach { car ->
        val t = car.depth.coerceIn(0.02f, 0.90f)
        val cx = centerXAt(t) + car.lane * 2f * laneOffsetAt(t)
        val cy = yAt(t)
        val roadHalf = halfWidthAt(t)
        val (objW, objH) = objectSizeForType(LauncherAdasFrontObjectType.Car, roadHalf)
        val approach = (t / 0.48f).coerceIn(0f, 1f)
        drawOncomingCarFront(
            cx = cx,
            cy = cy,
            objW = objW * (1.22f + 0.62f * approach),
            objH = objH * (1.18f + 0.52f * approach),
            paintId = car.id,
        )
    }
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
    if (adas.frontObject.type == LauncherAdasFrontObjectType.Car && !alert) {
        // A plain car ahead reads better as a light 3/4-rear body than as a
        // coloured wireframe; alerts still fall back to the high-contrast silhouette.
        drawLeadCarBody(cx = cx, cy = cy, objW = objW * 1.35f, objH = objH * 1.15f)
    } else {
        drawFrontObjectSilhouette(
            type = adas.frontObject.type,
            cx = cx,
            cy = cy,
            objW = objW,
            objH = objH,
            fill = fillColor,
            stroke = strokeColor,
        )
    }

    // Distance label above the vehicle, where the ACC beam ends.
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = (if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan).toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = (11f + depth * 6f).coerceIn(11f, 15f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        "${distanceM}m",
        cx,
        cy - objH * 1.35f - 4f,
        paint,
    )
}

private val OncomingCarPaints = arrayOf(
    Color(0xFFF4F6F8),
    Color(0xFF232830),
    Color(0xFF4E6D98),
    Color(0xFFB4453C),
    Color(0xFFC5CAD0),
    Color(0xFF2E4A3F),
)

/** Oncoming modern crossover, front view: slim DRL bar, closed fascia, windshield. */
private fun DrawScope.drawOncomingCarFront(
    cx: Float,
    cy: Float,
    objW: Float,
    objH: Float,
    paintId: Int,
) {
    val w = objW
    val h = objH
    val paint = OncomingCarPaints[kotlin.math.abs(paintId) % OncomingCarPaints.size]
    val darkBody = (paint.red + paint.green + paint.blue) / 3f < 0.42f
    val bodyHi = if (darkBody) {
        Color(
            (paint.red + 0.16f).coerceAtMost(1f),
            (paint.green + 0.16f).coerceAtMost(1f),
            (paint.blue + 0.18f).coerceAtMost(1f),
        )
    } else {
        Color.White
    }
    val bodyLo = Color(
        paint.red * 0.62f,
        paint.green * 0.64f,
        paint.blue * 0.68f,
    )
    val roofY = cy - h * 0.98f
    val glassBottom = cy - h * 0.50f
    val lampY = cy - h * 0.34f
    val bumperY = cy - h * 0.16f
    val roofW = w * 0.54f
    val shoulderW = w * 0.90f

    drawOval(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(cx - w * 0.56f, cy - h * 0.05f),
        size = Size(w * 1.12f, h * 0.16f),
    )

    val wheelW = w * 0.17f
    val wheelH = h * 0.13f
    listOf(-1f, 1f).forEach { side ->
        val wx = cx + side * w * 0.40f - wheelW / 2f
        val wy = cy - wheelH * 0.42f
        drawOval(Color(0xFF14161A), Offset(wx, wy), Size(wheelW, wheelH))
        drawOval(
            Color(0xFF8B919A),
            Offset(wx + wheelW * 0.22f, wy + wheelH * 0.22f),
            Size(wheelW * 0.56f, wheelH * 0.56f),
        )
        drawOval(
            Color(0xFF2A2E34),
            Offset(wx + wheelW * 0.34f, wy + wheelH * 0.34f),
            Size(wheelW * 0.32f, wheelH * 0.32f),
        )
    }

    val body = Path().apply {
        moveTo(cx - roofW / 2f, roofY + h * 0.10f)
        quadraticTo(cx, roofY - h * 0.01f, cx + roofW / 2f, roofY + h * 0.10f)
        lineTo(cx + shoulderW / 2f, glassBottom)
        lineTo(cx + w * 0.50f, bumperY)
        quadraticTo(cx + w * 0.50f, cy + h * 0.01f, cx + w * 0.34f, cy - h * 0.015f)
        lineTo(cx - w * 0.34f, cy - h * 0.015f)
        quadraticTo(cx - w * 0.50f, cy + h * 0.01f, cx - w * 0.50f, bumperY)
        lineTo(cx - shoulderW / 2f, glassBottom)
        close()
    }
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            0f to bodyHi,
            0.42f to paint,
            1f to bodyLo,
            startY = roofY,
            endY = cy,
        ),
    )

    val glass = Path().apply {
        moveTo(cx - roofW * 0.40f, roofY + h * 0.16f)
        lineTo(cx + roofW * 0.40f, roofY + h * 0.16f)
        lineTo(cx + shoulderW * 0.36f, glassBottom - h * 0.03f)
        lineTo(cx - shoulderW * 0.36f, glassBottom - h * 0.03f)
        close()
    }
    drawPath(
        path = glass,
        brush = Brush.verticalGradient(
            0f to Color(0xFF9EB4C8),
            0.35f to Color(0xFF3A4654),
            1f to Color(0xFF1A222C),
            startY = roofY,
            endY = glassBottom,
        ),
    )
    drawPath(
        path = Path().apply {
            moveTo(cx - roofW * 0.18f, roofY + h * 0.18f)
            lineTo(cx + roofW * 0.08f, roofY + h * 0.18f)
            lineTo(cx - roofW * 0.06f, glassBottom - h * 0.10f)
            lineTo(cx - shoulderW * 0.28f, glassBottom - h * 0.08f)
            close()
        },
        color = Color.White.copy(alpha = 0.14f),
    )

    val mirrorW = w * 0.11f
    val mirrorH = h * 0.07f
    val mirrorY = glassBottom - h * 0.09f
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = paint,
            topLeft = Offset(cx + side * (shoulderW / 2f + w * 0.01f) - if (side < 0) mirrorW else 0f, mirrorY),
            size = Size(mirrorW, mirrorH),
            cornerRadius = CornerRadius(mirrorH * 0.45f, mirrorH * 0.45f),
        )
        drawRoundRect(
            color = Color(0xFF2A313A),
            topLeft = Offset(
                cx + side * (shoulderW / 2f + w * 0.015f) - if (side < 0) mirrorW * 0.72f else mirrorW * 0.08f,
                mirrorY + mirrorH * 0.18f,
            ),
            size = Size(mirrorW * 0.64f, mirrorH * 0.58f),
            cornerRadius = CornerRadius(mirrorH * 0.25f, mirrorH * 0.25f),
        )
    }

    val barW = w * 0.78f
    val barH = h * 0.045f
    val barLeft = cx - barW / 2f
    val barTop = lampY - barH / 2f
    drawRoundRect(
        color = Color(0xFFD9F0FF).copy(alpha = 0.35f),
        topLeft = Offset(barLeft - w * 0.03f, barTop - h * 0.02f),
        size = Size(barW + w * 0.06f, barH + h * 0.04f),
        cornerRadius = CornerRadius(barH, barH),
    )
    drawRoundRect(
        color = Color(0xFFEAF6FF),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barH, barH),
    )
    val lampW = w * 0.20f
    val lampH = h * 0.09f
    listOf(-1f, 1f).forEach { side ->
        val lx = cx + side * w * 0.32f - lampW / 2f
        drawRoundRect(
            color = Color(0xFFB8DFFF).copy(alpha = 0.55f),
            topLeft = Offset(lx - w * 0.01f, lampY - lampH * 0.62f),
            size = Size(lampW + w * 0.02f, lampH * 1.15f),
            cornerRadius = CornerRadius(lampH * 0.5f, lampH * 0.5f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0f to Color(0xFFF8FCFF),
                1f to Color(0xFF7EC8F5),
            ),
            topLeft = Offset(lx, lampY - lampH * 0.48f),
            size = Size(lampW, lampH),
            cornerRadius = CornerRadius(lampH * 0.48f, lampH * 0.48f),
        )
    }

    val intakeW = w * 0.58f
    val intakeH = h * 0.09f
    drawRoundRect(
        color = Color(0xFF14181D),
        topLeft = Offset(cx - intakeW / 2f, bumperY - intakeH * 0.15f),
        size = Size(intakeW, intakeH),
        cornerRadius = CornerRadius(intakeH * 0.35f, intakeH * 0.35f),
    )
    drawRoundRect(
        color = Color(0xFF2A3038),
        topLeft = Offset(cx - intakeW * 0.42f, bumperY + intakeH * 0.12f),
        size = Size(intakeW * 0.84f, intakeH * 0.38f),
        cornerRadius = CornerRadius(intakeH * 0.12f, intakeH * 0.12f),
    )

    val plateW = w * 0.28f
    val plateH = h * 0.07f
    drawRoundRect(
        color = Color(0xFFE7E7E2),
        topLeft = Offset(cx - plateW / 2f, bumperY + intakeH * 0.22f),
        size = Size(plateW, plateH),
        cornerRadius = CornerRadius(plateH * 0.12f, plateH * 0.12f),
    )
    drawRoundRect(
        color = Color(0xFF3A4048),
        topLeft = Offset(cx - plateW / 2f, bumperY + intakeH * 0.22f),
        size = Size(plateW, plateH),
        cornerRadius = CornerRadius(plateH * 0.12f, plateH * 0.12f),
        style = Stroke(width = 1.2f),
    )
}

/** Modern hatchback/crossover seen from behind: full-width LED bar, floating roof. */
private fun DrawScope.drawLeadCarBody(cx: Float, cy: Float, objW: Float, objH: Float) {
    val w = objW
    val h = objH
    val paint = Color(0xFFE4E8EE)
    val bodyLo = Color(0xFF8F97A2)
    val roofY = cy - h * 0.98f
    val glassBottom = cy - h * 0.52f
    val lampY = cy - h * 0.36f
    val bumperY = cy - h * 0.16f
    val roofW = w * 0.56f
    val shoulderW = w * 0.90f

    drawOval(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(cx - w * 0.56f, cy - h * 0.05f),
        size = Size(w * 1.12f, h * 0.16f),
    )

    val wheelW = w * 0.17f
    val wheelH = h * 0.13f
    listOf(-1f, 1f).forEach { side ->
        val wx = cx + side * w * 0.40f - wheelW / 2f
        val wy = cy - wheelH * 0.42f
        drawOval(Color(0xFF14161A), Offset(wx, wy), Size(wheelW, wheelH))
        drawOval(
            Color(0xFF8B919A),
            Offset(wx + wheelW * 0.22f, wy + wheelH * 0.22f),
            Size(wheelW * 0.56f, wheelH * 0.56f),
        )
    }

    val body = Path().apply {
        moveTo(cx - roofW / 2f, roofY + h * 0.10f)
        quadraticTo(cx, roofY - h * 0.01f, cx + roofW / 2f, roofY + h * 0.10f)
        lineTo(cx + shoulderW / 2f, glassBottom)
        lineTo(cx + w * 0.50f, bumperY)
        quadraticTo(cx + w * 0.50f, cy + h * 0.01f, cx + w * 0.34f, cy - h * 0.015f)
        lineTo(cx - w * 0.34f, cy - h * 0.015f)
        quadraticTo(cx - w * 0.50f, cy + h * 0.01f, cx - w * 0.50f, bumperY)
        lineTo(cx - shoulderW / 2f, glassBottom)
        close()
    }
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            0f to Color.White,
            0.42f to paint,
            1f to bodyLo,
            startY = roofY,
            endY = cy,
        ),
    )

    drawRoundRect(
        color = Color(0xFF2A3038),
        topLeft = Offset(cx - roofW * 0.42f, roofY + h * 0.04f),
        size = Size(roofW * 0.84f, h * 0.045f),
        cornerRadius = CornerRadius(h * 0.02f, h * 0.02f),
    )

    val glass = Path().apply {
        moveTo(cx - roofW * 0.40f, roofY + h * 0.14f)
        lineTo(cx + roofW * 0.40f, roofY + h * 0.14f)
        lineTo(cx + shoulderW * 0.36f, glassBottom - h * 0.04f)
        lineTo(cx - shoulderW * 0.36f, glassBottom - h * 0.04f)
        close()
    }
    drawPath(
        path = glass,
        brush = Brush.verticalGradient(
            0f to Color(0xFF6A7A8C),
            0.45f to Color(0xFF2A3340),
            1f to Color(0xFF151B22),
            startY = roofY,
            endY = glassBottom,
        ),
    )

    val barW = w * 0.78f
    val barH = h * 0.042f
    val barLeft = cx - barW / 2f
    val barTop = lampY - barH / 2f
    drawRoundRect(
        color = Color(0xFFE05561).copy(alpha = 0.28f),
        topLeft = Offset(barLeft - w * 0.03f, barTop - h * 0.02f),
        size = Size(barW + w * 0.06f, barH + h * 0.04f),
        cornerRadius = CornerRadius(barH, barH),
    )
    drawRoundRect(
        color = Color(0xFFC43B44),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barH, barH),
    )
    val lampW = w * 0.20f
    val lampH = h * 0.09f
    listOf(-1f, 1f).forEach { side ->
        val lx = cx + side * w * 0.32f - lampW / 2f
        drawRoundRect(
            color = Color(0xFFFF6B73).copy(alpha = 0.45f),
            topLeft = Offset(lx - w * 0.01f, lampY - lampH * 0.62f),
            size = Size(lampW + w * 0.02f, lampH * 1.15f),
            cornerRadius = CornerRadius(lampH * 0.5f, lampH * 0.5f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0f to Color(0xFFFF8A90),
                1f to Color(0xFFB3262E),
            ),
            topLeft = Offset(lx, lampY - lampH * 0.48f),
            size = Size(lampW, lampH),
            cornerRadius = CornerRadius(lampH * 0.48f, lampH * 0.48f),
        )
    }

    val intakeW = w * 0.58f
    val intakeH = h * 0.09f
    drawRoundRect(
        color = Color(0xFF14181D),
        topLeft = Offset(cx - intakeW / 2f, bumperY - intakeH * 0.15f),
        size = Size(intakeW, intakeH),
        cornerRadius = CornerRadius(intakeH * 0.35f, intakeH * 0.35f),
    )
    val plateW = w * 0.28f
    val plateH = h * 0.07f
    drawRoundRect(
        color = Color(0xFFE7E7E2),
        topLeft = Offset(cx - plateW / 2f, bumperY + intakeH * 0.18f),
        size = Size(plateW, plateH),
        cornerRadius = CornerRadius(plateH * 0.12f, plateH * 0.12f),
    )
    drawRoundRect(
        color = Color(0xFF3A4048),
        topLeft = Offset(cx - plateW / 2f, bumperY + intakeH * 0.18f),
        size = Size(plateW, plateH),
        cornerRadius = CornerRadius(plateH * 0.12f, plateH * 0.12f),
        style = Stroke(width = 1.2f),
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
    // Scaled against the (now much wider) road, so a lead car keeps a plausible size.
    val base = roadHalf * 0.26f
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

