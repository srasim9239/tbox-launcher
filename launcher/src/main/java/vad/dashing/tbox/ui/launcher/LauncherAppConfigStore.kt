package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val PREFS = "tbox_launcher_app_config"
private const val KEY_HIDDEN = "hidden_packages"
private const val KEY_GRID = "grid_packages"
private const val KEY_DOCK = "dock_packages"
private const val KEY_CAR_PAINT = "car_paint_id"
private const val KEY_DEFAULT_MEDIA = "default_media_package"
private const val KEY_MEDIA_CARD_ALPHA = "media_card_alpha"
private const val KEY_MEDIA_MINI_PLAYER_VISIBLE = "media_mini_player_visible"
private const val KEY_DOCK_ICON_SCALE = "dock_icon_scale"
private const val KEY_FUEL_SHOWS_RANGE = "fuel_shows_range"
private const val KEY_FULLSCREEN = "fullscreen_packages"
internal const val MEDIA_CARD_ALPHA_DEFAULT = 0.88f
internal const val MEDIA_CARD_ALPHA_MIN = 0.40f
internal const val MEDIA_CARD_ALPHA_MAX = 1.00f
internal const val DOCK_ICON_SCALE_DEFAULT = 1.00f
internal const val DOCK_ICON_SCALE_MIN = 1.00f
internal const val DOCK_ICON_SCALE_MAX = 1.45f
internal val CRUISE_PRESET_DEFAULTS_KMH = listOf(110, 80, 60)
internal const val CRUISE_PRESET_MIN_KMH = 30
internal const val CRUISE_PRESET_MAX_KMH = 160
internal const val CRUISE_PRESET_STEP_KMH = 5
private const val KEY_CRUISE_PRESETS = "cruise_presets_kmh"
internal const val GRID_SLOT_COUNT = 9
private const val DOCK_SLOT_COUNT = 4

private val DEFAULT_DOCK = listOf(
    "com.autopai.car.dialer",
    "com.wt.multimedia.platform3",
    "com.tencent.wecarnavi",
    "com.autopai.system.settings",
)

internal object LauncherAppConfigStore {

    private val mediaCardAlphaRevision = MutableStateFlow(0)
    internal val mediaCardAlphaRevisionFlow: StateFlow<Int> = mediaCardAlphaRevision
    private val mediaMiniPlayerRevision = MutableStateFlow(0)
    internal val mediaMiniPlayerRevisionFlow: StateFlow<Int> = mediaMiniPlayerRevision
    private val dockIconScaleRevision = MutableStateFlow(0)
    internal val dockIconScaleRevisionFlow: StateFlow<Int> = dockIconScaleRevision
    private val cruisePresetsRevision = MutableStateFlow(0)
    internal val cruisePresetsRevisionFlow: StateFlow<Int> = cruisePresetsRevision

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun carPaintId(context: Context): String {
        val raw = prefs(context).getString(KEY_CAR_PAINT, LauncherCarPaint.defaultId)
        return LauncherCarPaint.options.firstOrNull { it.id == raw }?.id ?: LauncherCarPaint.defaultId
    }

    fun setCarPaintId(context: Context, paintId: String) {
        prefs(context).edit().putString(KEY_CAR_PAINT, paintId).apply()
    }

    fun defaultMediaPackage(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT_MEDIA, null)?.takeIf { it.isNotBlank() }

