package com.alarm.qinghao.wakeup.data

import java.util.Calendar
import java.util.Locale

/**
 * 重复规则：以位掩码存储，"周一"为 bit0，"周日"为 bit6。
 * repeatMask == 0 表示仅响一次。
 */
object RepeatDays {

    const val ALL_MASK = 0b111_1111
    const val WORKDAY_MASK = 0b001_1111
    const val WEEKEND_MASK = 0b110_0000

    private val SHORT_NAMES = arrayOf("一", "二", "三", "四", "五", "六", "日")

    /** 周一 -> 0 ... 周日 -> 6 */
    fun indexOf(calendarDayOfWeek: Int): Int = (calendarDayOfWeek + 5) % 7

    fun bitOf(index: Int): Int = 1 shl index

    fun masksOf(indices: Set<Int>): Int = indices.fold(0) { acc, i -> acc or bitOf(i) }

    fun indicesOf(mask: Int): Set<Int> = (0..6).filter { (mask shr it) and 1 == 1 }.toSet()

    fun shortName(index: Int): String =
        if (index in 0..6) SHORT_NAMES[index] else "?"

    /** 列表页展示用的摘要文案。 */
    fun describe(mask: Int): String = when {
        mask == 0 -> "仅一次"
        mask and ALL_MASK == ALL_MASK -> "每天"
        mask == WORKDAY_MASK -> "工作日"
        mask == WEEKEND_MASK -> "周末"
        else -> indicesOf(mask).sorted().joinToString("、") { "周${shortName(it)}" }
    }

    /**
     * 计算下一次触发时间（epoch millis）。
     * @param from 基准时间，默认当前时间
     * @return null 表示该规则下不再有触发时间
     */
    fun nextTriggerAt(hour: Int, minute: Int, repeatMask: Int, from: Long = System.currentTimeMillis()): Long? {
        val hourOfDay = hour.coerceIn(0, 23)
        val minuteOfHour = minute.coerceIn(0, 59)

        if (repeatMask == 0) {
            val target = Calendar.getInstance().apply {
                timeInMillis = from
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minuteOfHour)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= from) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        for (offset in 0..7) {
            val probe = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minuteOfHour)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val matched = (repeatMask shr indexOf(probe.get(Calendar.DAY_OF_WEEK))) and 1 == 1
            if (matched && probe.timeInMillis > from) {
                return probe.timeInMillis
            }
        }
        return null
    }

    /** 例如 "明天 07:30"、"周二 07:30"。 */
    fun describeTrigger(triggerAt: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = triggerAt }
        val time = String.format(Locale.getDefault(), "%02d:%02d",
            target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))

        val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "今天 $time"

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        if (isTomorrow) return "明天 $time"

        val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return "${names[target.get(Calendar.DAY_OF_WEEK) - 1]} $time"
    }
}
