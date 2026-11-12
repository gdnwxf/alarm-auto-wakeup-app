package com.alarm.qinghao.wakeup.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alarm.qinghao.wakeup.AlarmApplication
import com.alarm.qinghao.wakeup.R
import com.alarm.qinghao.wakeup.data.AlarmEntity
import com.alarm.qinghao.wakeup.data.VibrateMode
import com.alarm.qinghao.wakeup.data.VibratePatterns
import com.alarm.qinghao.wakeup.util.AlarmPlayer
import com.alarm.qinghao.wakeup.util.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 响铃前台服务：持有铃声播放器与震动，保证全屏响铃页被销毁时仍能停止声音。
 * 通知栏提供"停止/贪睡"兜底操作（目标应用被唤起后仍可操作）。
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ringingJob: Job? = null
    private var player: AlarmPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarm: AlarmEntity? = null
    private var snoozeCount: Int = 0

    /** 正在结束响铃：阻止仍在途的加载协程重新开播。 */
    private var stopping = false

    private var screenOffRegistered = false

    /** 响铃期间监听熄屏：用户按电源键关屏即静音（电源键事件不向应用分发）。 */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.i(TAG, "检测到熄屏（电源键），静音闹钟")
                mutePlayback()
            }
        }
    }

    /** 周期性重新投递有限时长震动波形，进程被杀后自然停止。 */
    private val vibrateLoop = object : Runnable {
        override fun run() {
            val target = alarm ?: return
            val current = vibrator ?: return
            val mode = VibrateMode.fromValue(target.vibrateMode)
            if (mode == VibrateMode.OFF) return
            runCatching { VibratePatterns.start(current, mode) }
                .onFailure { Log.w(TAG, "震动投递失败", it) }
            handler.postDelayed(this, VibratePatterns.patternDurationOf(mode))
        }
    }

    private val timeoutRunnable = Runnable {
        Log.i(TAG, "响铃超时，自动关闭")
        finishRinging(snooze = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val safeIntent = intent
        if (safeIntent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (safeIntent.action) {
            ACTION_STOP -> finishRinging(snooze = false)
            ACTION_SNOOZE -> finishRinging(snooze = true)
            ACTION_MUTE -> mutePlayback()
            else -> {
                val alarmId = safeIntent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                if (alarmId <= 0L) {
                    Log.w(TAG, "缺少 alarmId，停止响铃服务")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val count = safeIntent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
                startRinging(alarmId, count)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayback()
        RingingStateHolder.clear()
        ringingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRinging(alarmId: Long, count: Int) {
        stopping = false
        // 先进入前台，满足 startForegroundService 的 5 秒约束；随后用真实内容更新通知
        if (!promoteToForeground(buildPlaceholderNotification())) return

        // 同一闹钟、同一贪睡次数的重复回调（如降级为非精确闹钟时的多次触发）不重复播放
        val ringing = alarm
        if (ringing != null && ringing.id == alarmId && snoozeCount == count) {
            if (!promoteToForeground(buildNotification(ringing, snoozeCount))) {
                Log.w(TAG, "刷新响铃通知失败，服务已停止")
            }
            return
        }

        // 数据库读取放到后台线程，避免阻塞主线程导致 ANR
        ringingJob?.cancel()
        ringingJob = serviceScope.launch {
            val app = application as AlarmApplication
            val cached = RingingStateHolder.currentOrNull()
                ?.takeIf { it.alarm.id == alarmId }
                ?.alarm
            val target = cached
                ?: withContext(Dispatchers.IO) { app.repository.get(alarmId) }
            if (target == null) {
                Log.w(TAG, "闹钟不存在，停止响铃 alarmId=$alarmId")
                stopSelf()
                return@launch
            }
            showRinging(target, count)
        }
    }

    private fun showRinging(target: AlarmEntity, count: Int) {
        if (stopping) {
            Log.i(TAG, "响铃已结束，忽略迟到的加载结果 alarmId=${target.id}")
            return
        }
        if (alarm?.id == target.id) {
            if (!promoteToForeground(buildNotification(target, count))) {
                Log.w(TAG, "刷新响铃通知失败，服务已停止")
            }
            return
        }
        // 切换闹钟前先释放旧的播放器/震动/唤醒锁，避免资源叠加
        releasePlayback()

        alarm = target
        snoozeCount = count
        RingingStateHolder.begin(target, count)

        if (!promoteToForeground(buildNotification(target, count))) return
        startPlayback(target)
        scheduleAutoStop(target)

        // 服务已在前台，此时拉起响铃页成功率更高；页面已存在时只会回调 onNewIntent
        AlarmRingActivity.start(this, target.id, count)
    }

    private fun promoteToForeground(notification: Notification): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            // 未能在 5 秒内进入前台时，Android 12+ 会抛
            // ForegroundServiceDidNotStartInTimeException 杀掉进程，必须主动停止服务
            Log.e(TAG, "前台服务启动失败，停止服务", e)
            releasePlayback()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            false
        }
    }

    private fun buildPlaceholderNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("闹钟响铃中")
            .setContentText("正在准备铃声…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    private fun startPlayback(target: AlarmEntity) {
        player = AlarmPlayer(this).also { it.start(target.soundUri, target.fadeInSeconds) }

        val mode = VibrateMode.fromValue(target.vibrateMode)
        if (mode != VibrateMode.OFF) {
            val deviceVibrator = getSystemService(Vibrator::class.java)
            vibrator = deviceVibrator
            if (deviceVibrator != null && deviceVibrator.hasVibrator()) {
                runCatching { VibratePatterns.start(deviceVibrator, mode) }
                    .onFailure { Log.w(TAG, "震动启动失败", it) }
                // 波形有限时长，按时长重新投递以保持连续震动
                handler.postDelayed(vibrateLoop, VibratePatterns.patternDurationOf(mode))
            }
        }

        val powerManager = getSystemService(PowerManager::class.java)
        // 唤醒锁超时与响铃时长对齐，避免响铃还未结束锁已过期
        val wakeLockTimeout = (target.ringTimeoutMinutes.coerceIn(1, 60) + 1) * 60_000L
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmWakeup:ringing")
            ?.apply { runCatching { acquire(wakeLockTimeout) } }

        registerScreenOffReceiver()
    }

    private fun registerScreenOffReceiver() {
        if (screenOffRegistered) return
        screenOffRegistered = runCatching {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrElse {
            Log.w(TAG, "注册熄屏监听失败", it)
            false
        }
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenOffRegistered) return
        screenOffRegistered = false
        runCatching { unregisterReceiver(screenOffReceiver) }
    }

    private fun scheduleAutoStop(target: AlarmEntity) {
        val timeoutMinutes = target.ringTimeoutMinutes.coerceIn(1, 60)
        handler.postDelayed(timeoutRunnable, timeoutMinutes * 60_000L)
    }

    /** 锁屏「静音」：停止铃声、震动与唤醒锁，但保留响铃状态与通知，用户仍可关闭。 */
    private fun mutePlayback() {
        val current = alarm
        if (current == null) {
            Log.i(TAG, "无响铃中的闹钟，静音请求直接结束服务")
            stopSelf()
            return
        }
        if (RingingStateHolder.muted.value) return

        handler.removeCallbacks(vibrateLoop)
        handler.removeCallbacks(timeoutRunnable)
        player?.stop()
        player = null
        vibrator?.let { runCatching { VibratePatterns.stop(it) } }
        vibrator = null
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null

        RingingStateHolder.setMuted(true)
        unregisterScreenOffReceiver()
        if (!promoteToForeground(buildNotification(current, snoozeCount))) return
        // 依旧保留超时自动结束，避免通知永久驻留
        scheduleAutoStop(current)
    }

    private fun finishRinging(snooze: Boolean) {
        stopping = true
        ringingJob?.cancel()
        ringingJob = null
        handler.removeCallbacks(timeoutRunnable)

        val current = alarm
        if (snooze && current != null) {
            val app = application as AlarmApplication
            val scheduled = app.scheduler.scheduleSnooze(current, snoozeCount + 1)
            Log.i(TAG, "贪睡请求：第 ${snoozeCount + 1} 次，结果=$scheduled")
            if (!scheduled) {
                Log.i(TAG, "已达最大贪睡次数，本次直接关闭")
            }
        }

        releasePlayback()
        RingingStateHolder.clear()
        alarm = null
        snoozeCount = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayback() {
        unregisterScreenOffReceiver()
        handler.removeCallbacks(vibrateLoop)
        player?.stop()
        player = null
        vibrator?.let { runCatching { VibratePatterns.stop(it) } }
        vibrator = null
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
    }

    private fun buildNotification(target: AlarmEntity, count: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            AlarmRingActivity.intent(this, target.id, count),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            REQ_FULL_SCREEN,
            AlarmRingActivity.intent(this, target.id, count),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQ_STOP,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getService(
            this,
            REQ_SNOOZE,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = String.format("%02d:%02d", target.hour, target.minute)
        val title = if (target.label.isBlank()) timeText else "$timeText  ${target.label}"
        val subText = when {
            RingingStateHolder.muted.value -> "已静音 · 铃声与震动已停止"
            count > 0 -> "贪睡中（第 $count 次）"
            else -> "闹钟响铃中"
        }

        return NotificationCompat.Builder(this, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(subText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(0L)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .apply {
                // 设为「不贪睡」时不再展示贪睡按钮，避免点了无效
                if (target.snoozeMaxTimes > 0) addAction(0, "贪睡", snoozeIntent)
                addAction(0, "停止", stopIntent)
            }
            .build()
    }

    companion object {
        private const val TAG = "AlarmService"

        const val ACTION_START = "com.alarm.qinghao.wakeup.action.SERVICE_START"
        const val ACTION_STOP = "com.alarm.qinghao.wakeup.action.SERVICE_STOP"
        const val ACTION_SNOOZE = "com.alarm.qinghao.wakeup.action.SERVICE_SNOOZE"
        const val ACTION_MUTE = "com.alarm.qinghao.wakeup.action.SERVICE_MUTE"

        private const val NOTIFICATION_ID = 1001
        private const val REQ_CONTENT = 2001
        private const val REQ_FULL_SCREEN = 2002
        private const val REQ_STOP = 2003
        private const val REQ_SNOOZE = 2004

        fun start(context: Context, alarmId: Long, snoozeCount: Int) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "启动响铃服务失败", e)
            }
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, AlarmService::class.java).setAction(action)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "响铃服务操作失败 action=$action", e)
                context.stopService(Intent(context, AlarmService::class.java))
            }
        }
    }
}
