package vad.dashing.tbox.ui.launcher

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.FilamentInstance
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.CameraNode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cached, allocation-conscious bridge between launcher state and Filament pivot entities.
 *
 * Entity lookup and original local matrices happen once. A missing or non-transform entity is
 * omitted from [boundNodes], so a model update can degrade gracefully instead of crashing frames.
 */
class LauncherCarRigController private constructor(
    private val transformManager: TransformManager,
    private val boundNodes: Map<String, BoundNode>,
    val missingNodes: Set<String>,
) {
    private data class BoundNode(
        val entity: Int,
        val instance: Int,
    )

    private val workTransform = FloatArray(16)
    private val multiplyScratch = FloatArray(16)
    private val rotationTransform = FloatArray(16)
    private val lastAppliedRadians = mutableMapOf<String, Float>()

    fun update(state: LauncherCarRigState, @Suppress("UNUSED_PARAMETER") dtSec: Float) {
        transformManager.openLocalTransformTransaction()
        try {
            setRotation(
                DOOR_FL,
                RigAxis.Y,
                degreesToRadians(if (state.doorFlOpen) -LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                DOOR_FR,
                RigAxis.Y,
                degreesToRadians(if (state.doorFrOpen) LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                DOOR_RL,
                RigAxis.Y,
                degreesToRadians(if (state.doorRlOpen) -LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                DOOR_RR,
                RigAxis.Y,
                degreesToRadians(if (state.doorRrOpen) LauncherCarRig.DOOR_OPEN_DEG else 0f),
            )
            setRotation(
                TAILGATE,
                RigAxis.Z,
                degreesToRadians(
                    if (state.tailgateOpen) LauncherCarRig.TAILGATE_OPEN_DEG else 0f,
                ),
            )

            // Wheel pivot exports currently include adjacent arch geometry. Keep their original
            // transforms untouched until the GLB separates wheel-only meshes.
        } finally {
            transformManager.commitLocalTransformTransaction()
        }
    }

    /**
     * Projects actual wheel-pivot world positions into SceneView pixels.
     * Invalid/behind-camera/outlier coordinates are dropped fail-soft.
     */
    fun projectWheelAnchors(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): Map<LauncherWheelCorner, Offset> = projectAnchors(
        cameraNode = cameraNode,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        nodeNames = WHEEL_CORNERS,
    )

    fun projectDoorAnchors(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): Map<LauncherWheelCorner, Offset> = projectAnchors(
        cameraNode = cameraNode,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        nodeNames = DOOR_CORNERS,
        localOffsetX = 0.45f,
        localOffsetY = 0.28f,
    )

    private fun <K> projectAnchors(
        cameraNode: CameraNode,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        nodeNames: Map<K, String>,
        localOffsetX: Float = 0f,
        localOffsetY: Float = 0f,
        localOffsetZ: Float = 0f,
    ): Map<K, Offset> {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return emptyMap()
        return buildMap {
            nodeNames.forEach { (corner, nodeName) ->
                val node = boundNodes[nodeName] ?: return@forEach
                val world = FloatArray(16)
                transformManager.getWorldTransform(node.instance, world)
                val anchorX =
                    world[12] +
                        world[0] * localOffsetX +
                        world[4] * localOffsetY +
                        world[8] * localOffsetZ
                val anchorY =
                    world[13] +
                        world[1] * localOffsetX +
                        world[5] * localOffsetY +
                        world[9] * localOffsetZ
                val anchorZ =
                    world[14] +
                        world[2] * localOffsetX +
                        world[6] * localOffsetY +
                        world[10] * localOffsetZ
                val cameraPosition = cameraNode.worldPosition
                val cameraForward = cameraNode.forwardDirection
                val cameraDepth =
                    (anchorX - cameraPosition.x) * cameraForward.x +
                        (anchorY - cameraPosition.y) * cameraForward.y +
                        (anchorZ - cameraPosition.z) * cameraForward.z
                if (!cameraDepth.isFinite() || cameraDepth <= cameraNode.near) return@forEach
                val screen = cameraNode.worldToScreenPoint(
                    Vector3(anchorX, anchorY, anchorZ),
                )
                if (
                    screen.x.isFinite() && screen.y.isFinite() && screen.z.isFinite() &&
                    screen.x in -viewportWidthPx.toFloat()..(viewportWidthPx * 2f) &&
                    screen.y in -viewportHeightPx.toFloat()..(viewportHeightPx * 2f)
                ) {
                    put(corner, Offset(screen.x, screen.y))
                }
            }
        }
    }

    private fun setRotation(name: String, axis: RigAxis, radians: Float) {
        val node = boundNodes[name] ?: return
        val last = lastAppliedRadians[name]
        if (last == null && kotlin.math.abs(radians) < 0.0001f) return
        if (last != null && kotlin.math.abs(last - radians) < 0.0001f) return
        val translation = PIVOT_TRANSLATIONS[name] ?: return
        workTransform.fill(0f)
        workTransform[0] = 1f
        workTransform[5] = 1f
        workTransform[10] = 1f
        workTransform[15] = 1f
        workTransform[12] = translation[0]
        workTransform[13] = translation[1]
        workTransform[14] = translation[2]
        applyRotation(axis, radians)
        transformManager.setTransform(node.instance, workTransform)
        lastAppliedRadians[name] = radians
    }

    private fun applyRotation(axis: RigAxis, radians: Float) {
        rotationTransform.fill(0f)
        rotationTransform[15] = 1f
        val c = cos(radians)
        val s = sin(radians)
        when (axis) {
            RigAxis.X -> {
                rotationTransform[0] = 1f
                rotationTransform[5] = c
                rotationTransform[6] = s
                rotationTransform[9] = -s
                rotationTransform[10] = c
            }
            RigAxis.Y -> {
                rotationTransform[0] = c
                rotationTransform[2] = -s
                rotationTransform[5] = 1f
                rotationTransform[8] = s
                rotationTransform[10] = c
            }
            RigAxis.Z -> {
                rotationTransform[0] = c
                rotationTransform[1] = s
                rotationTransform[4] = -s
                rotationTransform[5] = c
                rotationTransform[10] = 1f
            }
        }
        multiply4x4Into(workTransform, rotationTransform, multiplyScratch)
        multiplyScratch.copyInto(workTransform)
    }

    private fun multiply4x4Into(left: FloatArray, right: FloatArray, result: FloatArray) {
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += left[k * 4 + row] * right[col * 4 + k]
                }
                result[col * 4 + row] = sum
            }
        }
    }

    companion object {
        const val DOOR_FL = "p_door01"
        const val DOOR_FR = "p_door02"
        const val DOOR_RL = "p_door03"
        const val DOOR_RR = "p_door04"
        const val TAILGATE = "houbeimen_copy"
        const val WHEEL_FL = "wheel_lungu01_L"
        const val WHEEL_FR = "wheel_lungu01_R"
        const val WHEEL_RL = "wheel_lungu02_L"
        const val WHEEL_RR = "wheel_lungu02_R"

        val requiredNodeNames: Set<String> = linkedSetOf(
            DOOR_FL,
            DOOR_FR,
            DOOR_RL,
            DOOR_RR,
            TAILGATE,
            WHEEL_FL,
            WHEEL_FR,
            WHEEL_RL,
            WHEEL_RR,
        )

        private val WHEEL_CORNERS = mapOf(
            LauncherWheelCorner.FL to WHEEL_FL,
            LauncherWheelCorner.FR to WHEEL_FR,
            LauncherWheelCorner.RL to WHEEL_RL,
            LauncherWheelCorner.RR to WHEEL_RR,
        )
        private val DOOR_CORNERS = mapOf(
            LauncherWheelCorner.FL to DOOR_FL,
            LauncherWheelCorner.FR to DOOR_FR,
            LauncherWheelCorner.RL to DOOR_RL,
            LauncherWheelCorner.RR to DOOR_RR,
        )
        private val PIVOT_TRANSLATIONS = mapOf(
            DOOR_FL to floatArrayOf(-0.929257f, 1.0214f, 0.896827f),
            DOOR_FR to floatArrayOf(-0.929257f, 1.0214f, -0.896827f),
            DOOR_RL to floatArrayOf(0.111599f, 1.05741f, 0.903843f),
            DOOR_RR to floatArrayOf(0.111599f, 1.05741f, -0.903843f),
            TAILGATE to floatArrayOf(1.39406f, 1.60524f, 0.36237f),
        )
        private val loggedMissingNodes = mutableSetOf<String>()

        fun bind(engine: Engine, modelInstance: FilamentInstance): LauncherCarRigController {
            val manager = engine.transformManager
            val asset = modelInstance.asset
            val entitiesByName = buildMap<String, Int> {
                for (entity in modelInstance.entities) {
                    val name = asset.getName(entity)
                    if (!name.isNullOrBlank()) put(name, entity)
                }
            }
            val nodes = buildMap {
                requiredNodeNames.forEach { name ->
                    val entity = entitiesByName[name] ?: return@forEach
                    if (!manager.hasComponent(entity)) return@forEach
                    val instance = manager.getInstance(entity)
                    if (instance == 0) return@forEach
                    put(
                        name,
                        BoundNode(
                            entity = entity,
                            instance = instance,
                        ),
                    )
                }
            }
            val missing = requiredNodeNames - nodes.keys
            synchronized(loggedMissingNodes) {
                (missing - loggedMissingNodes).forEach { name ->
                    Log.w("LauncherCarRig", "Model pivot is missing; effect disabled: $name")
                }
                loggedMissingNodes += missing
            }
            return LauncherCarRigController(
                transformManager = manager,
                boundNodes = nodes,
                missingNodes = missing,
            )
        }

        private fun degreesToRadians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)
    }
}
