package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.theme.tboxCaption

private const val LAUNCHER_MEDIA_SOURCE_ID = "launcher_mini_player"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherMediaMiniPlayer(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var defaultRevision by remember { mutableIntStateOf(0) }
    var pickerVisible by remember { mutableStateOf(false) }
    var pickerFallback by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val playerStates by SharedMediaControlService.playerStates.collectAsStateWithLifecycle()
    val mediaAlphaRevision by LauncherAppConfigStore.mediaCardAlphaRevisionFlow
        .collectAsStateWithLifecycle()
    val mediaCardAlpha = remember(context, mediaAlphaRevision) {
        LauncherAppConfigStore.mediaCardAlpha(context)
    }

    // The grant may change while HOME stays resumed (settings open as freeform), so
    // lifecycle-resume checks are not enough — observe the Secure setting directly.
    val listenerComponent = remember {
        android.content.ComponentName(
            context,
            vad.dashing.tbox.MediaControlNotificationListenerService::class.java,
        )
    }
    DisposableEffect(context) {
        val uri = Settings.Secure.getUriFor("enabled_notification_listeners")
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                permissionTick++
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val notificationAccessGranted = remember(permissionTick) {
        isNotificationListenerEnabled(context, listenerComponent)
    }

    val defaultPackage = remember(context, defaultRevision) {
        LauncherAppConfigStore.defaultMediaPackage(context)
    }
    val mediaPackages = remember(context, defaultRevision, playerStates) {
        (discoverAllMediaPlayerPackages(context, defaultPackage) + playerStates.keys).distinct()
    }
    val monitorPackages = remember(mediaPackages, defaultPackage) {
        buildSet {
            defaultPackage?.let { add(it) }
            addAll(mediaPackages)
        }
    }

    DisposableEffect(context, monitorPackages) {
        if (monitorPackages.isNotEmpty()) {
            SharedMediaControlService.updateSourceSelection(context, LAUNCHER_MEDIA_SOURCE_ID, monitorPackages)
        }
        onDispose {
            SharedMediaControlService.clearSourceSelection(LAUNCHER_MEDIA_SOURCE_ID)
        }
    }

    val mediaPickerApps = remember(context, mediaPackages) {
        val pm = context.packageManager
        mediaPackages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = runCatching {
                    val sizePx = (64f * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)
                    val drawable = pm.getApplicationIcon(info)
                    if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                        drawable.setBounds(0, 0, sizePx, sizePx)
                    }
                    drawable.toBitmap(sizePx, sizePx).asImageBitmap()
                }.getOrNull()
                LaunchableAppEntry(packageName = pkg, label = label, icon = icon, activityName = null)
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    val preferredPackage = defaultPackage?.takeIf { it in monitorPackages }
        ?: mediaPackages.firstOrNull().orEmpty()
    val mediaState = remember(monitorPackages, playerStates, preferredPackage) {
        SharedMediaControlService.resolveWidgetState(
            selectedPackages = monitorPackages,
            currentStates = playerStates,
            preferredPackage = preferredPackage,
        )
    }
    val activePkg = preferredPackage.ifBlank { mediaPackages.firstOrNull().orEmpty() }
    val openActivePlayer: () -> Unit = {
        activePkg.takeIf { it.isNotBlank() }?.let { launchLauncherApp(context, it) }
    }
    val title = mediaState.track.ifBlank { stringResource(R.string.launcher_media_no_track) }
    val artist = mediaState.artist
    val isPlaying = mediaState.isPlaying
    val albumArtBitmap = remember(activePkg, playerStates, title) {
        runCatching {
            SharedMediaControlService.albumArtFor(activePkg)?.asImageBitmap()
        }.getOrNull()
    }
    // Tint the card towards the album art's dark accent so the widget follows the music.
    val mediaCardTarget = remember(albumArtBitmap) {
        val bitmap = albumArtBitmap ?: return@remember LauncherColors.LeftPanelCard
        runCatching {
            val swatch = androidx.palette.graphics.Palette.from(bitmap.asAndroidBitmap()).generate().let {
                it.darkVibrantSwatch ?: it.darkMutedSwatch ?: it.dominantSwatch
            }
            swatch?.let { lerp(LauncherColors.LeftPanelCard, Color(it.rgb), 0.42f) }
        }.getOrNull() ?: LauncherColors.LeftPanelCard
    }
    val mediaCardColor by animateColorAsState(
        targetValue = mediaCardTarget.copy(alpha = mediaCardAlpha),
        label = "mediaCardTint",
    )

    val pickerTitle = stringResource(R.string.launcher_media_bind_player)
    LaunchedEffect(pickerVisible) {
        LauncherOverlayElevator.setHoldSource("media_picker", pickerVisible)
        if (pickerVisible) {
            val shown = LauncherAppPickerOverlayWindow.show(
                context = context,
                title = pickerTitle,
                apps = mediaPickerApps,
                onPick = { entry ->
                    LauncherAppConfigStore.setDefaultMediaPackage(context, entry.packageName)
                    defaultRevision++
                    pickerVisible = false
                },
                onDismiss = { pickerVisible = false },
            )
            pickerFallback = !shown
        } else {
            LauncherAppPickerOverlayWindow.hide()
            pickerFallback = false
        }
    }

    if (pickerVisible && pickerFallback) {
    LauncherAppPickerDialog(
            visible = true,
            title = pickerTitle,
        apps = mediaPickerApps,
        onDismiss = { pickerVisible = false },
        onPick = { entry ->
            LauncherAppConfigStore.setDefaultMediaPackage(context, entry.packageName)
            defaultRevision++
            pickerVisible = false
        },
    )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mediaCardColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LauncherColors.LeftPanelBg)
                    .clickable(enabled = activePkg.isNotBlank(), onClick = openActivePlayer),
                contentAlignment = Alignment.Center,
            ) {
                if (albumArtBitmap != null && notificationAccessGranted) {
                    Image(
                        bitmap = albumArtBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("♪", fontSize = 20.sp, color = LauncherColors.LeftTextSecondary)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = notificationAccessGranted && activePkg.isNotBlank(),
                        onClick = openActivePlayer,
                    ),
            ) {
                if (!notificationAccessGranted) {
                    Text(
                        text = stringResource(R.string.widget_music_access_required),
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.AccentCyan,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.clickable { openNotificationListenerSettings(context) },
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.LeftTextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.tboxCaption,
                            color = LauncherColors.LeftTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                }
            }
            if (mediaState.supportsLike && notificationAccessGranted) {
                IconButton(
                    onClick = {
                        SharedMediaControlService.toggleLike(
                            selectedPackages = monitorPackages,
                            preferredPackage = activePkg,
                        )
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (mediaState.isLiked == true) R.drawable.ic_launcher_heart
                            else R.drawable.ic_launcher_heart_outline,
                        ),
                        contentDescription = null,
                        tint = if (mediaState.isLiked == true) Color(0xFFE57373) else LauncherColors.LeftTextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(
                onClick = { pickerVisible = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.launcher_media_bind_player),
                    tint = LauncherColors.LeftTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherMediaIconButton(
                iconRes = R.drawable.skip_previous,
                enabled = notificationAccessGranted && activePkg.isNotBlank(),
                onClick = {
                    SharedMediaControlService.skipToPrevious(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
            )
            LauncherMediaIconButton(
                iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                iconSize = 28.dp,
                buttonSize = 44.dp,
                enabled = activePkg.isNotBlank(),
                onClick = {
                    SharedMediaControlService.playPause(
                        context = context,
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                        keepPlayerForeground = true,
                        launchAppIfNeeded = true,
                    )
                },
            )
            LauncherMediaIconButton(
                iconRes = R.drawable.next_track,
                enabled = notificationAccessGranted && activePkg.isNotBlank(),
                onClick = {
                    SharedMediaControlService.skipToNext(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
            )
        }
    }
}

@Composable
private fun LauncherMediaIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    buttonSize: androidx.compose.ui.unit.Dp = 40.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) LauncherColors.LeftTextPrimary else LauncherColors.TextMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun openNotificationListenerSettings(context: Context) {
    launchSystemSettingsInFreeform(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
