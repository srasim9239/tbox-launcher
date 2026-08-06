package vad.dashing.tbox.ui.launcher

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.widget.RemoteViews
import java.lang.reflect.Field

/**
 * Reflection helpers for reading custom notification RemoteViews
 * (same approach as YNarrows RemoteViewsUtils).
 */
internal object RemoteViewsNavExtractor {

    data class ActionPair(
        val viewIdName: String,
        /** Prefix (8 chars) + value, e.g. "setText:250 м" / "setImRe:notification_left_sdl". */
        val prefixedValue: String,
    )

    fun extractRemoteViews(notification: Notification, fieldName: String = "contentView"): RemoteViews? {
        try {
            val field = Notification::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(notification) as? RemoteViews
        } catch (_: Exception) {
            // Fall through to public accessors where available.
        }
        return when (fieldName) {
            "contentView" -> notification.contentView
            "bigContentView" -> notification.bigContentView
            "headsUpContentView" -> notification.headsUpContentView
            else -> null
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun getRemoteViewActions(
        context: Context,
        remoteViews: RemoteViews,
        sourcePackage: String,
    ): List<ActionPair> {
        val actionsField = findField(RemoteViews::class.java, "mActions", "actions") ?: return emptyList()
        val rawActions = try {
            actionsField.get(remoteViews)
        } catch (_: Exception) {
            return emptyList()
        }
        if (rawActions !is ArrayList<*>) return emptyList()

        val result = ArrayList<ActionPair>(rawActions.size)
        for (action in rawActions) {
            if (action == null) continue
            toActionPair(context, action, sourcePackage)?.let { result.add(it) }
        }
        return result
    }

    private fun toActionPair(context: Context, action: Any, sourcePackage: String): ActionPair? {
        val actionClass = action.javaClass
        val methodName = readStringField(action, actionClass, "methodName", "mMethodName") ?: return null
        val prefix = when (methodName) {
            "setText" -> "setText:"
            "setImageResource" -> "setImRe:"
            "setBackgroundResource" -> "setBRes:"
            "setVisibility" -> "SetVisi:"
            else -> return null
        }

        val viewId = readViewId(action, actionClass) ?: return null
        val viewIdName = resolveResourceEntryName(context, sourcePackage, viewId) ?: viewId.toString()

        val value = readValueField(action, actionClass) ?: return null
        val combined = when (prefix) {
            "setImRe:", "setBRes:" -> {
                val resId = value as? Int ?: return null
                val name = resolveResourceEntryName(context, sourcePackage, resId)
                prefix + (name ?: resId.toString())
            }
            else -> prefix + value.toString()
        }
        return ActionPair(viewIdName, combined)
    }

    private fun readViewId(action: Any, actionClass: Class<*>): Int? {
        val superClass = actionClass.superclass ?: return null
        for (field in superClass.declaredFields) {
            if (field.name == "viewId" || field.name == "mViewId") {
                field.isAccessible = true
                return try {
                    field.getInt(action)
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun readValueField(action: Any, actionClass: Class<*>): Any? {
        val field = findField(actionClass, "value", "mValue") ?: return null
        return try {
            field.get(action)
        } catch (_: Exception) {
            null
        }
    }

    private fun readStringField(action: Any, actionClass: Class<*>, vararg names: String): String? {
        val field = findField(actionClass, *names) ?: return null
        return try {
            field.get(action) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun findField(clazz: Class<*>, vararg names: String): Field? {
        for (name in names) {
            try {
                return clazz.getDeclaredField(name).also { it.isAccessible = true }
            } catch (_: NoSuchFieldException) {
                // try next
            }
        }
        return null
    }

    private fun resolveResourceEntryName(context: Context, sourcePackage: String, resId: Int): String? {
        return try {
            val resources = if (sourcePackage == context.packageName) {
                context.resources
            } else {
                context.createPackageContext(sourcePackage, 0).resources
            }
            resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            null
        }
    }
}
