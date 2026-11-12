package com.alarm.qinghao.wakeup.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/** 可选择的目标应用 */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

object InstalledApps {

    private const val TAG = "InstalledApps"

    /**
     * 读取所有带桌面入口的应用。耗时操作，必须在 IO 线程调用。
     * 已排除本应用自身。
     */
    fun load(context: Context): List<LaunchableApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(launcherIntent, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询可启动应用失败", e)
            return emptyList()
        }

        val result = LinkedHashMap<String, LaunchableApp>()
        for (info in resolved) {
            val packageName = info.activityInfo?.applicationInfo?.packageName ?: continue
            if (packageName == context.packageName) continue
            if (result.containsKey(packageName)) continue

            val label = runCatching { info.loadLabel(packageManager).toString() }
                .getOrDefault(packageName)
            val icon = runCatching {
                info.loadIcon(packageManager).toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap()
            }.getOrNull()

            result[packageName] = LaunchableApp(packageName, label, icon)
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    private const val ICON_SIZE = 56

    /** 应用显示名缓存，避免列表反复重组时重复查询 PackageManager。 */
    private val labelCache = ConcurrentHashMap<String, String>()

    /** 解析应用显示名，失败时回退包名；结果进程内缓存。 */
    fun labelOf(context: Context, packageName: String): String {
        labelCache[packageName]?.let { return it }
        val label = runCatching {
            val packageManager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        labelCache[packageName] = label
        return label
    }
}
