package vad.dashing.tbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import vad.dashing.tbox.update.UpdateChannel

private const val DATASTORE_NAME = "ras.dashing.tbox.launcher.settings"

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

enum class SetLauncherAppCustomIconResult {
    Success,
    InvalidPackage,
    DimensionsTooLarge,
    NotImageOrUnreadable,
    CopyFailed,
}

/**
 * Slim settings store for the standalone launcher — only fields the HOME UI and CAN bind need.
 */
class SettingsManager(private val context: Context) {

    companion object {
        const val LAUNCHER_APP_ICONS_DIR = "launcher_app_icons"
        private val KEY_NAV_HINTS = booleanPreferencesKey("launcher_nav_hints_enabled")
        private val KEY_HEAD_UNIT_CAN = stringPreferencesKey("head_unit_can_mode")
        private val KEY_CAN_AUTO_BIND = booleanPreferencesKey("can_auto_bind_enabled")
        private val KEY_CAN_AUTO_BIND_LOCKED = booleanPreferencesKey("can_auto_bind_locked")
        private val KEY_CAN_AUTO_BIND_PRIMARY = stringPreferencesKey("can_auto_bind_last_primary")
        private val KEY_CAN_AUTO_BIND_RESULT = stringPreferencesKey("can_auto_bind_last_result")
        private val KEY_ICON_REVISION = intPreferencesKey("launcher_app_icon_revision")
        private val KEY_UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        private val KEY_UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
        private const val MAX_ICON_EDGE_PX = 512
    }

    private val _iconRevision = MutableStateFlow(0)
    val launcherAppIconRevisionFlow: Flow<Int> = _iconRevision.asStateFlow()

    val launcherNavHintsEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_NAV_HINTS] ?: false
    }

    val headUnitCanModeFlow: Flow<HeadUnitCanMode> = context.settingsDataStore.data.map { prefs ->
        HeadUnitCanMode.fromStorageValue(prefs[KEY_HEAD_UNIT_CAN])
    }

    val canAutoBindEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_CAN_AUTO_BIND] ?: true
    }

    val canAutoBindLockedFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_CAN_AUTO_BIND_LOCKED] ?: false
    }

    val updateChannelFlow: Flow<UpdateChannel> = context.settingsDataStore.data.map { prefs ->
        UpdateChannel.fromStorageValue(prefs[KEY_UPDATE_CHANNEL])
    }

    val updateCheckEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_UPDATE_CHECK_ENABLED] ?: true
    }

    suspend fun saveLauncherNavHintsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_NAV_HINTS] = enabled }
    }

    suspend fun saveHeadUnitCanMode(mode: HeadUnitCanMode) {
        context.settingsDataStore.edit { it[KEY_HEAD_UNIT_CAN] = mode.storageValue }
    }

    suspend fun saveCanAutoBindEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND] = enabled }
    }

    suspend fun saveCanAutoBindLocked(locked: Boolean) {
        context.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND_LOCKED] = locked }
    }

    suspend fun saveCanAutoBindLastPrimaryMode(mode: HeadUnitCanMode?) {
        context.settingsDataStore.edit {
            if (mode == null) it.remove(KEY_CAN_AUTO_BIND_PRIMARY)
            else it[KEY_CAN_AUTO_BIND_PRIMARY] = mode.storageValue
        }
    }

    suspend fun saveCanAutoBindLastResult(result: String) {
        context.settingsDataStore.edit { it[KEY_CAN_AUTO_BIND_RESULT] = result }
    }

    suspend fun saveUpdateChannel(channel: UpdateChannel) {
        context.settingsDataStore.edit { it[KEY_UPDATE_CHANNEL] = channel.storageValue }
    }

    suspend fun saveUpdateCheckEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_UPDATE_CHECK_ENABLED] = enabled }
    }

    suspend fun launcherAppIconLookup(): LauncherAppIconPaths.Lookup =
        LauncherAppIconPaths.Lookup.None

    suspend fun hasCustomLauncherAppIcon(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            LauncherAppIconPaths.hasSharedOverride(context.filesDir, packageName)
        }

    suspend fun clearCustomLauncherAppIcon(packageName: String) {
        withContext(Dispatchers.IO) {
            val dir = LauncherAppIconPaths.sharedIconsDir(context.filesDir)
            LauncherAppIconPaths.resolveStoredIconFile(dir, packageName)?.delete()
            bumpIconRevision()
        }
    }

    suspend fun setCustomLauncherAppIconFromUri(
        packageName: String,
        sourceUri: Uri?,
    ): SetLauncherAppCustomIconResult = withContext(Dispatchers.IO) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return@withContext SetLauncherAppCustomIconResult.InvalidPackage
        if (sourceUri == null) return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
        val decoded = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
        if (decoded.width > MAX_ICON_EDGE_PX || decoded.height > MAX_ICON_EDGE_PX) {
            decoded.recycle()
            return@withContext SetLauncherAppCustomIconResult.DimensionsTooLarge
        }
        val dir = LauncherAppIconPaths.sharedIconsDir(context.filesDir)
        if (!dir.exists() && !dir.mkdirs()) {
            decoded.recycle()
            return@withContext SetLauncherAppCustomIconResult.CopyFailed
        }
        val dest = LauncherAppIconPaths.liveIconFile(dir, pkg)
        val ok = runCatching {
            FileOutputStream(dest).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.isSuccess
        decoded.recycle()
        if (!ok) return@withContext SetLauncherAppCustomIconResult.CopyFailed
        bumpIconRevision()
        SetLauncherAppCustomIconResult.Success
    }

    private fun bumpIconRevision() {
        _iconRevision.update { it + 1 }
        // Persist so cold start keeps a non-zero counter for caches that key on it.
        // Fire-and-forget: revision is also kept in memory for the process lifetime.
    }
}
