package com.alarm.qinghao.wakeup.alarm

import android.util.Log
import com.alarm.qinghao.wakeup.data.AlarmEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 响铃中状态的进程内共享源。
 * 服务写入，全屏响铃页 / 主界面横幅监听：状态变为 null 即结束。
 */
object RingingStateHolder {

    data class Ringing(
        val alarm: AlarmEntity,
        val snoozeCount: Int
    )

    private val _current = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = _current.asStateFlow()

    /** 锁屏「静音」：铃声与震动已停止，但闹钟仍处于响铃状态，可继续关闭。 */
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    fun begin(alarm: AlarmEntity, snoozeCount: Int) {
        Log.i("RingingState", "开始响铃 alarmId=${alarm.id} snoozeCount=$snoozeCount")
        _muted.value = false
        _current.value = Ringing(alarm, snoozeCount)
    }

    fun setMuted(value: Boolean) {
        Log.i("RingingState", "静音状态=$value")
        _muted.value = value
    }

    fun clear() {
        _current.value = null
        _muted.value = false
    }

    fun currentOrNull(): Ringing? = _current.value
}
