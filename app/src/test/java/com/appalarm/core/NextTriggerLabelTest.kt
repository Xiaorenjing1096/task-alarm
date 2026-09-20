package com.appalarm.core

import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Weekdays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerLabelTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun at(day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, shanghai)

    private fun alarm(hour: Int, minute: Int, mask: Int, enabled: Boolean = true) =
        Alarm(id = "a", hour = hour, minute = minute, repeatDaysMask = mask, enabled = enabled)

    // 2026-03-10 是周二，03-11 周三，03-12 周四，03-16 下周一。

    @Test
    fun `a disabled alarm says so`() {
        assertEquals(
            "已关闭",
            NextTriggerLabel.describe(alarm(7, 0, Weekdays.ALL, enabled = false), at(10, 6, 0)),
        )
    }

    @Test
    fun `an alarm later today is labelled today`() {
        assertEquals(
            "今天 07:00",
            NextTriggerLabel.describe(alarm(7, 0, Weekdays.ALL), at(10, 6, 0)),
        )
    }

    @Test
    fun `an alarm tomorrow is labelled tomorrow`() {
        assertEquals(
            "明天 07:00",
            NextTriggerLabel.describe(alarm(7, 0, Weekdays.ALL), at(10, 8, 0)),
        )
    }

    @Test
    fun `an alarm the day after tomorrow is labelled accordingly`() {
        // 周二 06:00 看「只勾周四」的闹钟：下一次是 03-12 周四，正好两天后。
        assertEquals(
            "后天 09:00",
            NextTriggerLabel.describe(
                alarm(9, 0, Weekdays.bit(DayOfWeek.THURSDAY)),
                at(10, 6, 0),
            ),
        )
    }

    @Test
    fun `further out within the week uses the weekday name`() {
        // 周二 06:00 看「只勾周日」的闹钟：下一次是 03-15 周日，还有 5 天。
        assertEquals(
            "周日 09:30",
            NextTriggerLabel.describe(
                alarm(9, 30, Weekdays.bit(DayOfWeek.SUNDAY)),
                at(10, 6, 0),
            ),
        )
    }

    @Test
    fun `a one-shot alarm already past its time shows tomorrow`() {
        assertEquals(
            "明天 06:00",
            NextTriggerLabel.describe(alarm(6, 0, Weekdays.NONE), at(10, 23, 30)),
        )
    }

    @Test
    fun `minutes are zero padded`() {
        assertEquals(
            "今天 07:05",
            NextTriggerLabel.describe(alarm(7, 5, Weekdays.ALL), at(10, 6, 0)),
        )
    }

    @Test
    fun `a weekdays alarm from saturday points at monday`() {
        // 2026-03-14 是周六。周一 03-16 只隔两天，所以命中「后天」而不是「周一」——
        // 这是有意的：能用「后天」说清楚时，比让人自己算周几更直观。
        assertEquals(
            "后天 06:45",
            NextTriggerLabel.describe(alarm(6, 45, Weekdays.WEEKDAYS), at(14, 10, 0)),
        )
    }

    @Test
    fun `labels three to six days out by weekday name`() {
        // 周六 10:00 看「只勾周三」的闹钟：下一次是 03-18 周三，还有 4 天。
        assertEquals(
            "周三 09:00",
            NextTriggerLabel.describe(
                alarm(9, 0, Weekdays.bit(DayOfWeek.WEDNESDAY)),
                at(14, 10, 0),
            ),
        )
    }
}
