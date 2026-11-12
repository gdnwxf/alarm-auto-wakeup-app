package com.alarm.qinghao.wakeup.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alarm.qinghao.wakeup.AlarmApplication
import com.alarm.qinghao.wakeup.ui.RingScreen
import com.alarm.qinghao.wakeup.ui.theme.AlarmQinghaoTheme
import com.alarm.qinghao.wakeup.util.WakeApps
import com.alarm.qinghao.wakeup.util.WakeAppsNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 全屏响铃页：锁屏展示、点亮屏幕，提供贪睡/关闭，并在响铃后唤醒目标应用。
 */
class AlarmRingActivity : ComponentActivity() {

    private var alarmId: Long = -1L
    private var snoozeCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        readIntent(intent)

        setContent {
            AlarmQinghaoTheme(darkTheme = true) {
                RingRoute()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    override fun onDestroy() {
        // 页面结束时取消尚未执行的唤醒任务，避免已关闭闹钟又弹出应用
        WakeApps.cancelPending()
        super.onDestroy()
    }

    private fun readIntent(intent: Intent?) {
        alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        snoozeCount = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0) ?: 0
    }

    private fun applyLockScreenFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    @Composable
    private fun RingRoute() {
        val app = application as AlarmApplication
        val ringing by RingingStateHolder.current.collectAsStateWithLifecycle()
        val muted by RingingStateHolder.muted.collectAsStateWithLifecycle()
        var everRinging by remember { mutableStateOf(false) }
        var locked by remember { mutableStateOf(isKeyguardLocked()) }

        // 锁屏 / 解锁状态变化时刷新按钮组合
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            locked = isKeyguardLocked()
        }

        // 响铃状态被清除时结束页面
        LaunchedEffect(ringing) {
            if (ringing != null) {
                everRinging = true
            } else if (everRinging) {
                finishAndRemoveTask()
            }
        }

        // 兜底：服务尚未写入状态时按 id 自行加载
        LaunchedEffect(Unit) {
            if (RingingStateHolder.current.value == null && alarmId > 0) {
                val loaded = withContext(Dispatchers.IO) { app.repository.get(alarmId) }
                if (loaded != null) {
                    RingingStateHolder.begin(loaded, snoozeCount)
                }
            }
        }

        // 兜底：长时间拿不到数据则退出，避免白屏
        LaunchedEffect(Unit) {
            delay(FALLBACK_TIMEOUT_MS)
            if (RingingStateHolder.current.value == null) {
                finishAndRemoveTask()
            }
        }

        val current = ringing
        if (current == null) return

        RingScreen(
            alarm = current.alarm,
            snoozeCount = current.snoozeCount,
            isLocked = locked,
            isMuted = muted,
            onMute = {
                AlarmService.sendAction(this@AlarmRingActivity, AlarmService.ACTION_MUTE)
            },
            onSnooze = { snoozeAlarm(current) },
            onDismiss = { dismissAlarm() }
        )

        // 响铃稳定后再唤起目标应用，避免与全屏页展示竞争
        LaunchedEffect(current.alarm.id, current.snoozeCount) {
            delay(WAKE_APPS_DELAY_MS)
            WakeApps.launchAll(this@AlarmRingActivity, current.alarm) { results ->
                results.forEach {
                    Log.i(TAG, "唤起 ${it.packageName} 成功=${it.success} 原因=${it.reason}")
                }
                if (results.any { !it.success }) {
                    // 用 applicationContext，避免页面已销毁
                    WakeAppsNotifier.notifyFailures(applicationContext, results)
                }
            }
        }
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * 贪睡由页面直接调度：即使响铃服务被系统回收，贪睡依然生效。
     * 服务侧 ACTION_STOP 只负责停止声音（snooze = false），不会重复排程。
     */
    private fun snoozeAlarm(ringing: RingingStateHolder.Ringing) {
        val app = application as AlarmApplication
        val scheduled = app.scheduler.scheduleSnooze(ringing.alarm, ringing.snoozeCount + 1)
        if (!scheduled) {
            Toast.makeText(this, "已达最大贪睡次数，本次关闭", Toast.LENGTH_SHORT).show()
        }
        stopRingingService()
        RingingStateHolder.clear()
        finishAndRemoveTask()
    }

    private fun dismissAlarm() {
        stopRingingService()
        RingingStateHolder.clear()
        finishAndRemoveTask()
    }

    /**
     * 双保险停止：
     * 1. ACTION_STOP 让服务走完整清理（移除通知、清理贪睡状态）；
     * 2. stopService 无条件触发 onDestroy，确保播放器/震动/唤醒锁一定释放。
     * 服务被系统回收后这里也不会卡死。
     */
    private fun stopRingingService() {
        AlarmService.sendAction(this, AlarmService.ACTION_STOP)
        runCatching { stopService(Intent(this, AlarmService::class.java)) }
            .onFailure { Log.w(TAG, "stopService 失败", it) }
    }

    companion object {
        private const val TAG = "AlarmRingActivity"
        private const val FALLBACK_TIMEOUT_MS = 10_000L
        private const val WAKE_APPS_DELAY_MS = 1_200L

        fun intent(context: Context, alarmId: Long, snoozeCount: Int): Intent =
            Intent(context, AlarmRingActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            }

        fun start(context: Context, alarmId: Long, snoozeCount: Int) {
            try {
                context.startActivity(intent(context, alarmId, snoozeCount))
            } catch (e: Exception) {
                Log.e(TAG, "拉起全屏响铃页失败", e)
            }
        }
    }
}
