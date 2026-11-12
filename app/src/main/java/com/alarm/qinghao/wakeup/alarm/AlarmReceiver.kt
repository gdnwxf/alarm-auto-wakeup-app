package com.alarm.qinghao.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.alarm.qinghao.wakeup.AlarmApplication
import com.alarm.qinghao.wakeup.data.AlarmEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟到点入口：
 * 1. 过滤重复触发（非精确闹钟可能在同一分钟内回调多次）；
 * 2. 校验闹钟是否仍然启用；
 * 3. 为重复闹钟重排下一次、为一次性闹钟关闭开关；
 * 4. 启动前台服务响铃 + 拉起全屏响铃页。
 *
 * 使用 goAsync + 协程避免 DB 读写阻塞主线程（主线程阻塞超过 10 秒会 ANR）。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)

        if (isDuplicate(alarmId, snoozeCount)) {
            Log.i(TAG, "忽略重复触发 alarmId=$alarmId snooze=$snoozeCount")
            return
        }

        val app = context.applicationContext as? AlarmApplication ?: run {
            Log.w(TAG, "Application 类型不符，忽略本次闹钟")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, context, alarmId, snoozeCount)
            } catch (t: Throwable) {
                Log.e(TAG, "处理闹钟失败 alarmId=$alarmId", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private suspend fun handle(
        app: AlarmApplication,
        context: Context,
        alarmId: Long,
        snoozeCount: Int
    ) {
        val alarm = app.repository.get(alarmId) ?: run {
            Log.w(TAG, "闹钟不存在 alarmId=$alarmId")
            return
        }

        if (snoozeCount == 0) {
            if (!alarm.enabled) {
                Log.i(TAG, "闹钟已关闭，跳过 alarmId=$alarmId")
                return
            }
            // 一次性闹钟响过后自动关闭；重复闹钟重排下一次
            val updated: AlarmEntity = if (alarm.repeatMask == 0) {
                alarm.copy(enabled = false)
            } else {
                alarm
            }
            app.repository.upsert(updated)
            app.scheduler.schedule(updated)
        }

        AlarmService.start(context, alarmId, snoozeCount)
        AlarmRingActivity.start(context, alarmId, snoozeCount)
    }

    private fun isDuplicate(alarmId: Long, snoozeCount: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastTrigger
        if (last != null &&
            last.alarmId == alarmId &&
            last.snoozeCount == snoozeCount &&
            now - last.atMillis < DUPLICATE_WINDOW_MS
        ) {
            return true
        }
        lastTrigger = LastTrigger(alarmId, snoozeCount, now)
        return false
    }

    private data class LastTrigger(val alarmId: Long, val snoozeCount: Int, val atMillis: Long)

    companion object {
        private const val TAG = "AlarmReceiver"

        /** 同一闹钟、同一贪睡次数在该窗口内的重复回调视为抖动。 */
        private const val DUPLICATE_WINDOW_MS = 10_000L

        @Volatile
        private var lastTrigger: LastTrigger? = null
    }
}
