package com.appalarm.core

import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Weekdays
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 计算闹钟的下一次触发时刻。
 *
 * 这里刻意做成纯函数（不碰 AlarmManager、不碰系统时钟），因为「下周几几点响」
 * 这类跨天、跨夏令时的逻辑是最容易出错的地方，放在这里就能用普通 JVM 单元测试
 * 覆盖到边界情况。
 */
object NextTriggerCalculator {

    /**
     * 返回 [alarm] 在 [from] 之后（严格晚于）的下一次触发时刻。
     *
     * 返回 null 只可能发生在 [Alarm.repeatDaysMask] 是非法值的时候，
     * 调用方应当按「这个闹钟不会再响」处理。
     */
    fun nextTrigger(alarm: Alarm, from: ZonedDateTime): ZonedDateTime? {
        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))

        if (alarm.repeatDaysMask == Weekdays.NONE) {
            // 今天这个点还没到就今天，否则明天。
            val today = from.toLocalDate().atTime(time).atZone(from.zone)
            return if (today.isAfter(from)) {
                today
            } else {
                from.toLocalDate().plusDays(1).atTime(time).atZone(from.zone)
            }
        }

        // 往后看 8 天，必定覆盖到「同一星期几的下一周」。
        for (offset in 0..7) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            if (!Weekdays.contains(alarm.repeatDaysMask, date.dayOfWeek)) continue
            // atZone 会把夏令时跳变里不存在的本地时间顺延，不会抛异常。
            val candidate = date.atTime(time).atZone(from.zone)
            if (candidate.isAfter(from)) return candidate
        }
        return null
    }

    /** 下一次响铃的毫秒时间戳，供 AlarmManager 使用。 */
    fun nextTriggerMillis(alarm: Alarm, from: ZonedDateTime): Long? =
        nextTrigger(alarm, from)?.toInstant()?.toEpochMilli()
}
