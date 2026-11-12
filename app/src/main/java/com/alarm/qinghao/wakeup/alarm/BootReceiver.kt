package com.alarm.qinghao.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alarm.qinghao.wakeup.AlarmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机、应用升级、系统时间/时区变化后重建全部启用的闹钟。
 * 使用 goAsync + 协程，避免在主线程做数据库读写。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val app = context.applicationContext as? AlarmApplication ?: return
        Log.i(TAG, "重建闹钟，触发事件=$action")

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val alarms = app.repository.getEnabled()
                alarms.forEach { app.scheduler.schedule(it) }
                Log.i(TAG, "重建完成，共 ${alarms.size} 个闹钟")
            } catch (t: Throwable) {
                Log.e(TAG, "重建闹钟失败", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
