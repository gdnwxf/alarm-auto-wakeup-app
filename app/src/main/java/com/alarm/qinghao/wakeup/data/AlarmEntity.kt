package com.alarm.qinghao.wakeup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int = 7,
    val minute: Int = 30,
    val label: String = "",
    val enabled: Boolean = true,
    /** 位掩码：bit0=周一 ... bit6=周日；0 表示仅一次 */
    val repeatMask: Int = 0,
    /** 铃声 URI；null 表示系统默认闹钟铃声，[SILENT_URI] 表示静音 */
    val soundUri: String? = null,
    val soundName: String = DEFAULT_SOUND_NAME,
    val vibrateMode: Int = VibrateMode.SHORT.value,
    /** 音量渐强秒数，0 表示不做渐强 */
    val fadeInSeconds: Int = 0,
    /** 响铃超时分钟数，到点自动停止 */
    val ringTimeoutMinutes: Int = 10,
    val snoozeMinutes: Int = 10,
    val snoozeMaxTimes: Int = 3,
    /** 响铃时需要唤醒的目标应用包名，按顺序执行 */
    val wakeAppPackages: List<String> = emptyList(),
    /** 依次启动目标应用的间隔秒数（同时启动时为 0） */
    val wakeAppDelaySeconds: Int = 3,
    /** true = 一次性同时启动所有目标应用 */
    val wakeAppParallel: Boolean = false
) {
    companion object {
        const val SILENT_URI = "silent"
        const val DEFAULT_SOUND_NAME = "默认闹钟铃声"

        fun isSilent(soundUri: String?): Boolean = soundUri == SILENT_URI
    }
}
