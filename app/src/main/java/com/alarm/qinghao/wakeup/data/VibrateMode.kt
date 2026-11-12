package com.alarm.qinghao.wakeup.data

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** 震动模式，value 持久化到数据库。 */
enum class VibrateMode(val value: Int, val label: String) {
    OFF(0, "关闭"),
    SHORT(1, "短震动"),
    LONG(2, "长震动"),
    HEARTBEAT(3, "心跳"),
    RAMP(4, "渐强");

    companion object {
        fun fromValue(value: Int): VibrateMode = entries.firstOrNull { it.value == value } ?: SHORT
    }
}

/**
 * 震动波形。
 *
 * 关键约束：每个波形都是**有限时长且不循环**（repeat = -1），由服务按波形时长重新投递。
 * 这样即使进程被系统杀死、onDestroy 未执行，震动也最多再持续一个波形周期，不会无限震动。
 */
object VibratePatterns {

    private val TIMINGS: Map<VibrateMode, LongArray> = mapOf(
        VibrateMode.SHORT to longArrayOf(0, 350, 650),
        VibrateMode.LONG to longArrayOf(0, 1000, 700),
        VibrateMode.HEARTBEAT to longArrayOf(0, 200, 120, 200, 900),
        VibrateMode.RAMP to longArrayOf(0, 120, 180, 220, 180, 340, 180, 500, 900)
    )

    private const val LEGACY_DURATION_MS = 3_000L

    /** 单个波形周期时长，服务据此重新投递。 */
    fun patternDurationOf(mode: VibrateMode): Long {
        val timings = TIMINGS[mode] ?: return LEGACY_DURATION_MS
        return timings.sum().coerceAtLeast(LEGACY_DURATION_MS)
    }

    fun effectOf(mode: VibrateMode): VibrationEffect? {
        val timings = TIMINGS[mode] ?: return null
        // repeat = -1：只播放一遍，绝不无限循环
        return VibrationEffect.createWaveform(timings, -1)
    }

    @Suppress("DEPRECATION")
    fun start(vibrator: Vibrator, mode: VibrateMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = effectOf(mode) ?: return
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(1_000L)
        }
    }

    fun stop(vibrator: Vibrator) {
        runCatching { vibrator.cancel() }
    }
}
