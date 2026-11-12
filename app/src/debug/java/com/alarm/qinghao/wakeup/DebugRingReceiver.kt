package com.alarm.qinghao.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alarm.qinghao.wakeup.alarm.AlarmReceiver
import com.alarm.qinghao.wakeup.alarm.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 仅 debug 构建可用的测试入口：
 * adb shell am broadcast -n com.alarm.qinghao.wakeup/.DebugRingReceiver -a com.alarm.qinghao.wakeup.debug.RING
 *
 * 不直接启动服务，而是用 AlarmManager 安排一个几秒后的精确闹钟，
 * 这样触发路径与真实闹钟一致（包含前台服务/后台启动的官方豁免），
 * 才能在真机上验证亮屏/锁屏下的实际行为。
 */
class DebugRingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AlarmApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                var alarmId = intent.getLongExtra("alarm_id", -1L)
                if (alarmId <= 0L) {
                    // 未指定时自动取第一个启用的闹钟，方便真机快速验证
                    alarmId = app.repository.getEnabled().firstOrNull()?.id ?: -1L
                }
                if (alarmId <= 0L) {
                    Log.w(TAG, "没有可触发的闹钟")
                    return@launch
                }

                val delayMs = intent.getLongExtra("delay_ms", 5_000L).coerceAtLeast(1_000L)
                val ringIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmScheduler.ACTION_RING
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    999_999,
                    ringIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = context.getSystemService(AlarmManager::class.java)
                val triggerAt = System.currentTimeMillis() + delayMs

                runCatching {
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, null), pendingIntent)
                }.onSuccess {
                    Log.i(TAG, "已安排 ${delayMs}ms 后由系统闹钟触发 alarmId=$alarmId")
                }.onFailure {
                    Log.e(TAG, "安排测试闹钟失败，尝试 setExactAndAllowWhileIdle", it)
                    runCatching {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    }.onFailure { Log.e(TAG, "安排测试闹钟彻底失败", it) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "测试入口执行失败", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        private const val TAG = "DebugRingReceiver"
    }
}
