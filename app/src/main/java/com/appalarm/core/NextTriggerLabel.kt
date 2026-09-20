package com.appalarm.core

import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Weekdays
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** 把「下一次什么时候响」写成人话，用在主页列表上。 */
object NextTriggerLabel {

    fun describe(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): String {
        if (!alarm.enabled) return "已关闭"

        val next = NextTriggerCalculator.nextTrigger(alarm, now) ?: return "不会再响"
        val daysAway = ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())

        val dayPart = when {
            daysAway <= 0L -> "今天"
            daysAway == 1L -> "明天"
            daysAway == 2L -> "后天"
            // 一周以内直接用星期几更直观，一周以上只能给日期。
            daysAway < 7L -> "周" + Weekdays.shortName(next.dayOfWeek)
            else -> "%d月%d日".format(next.monthValue, next.dayOfMonth)
        }
        return "$dayPart %02d:%02d".format(next.hour, next.minute)
    }
}