    fun setDefaultMediaPackage(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_DEFAULT_MEDIA, packageName).apply()
    }

    fun mediaCardAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_MEDIA_CARD_ALPHA, MEDIA_CARD_ALPHA_DEFAULT)
            .coerceIn(MEDIA_CARD_ALPHA_MIN, MEDIA_CARD_ALPHA_MAX)

    fun setMediaCardAlpha(context: Context, alpha: Float) {
        val next = alpha.coerceIn(MEDIA_CARD_ALPHA_MIN, MEDIA_CARD_ALPHA_MAX)
        prefs(context).edit().putFloat(KEY_MEDIA_CARD_ALPHA, next).apply()
        mediaCardAlphaRevision.value++
    }

    fun mediaMiniPlayerVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDIA_MINI_PLAYER_VISIBLE, true)

    fun setMediaMiniPlayerVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEDIA_MINI_PLAYER_VISIBLE, visible).apply()
        mediaMiniPlayerRevision.value++
    }

    fun dockIconScale(context: Context): Float =
        prefs(context).getFloat(KEY_DOCK_ICON_SCALE, DOCK_ICON_SCALE_DEFAULT)
            .coerceIn(DOCK_ICON_SCALE_MIN, DOCK_ICON_SCALE_MAX)

    fun setDockIconScale(context: Context, scale: Float) {
        val next = scale.coerceIn(DOCK_ICON_SCALE_MIN, DOCK_ICON_SCALE_MAX)
        prefs(context).edit().putFloat(KEY_DOCK_ICON_SCALE, next).apply()
        dockIconScaleRevision.value++
    }

    fun fuelShowsRange(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FUEL_SHOWS_RANGE, false)

    fun setFuelShowsRange(context: Context, showRange: Boolean) {
        prefs(context).edit().putBoolean(KEY_FUEL_SHOWS_RANGE, showRange).apply()
    }

    fun cruisePresetsKmh(context: Context): List<Int> {
        val parsed = prefs(context).getString(KEY_CRUISE_PRESETS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull()?.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH) }
        return if (parsed != null && parsed.size == CRUISE_PRESET_DEFAULTS_KMH.size) {
            parsed
        } else {
            CRUISE_PRESET_DEFAULTS_KMH
        }
    }

    fun setCruisePresetKmh(context: Context, index: Int, kmh: Int) {
        val next = cruisePresetsKmh(context).toMutableList()
        if (index !in next.indices) return
        next[index] = kmh.coerceIn(CRUISE_PRESET_MIN_KMH, CRUISE_PRESET_MAX_KMH)
        prefs(context).edit().putString(KEY_CRUISE_PRESETS, next.joinToString(",")).apply()
        cruisePresetsRevision.value++
    }

    fun isFullscreenLaunch(context: Context, packageName: String): Boolean =
        packageName.isNotBlank() && packageName in fullscreenPackages(context)

    fun fullscreenPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FULLSCREEN, emptySet()).orEmpty()

    fun setFullscreenLaunch(context: Context, packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        val next = fullscreenPackages(context).toMutableSet()
        if (enabled) next.add(packageName) else next.remove(packageName)
        prefs(context).edit().putStringSet(KEY_FULLSCREEN, next).apply()
    }

    fun hiddenPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet()).orEmpty()

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_HIDDEN, packages).apply()
    }

    fun hidePackage(context: Context, packageName: String) {
        val next = hiddenPackages(context).toMutableSet()
        next.add(packageName)
        setHiddenPackages(context, next)
    }

    fun showPackage(context: Context, packageName: String) {
        val next = hiddenPackages(context).toMutableSet()
        next.remove(packageName)
        setHiddenPackages(context, next)
    }

    fun gridPackages(context: Context): List<String> =
        decodeSlots(prefs(context).getString(KEY_GRID, null), GRID_SLOT_COUNT)

    fun dockPackages(context: Context): List<String> =
        decodeSlots(prefs(context).getString(KEY_DOCK, null), DOCK_SLOT_COUNT)
            .mapIndexed { index, pkg ->
                pkg.ifBlank { DEFAULT_DOCK.getOrElse(index) { "" } }
            }

    fun setGridSlot(context: Context, slotIndex: Int, packageName: String) {
        if (slotIndex !in 0 until GRID_SLOT_COUNT) return
        val slots = gridPackages(context).toMutableList()
        slots[slotIndex] = packageName
        prefs(context).edit().putString(KEY_GRID, encodeSlots(slots)).apply()
    }

    fun setDockSlot(context: Context, slotIndex: Int, packageName: String) {
        if (slotIndex !in 0 until DOCK_SLOT_COUNT) return
        val slots = dockPackages(context).toMutableList()
        slots[slotIndex] = packageName
        prefs(context).edit().putString(KEY_DOCK, encodeSlots(slots)).apply()
    }

    fun clearGridSlot(context: Context, slotIndex: Int) {
        setGridSlot(context, slotIndex, "")
    }

    fun gridSlotEntries(
        allVisible: List<LaunchableAppEntry>,
        pinned: List<String>,
    ): List<LaunchableAppEntry?> {
        val byPackage = allVisible.associateBy { it.packageName }
        return pinned.map { pkg ->
            if (pkg.isBlank()) null else byPackage[pkg]
        }
    }

    fun filterVisible(
        entries: List<LaunchableAppEntry>,
        hidden: Set<String>,
    ): List<LaunchableAppEntry> = entries.filter { it.packageName !in hidden }

    fun resolveGridApps(
        allVisible: List<LaunchableAppEntry>,
        pinned: List<String>,
        priority: List<String>,
    ): List<LaunchableAppEntry> {
        val byPackage = allVisible.associateBy { it.packageName }
        val result = mutableListOf<LaunchableAppEntry>()
        val used = mutableSetOf<String>()

        for (pkg in pinned) {
            if (pkg.isBlank()) continue
            byPackage[pkg]?.let {
                result.add(it)
                used.add(pkg)
            }
        }

        val fillOrder = LauncherOemAppSort.sortEntries(allVisible, priority)
        for (entry in fillOrder) {
            if (result.size >= GRID_SLOT_COUNT) break
            if (entry.packageName in used) continue
            result.add(entry)
            used.add(entry.packageName)
        }
        return result.take(GRID_SLOT_COUNT)
    }

    private fun decodeSlots(raw: String?, count: Int): List<String> {
        val parsed = raw?.split('|').orEmpty()
        return List(count) { index -> parsed.getOrElse(index) { "" } }
    }

    private fun encodeSlots(slots: List<String>): String =
        slots.joinToString("|")
}
