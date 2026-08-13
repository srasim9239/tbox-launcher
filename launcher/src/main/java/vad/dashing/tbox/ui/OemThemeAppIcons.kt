package vad.dashing.tbox.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import com.mengbo.mbconfig.utils.PropertyUtils

/**
 * Stock Launcher3 / NegativeScreen do **not** use the app's own `ic_launcher`
 * for many OEM packages. They paint themed drawables (`app_ic_*`) from
 * [com.android.launcherWT] and/or the active WT theme package
 * (`persist.wt.theme.path`, usually [com.autopai.theme.black]).
 *
 * Example: 360AVM (`com.mengbo.avm`) ships a wrong shared car/settings PNG in
 * its APK; the real icon is `app_ic_360` / `app_ic_quanjingyingxiang`.
 */
internal object OemThemeAppIcons {
    private const val TAG = "LauncherIcons"

    private const val PKG_NEGATIVE_SCREEN = "com.android.launcherWT"
    private const val PKG_THEME_BLACK = "com.autopai.theme.black"
    private const val PKG_THEME_CLASSIC = "com.autopai.theme.classic"
    private const val PROP_THEME_PATH = "persist.wt.theme.path"

    /** packageName → preferred drawable resource names (first hit wins). */
    private val packageIconNames: Map<String, List<String>> = mapOf(
        "com.mengbo.avm" to listOf("app_ic_360", "app_ic_quanjingyingxiang"),
        "com.mengbo.avmconfig" to listOf("app_ic_360", "app_ic_quanjingyingxiang"),
        "com.mengbo.systemsettings" to listOf("app_ic_shezhi"),
        "com.autopai.system.settings" to listOf("app_ic_shezhi"),
        "com.mengbo.acsettings" to listOf("app_ic_kongtiao"),
        "com.wt.airconditioner" to listOf("app_ic_kongtiao"),
        "com.mengbo.carsettings" to listOf("app_ic_cheliangkongzhi"),
        "com.wt.vehiclesettings" to listOf("app_ic_cheliangkongzhi"),
        "com.mengbo.usercenter" to listOf("app_ic_yonghuzhongxin"),
        "com.autopai.usercenter" to listOf("app_ic_yonghuzhongxin"),
        "com.mengbo.carota" to listOf("app_ic_cheliangshenji", "app_ic_xitongshenji"),
        "com.wutong.fota" to listOf("app_ic_xitongshenji", "app_ic_cheliangshenji"),
        "com.wtcl.filemanager" to listOf("app_ic_wenjianguanli"),
        "com.wt.multimedia.local" to listOf("app_ic_bendimeiti"),
        "com.wt.multimedia.platform3" to listOf("app_ic_meitizhongxin"),
        "com.mengbo.electronicmanual" to listOf("app_ic_dianzishuji"),
        "com.mengbo.phevhybrid" to listOf("app_ic_hunhedongli"),
        "com.mengbo.aiservice" to listOf("app_ic_yuyinkongzhi", "app_ic_xiaotong"),
        "com.iflytek.cutefly.speechclient.hmi" to listOf("app_ic_yuyinkongzhi", "app_ic_xiaotong"),
        "com.autopai.car.dialer" to listOf("app_ic_dianhua"),
        "com.tencent.mm" to listOf("app_ic_weixin"),
        "com.tencent.wecarnavi" to listOf("app_ic_gaode", "app_ic_map", "app_ic_tencent"),
        "com.tencent.wecarflow" to listOf("app_ic_tencent", "app_ic_meitizhongxin"),
        "com.wt.gamecenter" to listOf("app_ic_youxi"),
        "com.wt.roadbook" to listOf("app_ic_lushu"),
        "com.wt.thememall" to listOf("app_ic_zhutishangcheng"),
        "com.wt.scenecenter" to listOf("app_ic_xiaochangjing", "app_ic_zhinengchangjing"),
        "com.mengbo.varietyshop" to listOf("app_ic_zhutishangcheng", "app_ic_more"),
        "net.easyconn" to listOf("app_ic_hicar"),
        "com.huawei.maps.auto.app" to listOf("app_ic_gaode", "app_ic_map", "app_ic_baidu"),
    )

    fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        val names = packageIconNames[packageName] ?: return null
        val pm = context.packageManager
        for (host in themeHostPackages()) {
            val res = runCatching { pm.getResourcesForApplication(host) }.getOrNull() ?: continue
            for (name in names) {
                val id = runCatching { res.getIdentifier(name, "drawable", host) }.getOrDefault(0)
                if (id == 0) continue
                val drawable = runCatching {
                    @Suppress("DEPRECATION")
                    res.getDrawable(id)
                }.getOrNull()
                val bitmap = rasterizeThemeDrawable(drawable, sizePx) ?: continue
                Log.d(TAG, "oem-theme ok $packageName ← $host/$name")
                return bitmap
            }
        }
        return null
    }

    private fun themeHostPackages(): List<String> {
        val fromProp = runCatching {
            val path = PropertyUtils.getProperty(PROP_THEME_PATH, "")
            when {
                path.contains("ThemeResourcesBlack", ignoreCase = true) -> PKG_THEME_BLACK
                path.contains("ThemeResourcesClassic", ignoreCase = true) -> PKG_THEME_CLASSIC
                else -> null
            }
        }.getOrNull()
        // NegativeScreen ships the highest-res OEM app icons (app_ic_360, …).
        return listOfNotNull(PKG_NEGATIVE_SCREEN, fromProp, PKG_THEME_BLACK, PKG_THEME_CLASSIC)
            .distinct()
    }

    private fun rasterizeThemeDrawable(drawable: Drawable?, sizePx: Int): ImageBitmap? =
        LauncherAppIconLoader.rasterizeForTheme(drawable, sizePx)
}
