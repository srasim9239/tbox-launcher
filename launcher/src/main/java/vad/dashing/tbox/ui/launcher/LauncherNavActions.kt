package vad.dashing.tbox.ui.launcher

import android.app.ActivityManager
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import vad.dashing.tbox.LauncherForegroundHandoff
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.LauncherVehicleSettingsActivity

private const val TAG = "LauncherNav"
private const val INJECT_ASYNC = 0
private const val HOME_DEBOUNCE_MS = 400L

@Volatile
private var lastHomeAtMs = 0L

/**
 * Home: close overlays, dismiss freeform windows, bring launcher to front.
 *
 * [fromHomeIntent]=true when already handling ACTION_MAIN+HOME (onNewIntent).
 * In that case we must NOT startActivity(HOME) again — that re-enters onNewIntent
 * and freezes the UI in a tight loop.
 */
internal fun goLauncherHome(
    context: Context,
    onCloseOverlays: () -> Unit = {},
    fromHomeIntent: Boolean = false,
) {
    val now = SystemClock.uptimeMillis()
    if (now - lastHomeAtMs < HOME_DEBOUNCE_MS) {
        Log.w(TAG, "goLauncherHome: debounce skip fromHomeIntent=$fromHomeIntent")
        return
    }
    lastHomeAtMs = now
    Log.w(TAG, "goLauncherHome fromHomeIntent=$fromHomeIntent")

    onCloseOverlays()
    closeVehicleSettingsOverlay()
    LauncherForegroundHandoff.restoreLauncherWindow()
    LauncherOverlayElevator.clearHoldWithoutRestore()

    dismissForeignFreeformTasks(context)

    val home = LauncherHomeActivityHolder.instance
    if (home != null) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(home.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            Log.w(TAG, "goLauncherHome: moveTaskToFront home=${home.taskId}")
        }.onFailure {
            Log.w(TAG, "goLauncherHome: moveTaskToFront failed", it)
        }
    }

    if (fromHomeIntent) return

    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
    }
}

/**
 * Back: close launcher overlays first; otherwise deliver KEYCODE_BACK like the
 * physical button (after focusing the foreign freeform/fullscreen app).
 */
internal fun goLauncherBack(
    context: Context,
    vehicleSettingsOpen: Boolean,
    appDrawerOpen: Boolean,
    onCloseVehicleSettings: () -> Unit,
    onCloseAppDrawer: () -> Unit,
) {
    when {
        vehicleSettingsOpen ||
            LauncherVehicleSettingsUiState.open ||
            LauncherVehicleSettingsActivity.isOpen() -> {
            onCloseVehicleSettings()
            closeVehicleSettingsOverlay()
            return
        }
        appDrawerOpen -> {
            onCloseAppDrawer()
            return
        }
    }

    val foreign = findForeignAppTask(context)
    if (foreign == null) {
        Log.w(TAG, "goLauncherBack: no foreign app — ignore (do not close launcher)")
        return
    }

    // Guard against killing the freeform app with an extra BACK:
    // BACK must stop at the app's main page, never finish its root activity.
    if (isForeignTaskAtAppRoot(context, foreign)) {
        Log.w(TAG, "goLauncherBack: skip root BACK task=${foreign.taskId} pkg=${foreign.packageName}")
        return
    }

    // Focus freeform quietly (setFocusedTask), then BACK — avoid moveTaskToFront flash.
    Log.w(TAG, "goLauncherBack: focus+BACK task=${foreign.taskId} pkg=${foreign.packageName}")
    focusTaskQuietly(foreign.taskId)
    dispatchPhysicalBackDelayed()
}

/**
 * True when the foreign task is at (or near) its root activity, where BACK would close
 * the app. Exact activity counts are unavailable under the app uid on this Android 9 HU
 * (getRunningTasks needs the signature-only REAL_GET_TASKS, dumpsys needs DUMP), so the
 * fallback heuristic compares the task's top activity with the app's launcher component.
 */
private fun isForeignTaskAtAppRoot(context: Context, foreign: ForeignAppTask): Boolean {
    val depth = taskActivityCount(context, foreign.taskId)
    if (depth >= 0) return depth <= 1

    val top = foreign.topActivityClass
    if (top == null) {
        Log.w(TAG, "isAtRoot: top activity unknown task=${foreign.taskId} — treat as root")
        return true
    }
    val launchClass = runCatching {
        context.packageManager.getLaunchIntentForPackage(foreign.packageName)
            ?.component?.className
    }.getOrNull()
    if (launchClass == null) {
        Log.w(TAG, "isAtRoot: no launch component for ${foreign.packageName} — treat as root")
        return true
    }
    val normalizedTop =
        if (top.startsWith(".")) foreign.packageName + top else top
    val atRoot = normalizedTop == launchClass
    Log.w(TAG, "isAtRoot: task=${foreign.taskId} top=$normalizedTop launch=$launchClass atRoot=$atRoot")
    return atRoot
}

/**
 * Number of activities in [taskId]. Android 10+: `IActivityTaskManager.getTasks` (works
 * under the app uid thanks to the MANAGE_ACTIVITY_STACKS grant). Android 9 (the HU):
 * `ActivityManager.getRunningTasks`, which returns foreign tasks only when REAL_GET_TASKS
 * (protectionLevel development) is granted via `pm grant`. Returns -1 when unknown.
 */
