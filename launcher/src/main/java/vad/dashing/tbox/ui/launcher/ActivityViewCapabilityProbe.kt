package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Runtime probe for OEM ActivityView embedding from a normal /data app.
 */
internal object ActivityViewCapabilityProbe {
    private const val TAG = "ActivityViewProbe"
    @Volatile private var ran = false

    fun resetForRetest() {
        ran = false
    }

    fun runOnce(context: Context) {
        if (ran) return
        ran = true
        logPermissionState(context)
        val host = context as? android.app.Activity ?: run {
            Log.w(TAG, "no Activity host")
            return
        }
        try {
            val cls = Class.forName("android.app.ActivityView")
            Log.w(TAG, "class OK: ${cls.name}")
            Log.w(
                TAG,
                if (runCatching { Class.forName("com.wt.utils.WtActivityView") }.isSuccess) {
                    "WtActivityView OK"
                } else {
                    "WtActivityView missing"
                },
            )

            val activityView = cls.getConstructor(Context::class.java).newInstance(host) as View
            Log.w(TAG, "CONSTRUCT OK")

            // Log declared methods useful for embedding.
            cls.declaredMethods
                .filter {
                    it.name in setOf(
                        "startActivity",
                        "setCallback",
                        "release",
                        "onMovedToDisplay",
                        "setSurfaceCallback",
                    ) || it.name.contains("Callback", ignoreCase = true)
                }
                .forEach { Log.w(TAG, "api ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})") }

            val lp = FrameLayout.LayoutParams(800, 500, Gravity.CENTER)
            val decor = host.window.decorView as ViewGroup
            decor.addView(activityView, lp)
            Log.w(TAG, "attached to decorView size=800x500")

            // StateCallback is an abstract class — can't Proxy it. Retry start after surface init.
            listOf(300L, 800L, 1600L, 3000L).forEach { delayMs ->
                host.window.decorView.postDelayed({
                    tryStart(activityView, host, delayMs)
                }, delayMs)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PROBE FAIL: ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }

    private fun tryStart(activityView: View, host: android.app.Activity, delayMs: Long) {
        try {
            val intent = host.packageManager.getLaunchIntentForPackage("com.android.settings")
                ?: Intent().setClassName("com.android.settings", "com.android.settings.Settings")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val start = activityView.javaClass.getMethod("startActivity", Intent::class.java)
            start.invoke(activityView, intent)
            Log.w(TAG, "startActivity INVOKED @${delayMs}ms -> ${intent.component}")
        } catch (t: Throwable) {
            val root = generateSequence(t) { it.cause }.last()
            Log.e(
                TAG,
                "startActivity FAIL @${delayMs}ms: ${root.javaClass.simpleName}: ${root.message}",
            )
        }
    }

    private fun logPermissionState(context: Context) {
        val perms = listOf(
            "android.permission.MANAGE_ACTIVITY_STACKS",
            "android.permission.ACTIVITY_EMBEDDING",
            "android.permission.INJECT_EVENTS",
            "android.permission.INTERNAL_SYSTEM_WINDOW",
        )
        perms.forEach { p ->
            val granted = context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.w(TAG, "perm $p granted=$granted")
        }
    }
}
