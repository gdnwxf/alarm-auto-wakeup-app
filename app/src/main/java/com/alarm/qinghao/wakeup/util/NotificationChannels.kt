package com.alarm.qinghao.wakeup.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    /** 响铃前台通知（静音，声音由 AlarmPlayer 播放）。 */
    const val ALARM = "alarm_ring"

    /** 唤醒应用失败提醒。 */
    const val WAKE_FAILED = "wake_app_failed"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createIfAbsent(manager, alarmChannel())
        createIfAbsent(manager, wakeFailedChannel())
    }

    private fun createIfAbsent(manager: NotificationManager, channel: NotificationChannel) {
        if (manager.getNotificationChannel(channel.id) == null) {
            manager.createNotificationChannel(channel)
        }
    }

    private fun alarmChannel(): NotificationChannel =
        NotificationChannel(
            ALARM,
            "闹钟响铃",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "闹钟响铃时的前台通知，可在这里停止或贪睡"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

    private fun wakeFailedChannel(): NotificationChannel =
        NotificationChannel(
            WAKE_FAILED,
            "唤醒应用失败提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "闹钟未能打开目标应用时，说明具体原因"
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
}
