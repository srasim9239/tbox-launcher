package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

// Calibrated against the GLB's authored forward axis so the nose points to screen top.
private const val MODEL_YAW_DEG = 135f
private const val SETTINGS_YAW_DEG = 45f

private val TOP_CAMERA_POS = Float3(0f, 4.8f, 0.08f)
private val TOP_CAMERA_TARGET = Float3(0f, 0f, 0f)
private val DRIVE_CAMERA_POS = Float3(0f, 1.05f, 3.7f)
private val DRIVE_CAMERA_TARGET = Float3(0f, 0.25f, -2.2f)
/** Settings camera keeps the whole car in frame and clear of the near clipping plane. */
private val SETTINGS_CAMERA_POS = Float3(1.35f, 1.05f, 2.55f)
private val SETTINGS_CAMERA_TARGET = Float3(0f, 0.18f, 0f)

private const val HOME_MODEL_SCALE = 0.52f
// Settings uses the full SceneView bounds; visual size is controlled only here/camera.
private const val SETTINGS_MODEL_SCALE = 0.48f
internal const val SETTINGS_USER_SCALE_MIN = 1.20f
internal const val SETTINGS_USER_SCALE_MAX = 1.45f
private const val HOME_MODEL_X = 0f
private const val SETTINGS_MODEL_X = 0f
private const val HOME_MODEL_Y = -0.1f
private const val SETTINGS_MODEL_Y = -0.02f
private const val HOME_MODEL_Z = 0f
private const val SETTINGS_MODEL_Z = 0f