private fun taskActivityCount(context: Context, taskId: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        taskCountViaActivityTaskManager(taskId)
    } else {
        taskCountViaRunningTasks(context, taskId)
    }
}

private fun taskCountViaActivityTaskManager(taskId: Int): Int {
    val count = runCatching {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val service = atmClass.getDeclaredMethod("getService").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val tasks = service.javaClass
            .getMethod("getTasks", Int::class.javaPrimitiveType)
            .invoke(service, 200) as List<ActivityManager.RunningTaskInfo>
        tasks.firstOrNull { it.taskId == taskId }?.numActivities
    }.onFailure {
        Log.w(TAG, "taskActivityCount: getTasks failed for task=$taskId", it)
    }.getOrNull()
    return count ?: -1
}

@Suppress("DEPRECATION")
private fun taskCountViaRunningTasks(context: Context, taskId: Int): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return -1
    val count = runCatching {
        am.getRunningTasks(100)
            ?.firstOrNull { it.id == taskId }
            ?.numActivities
    }.onFailure {
        Log.w(TAG, "taskActivityCount: getRunningTasks failed for task=$taskId", it)
    }.getOrNull()
    return count ?: -1
}

/**
 * Give WM a beat to settle window focus after setFocusedTask, then dispatch Back off the
 * main thread (the fallback paths may block briefly).
 */
private fun dispatchPhysicalBackDelayed() {
    Thread({
        runCatching { Thread.sleep(220L) }
        dispatchPhysicalBack()
    }, "launcher-back").apply { isDaemon = true }.start()
}

/** Sends exactly the same global key event as the hardware Back button. */
internal fun dispatchPhysicalBack() {
    // Preferred path: accessibility global action — needs no INJECT_EVENTS and reaches the
    // focused foreign window. The service must be enabled in system accessibility settings.
    if (LauncherNavAccessibilityService.dispatchGlobalBack()) {
        Log.w(TAG, "BACK dispatched via accessibility global action")
        return
    }
    if (injectKeyEvent(KeyEvent.KEYCODE_BACK)) return
    runCatching {
        ProcessBuilder("input", "keyevent", KeyEvent.KEYCODE_BACK.toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }.onFailure {
        Log.w(TAG, "input keyevent BACK failed", it)
    }
}

/** Inject a key via InputManager (system nav-bar style). */
internal fun injectKeyEvent(keyCode: Int): Boolean {
    return runCatching {
        val imClass = InputManager::class.java
        val im = imClass.getDeclaredMethod("getInstance").invoke(null)
        val inject = imClass.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
        )
        val up = KeyEvent(
            now + 10, now + 10, KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
        )
        inject.invoke(im, down, INJECT_ASYNC)
        inject.invoke(im, up, INJECT_ASYNC)
        Log.w(TAG, "injectKeyEvent keyCode=$keyCode OK")
        true
    }.onFailure {
        Log.w(TAG, "injectKeyEvent keyCode=$keyCode failed", it)
    }.getOrDefault(false)
}

/** Prefer setFocusedTask (no z-order jump) over moveTaskToFront. */
private fun focusTaskQuietly(taskId: Int): Boolean {
    val ok = runCatching {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val service = atmClass.getDeclaredMethod("getService").invoke(null)
        service.javaClass.getMethod("setFocusedTask", Int::class.javaPrimitiveType)
            .invoke(service, taskId)
        true
    }.recoverCatching {
        val amClass = Class.forName("android.app.ActivityManager")
        val getService = amClass.getDeclaredMethod("getService")
        getService.isAccessible = true
        val service = getService.invoke(null)
        service.javaClass.getMethod("setFocusedTask", Int::class.javaPrimitiveType)
            .invoke(service, taskId)
        true
    }.onFailure {
        Log.w(TAG, "focusTaskQuietly failed task=$taskId", it)
    }.getOrDefault(false)
    if (ok) Log.w(TAG, "focusTaskQuietly OK task=$taskId")
    return ok
}

private data class ForeignAppTask(
    val taskId: Int,
    val packageName: String,
    val topActivityClass: String?,
)

private fun findForeignAppTask(context: Context): ForeignAppTask? {
    val launcher = context.packageName
    val stacks = LauncherAmStackShell.listStacks()
    val freeform = stacks.firstOrNull { row ->
        row.windowingMode.equals("freeform", ignoreCase = true) &&
            row.packageName != null &&
            row.packageName != launcher &&
            row.taskId != null
    }
    if (freeform?.taskId != null && freeform.packageName != null) {
        return ForeignAppTask(freeform.taskId, freeform.packageName, freeform.topActivityClass)
    }

    for (pkg in FreeformLaunchRegistry.snapshot()) {
        val row = stacks.firstOrNull { it.packageName == pkg && it.taskId != null }
        if (row?.taskId != null) return ForeignAppTask(row.taskId, pkg, row.topActivityClass)
    }

    val visibleOther = stacks.firstOrNull { row ->
        row.visible &&
            row.packageName != null &&
            row.packageName != launcher &&
            row.packageName != "com.wt.launcher3" &&
            row.packageName != "com.android.launcherWT" &&
            row.packageName != "ca.dstudio.atvlauncher" &&
            row.taskId != null
    }
    if (visibleOther?.taskId != null && visibleOther.packageName != null) {
        return ForeignAppTask(visibleOther.taskId, visibleOther.packageName, visibleOther.topActivityClass)
    }
    return null
}
