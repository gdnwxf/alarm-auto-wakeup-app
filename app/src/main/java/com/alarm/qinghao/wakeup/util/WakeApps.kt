package com.alarm.qinghao.wakeup.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alarm.qinghao.wakeup.data.AlarmEntity

/**
 * 响铃时唤醒目标应用。
 *
 * 两条硬约束：
 * 1. 必须由可见的 Activity 发起，否则 Android 10+ 的后台启动限制会拦截；
 * 2. 单次响铃最多拉起 [MAX_TARGETS] 个应用，避免连续启动大量 Activity 拖死低端设备。
 *
 * 延迟任务可取消（[cancelPending]），页面销毁后不会再弹出应用。
 * 全部执行完毕后通过 `onFinished` 回传每个应用的结果与失败原因，供通知栏提示使用。
 */
object WakeApps {

    private const val TAG = "WakeApps"

    /** 单次响铃最多唤醒的应用数量。 */
    const val MAX_TARGETS = 5

    data class LaunchResult(
        val packageName: String,
        val label: String,
        val success: Boolean,
        val reason: String? = null
    )

    private val handler = Handler(Looper.getMainLooper())
    private val pendingTasks = mutableListOf<Runnable>()

    fun launchAll(
        host: Activity,
        alarm: AlarmEntity,
        onFinished: (List<LaunchResult>) -> Unit = {}
    ) {
        // 新一次响铃覆盖上一次未执行的队列
        cancelPending()

        val targets = alarm.wakeAppPackages
            .filter { it.isNotBlank() }
            .take(MAX_TARGETS)
        if (targets.isEmpty()) {
            onFinished(emptyList())
            return
        }

        val intervalMillis =
            if (alarm.wakeAppParallel) 0L else alarm.wakeAppDelaySeconds.coerceIn(0, 60) * 1000L

        val results = mutableListOf<LaunchResult>()
        var finishedCount = 0

        targets.forEachIndexed { index, packageName ->
            val task = Runnable {
                val result = launchOne(host, packageName)
                results += result
                finishedCount++
                if (finishedCount == targets.size) {
                    // 全部执行完立即释放对响铃页的引用
                    pendingTasks.clear()
                    onFinished(results.toList())
                }
            }
            pendingTasks += task
            handler.postDelayed(task, intervalMillis * index)
        }
    }

    /** 取消所有尚未执行的启动任务（响铃页销毁时调用）。 */
    fun cancelPending() {
        pendingTasks.forEach { handler.removeCallbacks(it) }
        pendingTasks.clear()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun launchOne(host: Activity, packageName: String): LaunchResult {
        val label = labelOf(host, packageName)

        if (host.isFinishing || host.isDestroyed) {
            Log.i(TAG, "响铃页已结束，跳过启动 $packageName")
            return LaunchResult(packageName, label, false, "响铃页已关闭，未执行")
        }

        val launchIntent = host.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult(
                packageName,
                label,
                false,
                "应用未安装、被禁用或没有桌面入口"
            ).also { Log.w(TAG, "应用不可启动：$packageName") }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        return runCatching { host.startActivity(launchIntent) }
            .fold(
                onSuccess = { LaunchResult(packageName, label, true) },
                onFailure = { error ->
                    Log.w(TAG, "启动应用失败：$packageName", error)
                    LaunchResult(
                        packageName,
                        label,
                        false,
                        "系统拒绝启动：" + (error.message ?: error.javaClass.simpleName)
                    )
                }
            )
    }

    /** 供设置页校验：目标应用是否仍然可启动。 */
    fun isLaunchable(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    /** 解析应用显示名，失败时回退包名。 */
    fun labelOf(context: Context, packageName: String): String = runCatching {
        val packageManager = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