/** Offline Filament 3D car (Dashing 720) with pivot rig, paint colors and drive camera. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherCar3DModel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modelRevision: Int = 0,
    paintRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    rigState: LauncherCarRigState = LauncherCarRigState(),
    speedKmh: Float = 0f,
    steeringDeg: Float = 0f,
    steerPreview: Boolean = false,
    inDriveGear: Boolean = false,
    showRoad: Boolean = true,
    settingsView: Boolean = false,
    settingsProgress: Float = 1f,
    settingsUserYawDeg: Float = 0f,
    settingsUserScale: Float = 1f,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit = {},
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit = {},
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit = {},
    onBodyRigAvailabilityChanged: (Boolean) -> Unit = {},
    textureSurface: Boolean = false,
) {
    val interactionModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() },
        )
    } else {
        Modifier
    }

    Box(modifier = modifier) {
        if (showRoad) {
            LauncherVirtualRoad(
                speedKmh = speedKmh,
                steerAngleDeg = steeringDeg,
                steerPreview = steerPreview,
                inDriveGear = inDriveGear,
                modifier = Modifier.fillMaxSize(),
            )
        }
        LauncherCarFilamentModel(
            modifier = Modifier.fillMaxSize(),
            paintId = paintId,
            rigState = rigState,
            speedKmh = speedKmh,
            steeringDeg = steeringDeg,
            steerPreview = steerPreview,
            inDriveGear = inDriveGear,
            settingsProgress = settingsProgress,
            settingsUserYawDeg = settingsUserYawDeg,
            settingsUserScale = settingsUserScale,
            onWheelAnchorsChanged = onWheelAnchorsChanged,
            onDoorAnchorsChanged = onDoorAnchorsChanged,
            onPdcRingsChanged = onPdcRingsChanged,
            onBodyRigAvailabilityChanged = onBodyRigAvailabilityChanged,
            textureSurface = textureSurface,
        )
        if (onClick != null || onLongClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(interactionModifier),
            )
        }
    }
}

@Composable
private fun LauncherCarFilamentModel(
    modifier: Modifier = Modifier,
    paintId: String,
    rigState: LauncherCarRigState,
    speedKmh: Float,
    steeringDeg: Float,
    steerPreview: Boolean,
    inDriveGear: Boolean,
    settingsProgress: Float,
    settingsUserYawDeg: Float,
    settingsUserScale: Float,
    onWheelAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onDoorAnchorsChanged: (Map<LauncherWheelCorner, Offset>) -> Unit,
    onPdcRingsChanged: (LauncherPdcRingFrame?) -> Unit,
    onBodyRigAvailabilityChanged: (Boolean) -> Unit,
    textureSurface: Boolean = false,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val modelInstance = rememberModelInstance(modelLoader, LAUNCHER_CAR_MODEL_ASSET)
    val currentTransition = settingsProgress.coerceIn(0f, 1f)

    val inDriveRef = rememberUpdatedState(inDriveGear)
    val rigStateRef = rememberUpdatedState(
        rigState.copy(speedKmh = speedKmh, steeringDeg = steeringDeg),
    )
    val settingsUserYawRef = rememberUpdatedState(settingsUserYawDeg)
    val settingsUserScaleRef = rememberUpdatedState(settingsUserScale)
    val anchorCallbackRef = rememberUpdatedState(onWheelAnchorsChanged)
    val doorAnchorCallbackRef = rememberUpdatedState(onDoorAnchorsChanged)
    val pdcRingsCallbackRef = rememberUpdatedState(onPdcRingsChanged)
    val rigAvailabilityCallbackRef = rememberUpdatedState(onBodyRigAvailabilityChanged)
    var lastFrameNs by remember { mutableLongStateOf(0L) }
    var lastAnchorPublishNs by remember { mutableLongStateOf(0L) }
    var driveBlend by remember { mutableFloatStateOf(0f) }
    var preparedFrames by remember(modelInstance) { mutableIntStateOf(0) }
    var renderedScale by remember(modelInstance) { mutableFloatStateOf(0f) }
    var modelReady by remember(modelInstance) { mutableStateOf(false) }
    val modelAlpha by animateFloatAsState(
        targetValue = if (modelReady) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "launcherCarModelAlpha",
    )
    val modelNodeRef = remember { mutableStateOf<ModelNode?>(null) }
    val paintMaterialsRef = remember { mutableStateOf<List<com.google.android.filament.MaterialInstance>>(emptyList()) }
    // TransformManager is read-only here (wheel anchors); body motion uses authored glTF clips.
    val rigController = remember(modelInstance, engine) {
        modelInstance?.let { LauncherCarRigController.bind(engine, it) }
    }
    val animationController = remember(modelInstance) {
        modelInstance?.let(LauncherCarAnimationController::bind)
    }

    // SceneView otherwise renders one frame with its default camera before onFrame runs.
    SideEffect {
        val initialDrivePos = Float3(
            lerp(TOP_CAMERA_POS.x, DRIVE_CAMERA_POS.x, driveBlend),
            lerp(TOP_CAMERA_POS.y, DRIVE_CAMERA_POS.y, driveBlend),
            lerp(TOP_CAMERA_POS.z, DRIVE_CAMERA_POS.z, driveBlend),
        )
        val initialDriveTarget = Float3(
            lerp(TOP_CAMERA_TARGET.x, DRIVE_CAMERA_TARGET.x, driveBlend),
            lerp(TOP_CAMERA_TARGET.y, DRIVE_CAMERA_TARGET.y, driveBlend),
            lerp(TOP_CAMERA_TARGET.z, DRIVE_CAMERA_TARGET.z, driveBlend),
        )
        cameraNode.worldPosition = Position(
            lerp(initialDrivePos.x, SETTINGS_CAMERA_POS.x, currentTransition),
            lerp(initialDrivePos.y, SETTINGS_CAMERA_POS.y, currentTransition),
            lerp(initialDrivePos.z, SETTINGS_CAMERA_POS.z, currentTransition),
        )
        cameraNode.lookAt(
            Position(
                lerp(initialDriveTarget.x, SETTINGS_CAMERA_TARGET.x, currentTransition),
                lerp(initialDriveTarget.y, SETTINGS_CAMERA_TARGET.y, currentTransition),
                lerp(initialDriveTarget.z, SETTINGS_CAMERA_TARGET.z, currentTransition),
            ),
        )
    }

    LaunchedEffect(rigController, animationController) {
        val requiredBodyNodes = setOf(
            LauncherCarRigController.DOOR_FL,
            LauncherCarRigController.DOOR_FR,
            LauncherCarRigController.DOOR_RL,
            LauncherCarRigController.DOOR_RR,
            LauncherCarRigController.TAILGATE,
        )
        rigAvailabilityCallbackRef.value(
            animationController != null &&
                rigController != null &&
                rigController.missingNodes.none(requiredBodyNodes::contains),
        )
    }

    LaunchedEffect(modelInstance, paintId) {
        val instance = modelInstance ?: return@LaunchedEffect
        val materials = LauncherCarPaint.bindMaterials(instance)
        paintMaterialsRef.value = materials
        LauncherCarPaint.apply(materials, paintId)
    }

    Box(modifier = modifier) {
        if (modelInstance != null) {
            SceneView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = modelAlpha }
                    .pointerInteropFilter { false },
                // TextureSurface composites inline so Compose overlays (tire/door badges)
                // draw above the model; the default SurfaceView stays for the home panel
                // where overlays sit beside the car silhouette.
                surfaceType = if (textureSurface) SurfaceType.TextureSurface else SurfaceType.Surface,
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                cameraManipulator = null,
                isOpaque = false,
                autoFitContent = false,
                onFrame = { frameNs ->
                    val node = modelNodeRef.value ?: return@SceneView
                    val dt = if (lastFrameNs == 0L) {
                        0.016f
                    } else {
                        ((frameNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.05f)
                    }
                    lastFrameNs = frameNs

                    val transition = settingsProgress.coerceIn(0f, 1f)
                    val driveTarget = when {
                        transition > 0.02f -> 0f
                        inDriveRef.value -> 1f
                        steerPreview -> 1f
                        else -> 0f
                    }
                    driveBlend += (driveTarget - driveBlend) * (1f - kotlin.math.exp(-dt * 2.0f))

                    // Continuous camera morph: top/drive ↔ settings (no body yaw while driving).
                    val drivePos = Float3(
                        lerp(TOP_CAMERA_POS.x, DRIVE_CAMERA_POS.x, driveBlend),
                        lerp(TOP_CAMERA_POS.y, DRIVE_CAMERA_POS.y, driveBlend),
                        lerp(TOP_CAMERA_POS.z, DRIVE_CAMERA_POS.z, driveBlend),
                    )
                    val driveTargetPos = Float3(
                        lerp(TOP_CAMERA_TARGET.x, DRIVE_CAMERA_TARGET.x, driveBlend),
                        lerp(TOP_CAMERA_TARGET.y, DRIVE_CAMERA_TARGET.y, driveBlend),
                        lerp(TOP_CAMERA_TARGET.z, DRIVE_CAMERA_TARGET.z, driveBlend),
                    )
                    cameraNode.worldPosition = Position(
                        lerp(drivePos.x, SETTINGS_CAMERA_POS.x, transition),
                        lerp(drivePos.y, SETTINGS_CAMERA_POS.y, transition),
                        lerp(drivePos.z, SETTINGS_CAMERA_POS.z, transition),
                    )
                    cameraNode.lookAt(
                        Position(
                            lerp(driveTargetPos.x, SETTINGS_CAMERA_TARGET.x, transition),
                            lerp(driveTargetPos.y, SETTINGS_CAMERA_TARGET.y, transition),
                            lerp(driveTargetPos.z, SETTINGS_CAMERA_TARGET.z, transition),
                        ),
                    )
                    val modelYaw = lerp(
                        MODEL_YAW_DEG,
                        SETTINGS_YAW_DEG + settingsUserYawRef.value,
                        transition,
                    )
                    node.rotation = Rotation(y = modelYaw)
                    node.position = Position(
                        x = lerp(HOME_MODEL_X, SETTINGS_MODEL_X, transition),
                        y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, transition),
                        z = lerp(HOME_MODEL_Z, SETTINGS_MODEL_Z, transition),
                    )
                    val baseScale = lerp(HOME_MODEL_SCALE, SETTINGS_MODEL_SCALE, transition)
                    val pinchScale = lerp(
                        1f,
                        settingsUserScaleRef.value.coerceIn(
                            SETTINGS_USER_SCALE_MIN,
                            SETTINGS_USER_SCALE_MAX,
                        ),
                        transition,
                    )
                    val targetScale = baseScale * pinchScale
                    if (!modelReady) {
                        // Start slightly larger, then settle smoothly after the prepared
                        // model becomes visible. This masks unavoidable SurfaceView startup.
                        renderedScale = targetScale * lerp(1.08f, 1.18f, transition)
                        node.scale = Scale(renderedScale)
                        preparedFrames++
                        if (preparedFrames >= 12) {
                            node.isVisible = true
                            modelReady = true
                        }
                    } else {
                        val scaleResponse = 1f - kotlin.math.exp(-dt * 8f)
                        renderedScale += (targetScale - renderedScale) * scaleResponse
                        node.scale = Scale(renderedScale)
                    }

                    animationController?.update(rigStateRef.value, dt)
                    if (frameNs - lastAnchorPublishNs >= 100_000_000L) {
                        lastAnchorPublishNs = frameNs
                        val viewport = cameraNode.viewport
                        anchorCallbackRef.value(
                            rigController?.projectWheelAnchors(
                                cameraNode = cameraNode,
                                viewportWidthPx = viewport?.width ?: 0,
                                viewportHeightPx = viewport?.height ?: 0,
                            ).orEmpty(),
                        )
                        doorAnchorCallbackRef.value(
                            rigController?.projectDoorAnchors(
                                cameraNode = cameraNode,
                                viewportWidthPx = viewport?.width ?: 0,
                                viewportHeightPx = viewport?.height ?: 0,
                            ).orEmpty(),
                        )
                        pdcRingsCallbackRef.value(
                            rigController?.projectPdcRings(
                                cameraNode = cameraNode,
                                viewportWidthPx = viewport?.width ?: 0,
                                viewportHeightPx = viewport?.height ?: 0,
                            ),
                        )
                    }
                },
            ) {
                val initialTransition = settingsProgress.coerceIn(0f, 1f)
                val initialScale = lerp(
                    HOME_MODEL_SCALE,
                    SETTINGS_MODEL_SCALE,
                    initialTransition,
                ) * lerp(
                    1f,
                    settingsUserScale.coerceIn(
                        SETTINGS_USER_SCALE_MIN,
                        SETTINGS_USER_SCALE_MAX,
                    ),
                    initialTransition,
                )
                ModelNode(
                    modelInstance = modelInstance,
                    autoAnimate = false,
                    // The settings SceneView can expose its construction frame before
                    // visibility reaches the renderer. Keep that frame effectively empty;
                    // onFrame applies the final calibrated scale before revealing the node.
                    scale = Scale(if (initialTransition >= 0.99f) 0.001f else initialScale),
                    position = Position(
                        x = lerp(HOME_MODEL_X, SETTINGS_MODEL_X, initialTransition),
                        y = lerp(HOME_MODEL_Y, SETTINGS_MODEL_Y, initialTransition),
                        z = lerp(HOME_MODEL_Z, SETTINGS_MODEL_Z, initialTransition),
                    ),
                    rotation = Rotation(
                        y = lerp(
                            MODEL_YAW_DEG,
                            SETTINGS_YAW_DEG + settingsUserYawDeg,
                            initialTransition,
                        ),
                    ),
                    apply = {
                        isVisible = false
                        modelNodeRef.value = this
                    },
                )
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
