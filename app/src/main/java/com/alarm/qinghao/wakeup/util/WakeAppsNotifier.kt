package com.alarm.qinghao.wakeup.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alarm.qinghao.wakeup.MainActivity
import com.alarm.qinghao.wakeup.R

/**
 * 唤醒目标应用失败时的顶部通知。
 * 只在确实有失败项时发送，成功场景不打扰用户。
 */
object WakeAppsNotifier {

    private const val TAG = "WakeAppsNotifier"
    private const val NOTIFICATION_ID = 3001
    private const val REQUEST_CODE = 3002
    private const val MAX_DETAILS = 3

    fun notifyFailures(context: Context, results: List<WakeApps.LaunchResult>) {
        val failed = results.filter { !it.success }
        if (failed.isEmpty()) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "通知权限未开启，跳过失败提醒")
            return
        }

        val details = failed.take(MAX_DETAILS)
            .joinToString("\n") { "${it.label}：${it.reason ?: "未知原因"}" }
        val more = if (failed.size > MAX_DETAILS) "\n等共 ${failed.size} 个" else ""

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.WAKE_FAILED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("未能打开 ${failed.size} 个唤醒应用")
            .setContentText(details.replace("\n", "；"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(details + more))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.e(TAG, "失败提醒通知发送失败", it) }
    }
}
