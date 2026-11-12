package com.alarm.qinghao.wakeup.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alarm.qinghao.wakeup.MainActivity
import com.alarm.qinghao.wakeup.data.AlarmEntity
import com.alarm.qinghao.wakeup.data.RepeatDays

/**
 * 负责任闹钟的系统注册：到点触发、贪睡触发、取消、开机重建。
 * 使用 setAlarmClock，可在锁屏和息屏状态下精准唤醒。
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(alarm: AlarmEntity) {
        cancelMain(alarm.id)
        if (!alarm.enabled) return
        val manager = alarmManager ?: return
        val triggerAt = RepeatDays.nextTriggerAt(alarm.hour, alarm.minute, alarm.repeatMask) ?: return
        val operation = mainPendingIntent(alarm.id)

        if (canScheduleExact()) {
            try {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent(alarm.id)), operation)
            } catch (e: SecurityException) {
                Log.w(TAG, "setAlarmClock 被拒绝，降级为非精确闹钟", e)
                safeSet(manager, triggerAt, operation)
            }
        } else {
            Log.w(TAG, "缺少精确闹钟权限，降级为非精确闹钟")
            safeSet(manager, triggerAt, operation)
        }
    }

    fun scheduleAll(alarms: List<AlarmEntity>) {
        alarms.forEach { schedule(it) }
    }

    fun cancel(alarmId: Long) {
        cancelMain(alarmId)
        cancelSnooze(alarmId)
        // 同时取消系统闹钟详情页的入口 PendingIntent，避免残留
        alarmManager?.let { manager ->
            runCatching { manager.cancel(showIntent(alarmId)) }
        }
    }

    /**
     * 贪睡：使用独立 requestCode，不覆盖常规的下一次闹钟。
     * @return false 表示已超过最大贪睡次数或系统拒绝
     */
    fun scheduleSnooze(alarm: AlarmEntity, nextSnoozeCount: Int): Boolean {
        if (alarm.snoozeMaxTimes <= 0) return false
        if (nextSnoozeCount > alarm.snoozeMaxTimes) return false

        val manager = alarmManager ?: return false
        val triggerAt = System.currentTimeMillis() + alarm.snoozeMinutes.coerceAtLeast(1) * 60_000L
        val operation = snoozePendingIntent(alarm.id, nextSnoozeCount)

        return runCatching {
            if (canScheduleExact()) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent(alarm.id)), operation)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            true
        }.getOrElse {
            Log.w(TAG, "贪睡闹钟注册失败", it)
            false
        }
    }

    private fun safeSet(manager: AlarmManager, triggerAt: Long, operation: PendingIntent) {
        runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation) }
            .onFailure { Log.w(TAG, "精确闹钟注册失败", it) }
    }

    private fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    private fun cancelMain(alarmId: Long) {
        val manager = alarmManager ?: return
        runCatching { manager.cancel(mainPendingIntent(alarmId)) }
    }

    private fun cancelSnooze(alarmId: Long) {
        val manager = alarmManager ?: return
        runCatching { manager.cancel(snoozePendingIntent(alarmId, 0)) }
    }

    private fun mainPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RING
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE_COUNT, 0)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, snooze = false),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun snoozePendingIntent(alarmId: Long, snoozeCount: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RING
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, snooze = true),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(alarmId, snooze = false),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(alarmId: Long, snooze: Boolean): Int =
        ((alarmId % 100_000L).toInt() * 10) + if (snooze) 1 else 0

    companion object {
        private const val TAG = "AlarmScheduler"

        const val ACTION_RING = "com.alarm.qinghao.wakeup.action.RING"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
    }
}
