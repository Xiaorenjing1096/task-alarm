package com.appalarm.core

import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Weekdays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerCalculatorTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, shanghai)

    private fun alarm(hour: Int, minute: Int, mask: Int) =
        Alarm(id = "test", hour = hour, minute = minute, repeatDaysMask = mask)

    @Test
    fun `one-shot alarm later today fires today`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(7, 0, Weekdays.NONE),
            at(2026, 3, 10, 6, 0),
        )
        assertEquals(at(2026, 3, 10, 7, 0), next)
    }

    @Test
    fun `one-shot alarm whose time already passed fires tomorrow`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(7, 0, Weekdays.NONE),
            at(2026, 3, 10, 8, 0),
        )
        assertEquals(at(2026, 3, 11, 7, 0), next)
    }

    @Test
    fun `exactly at the alarm time rolls over to the next day`() {
        // 严格晚于 from：正好等于闹钟时刻时不能返回同一时刻，否则会无限重排。
        val next = NextTriggerCalculator.nextTrigger(
            alarm(7, 0, Weekdays.NONE),
            at(2026, 3, 10, 7, 0),
        )
        assertEquals(at(2026, 3, 11, 7, 0), next)
    }

    @Test
    fun `weekday alarm skips days that are not selected`() {
        // 2026-03-10 是周二；只勾周三 → 应该跳到 3-11 周三。
        val mask = Weekdays.bit(DayOfWeek.WEDNESDAY)
        val next = NextTriggerCalculator.nextTrigger(
            alarm(7, 30, mask),
            at(2026, 3, 10, 9, 0),
        )
        assertEquals(at(2026, 3, 11, 7, 30), next)
    }

    @Test
    fun `weekday alarm fires later the same day when selected`() {
        // 周二 06:00，闹钟周二 07:00 → 今天。
        val mask = Weekdays.bit(DayOfWeek.TUESDAY)
        val next = NextTriggerCalculator.nextTrigger(
            alarm(7, 0, mask),
            at(2026, 3, 10, 6, 0),
        )
        assertEquals(at(2026, 3, 10, 7, 0), next)
    }

    @Test
    fun `weekday alarm wraps to next week when today's time already passed`() {
        val mask = Weekdays.bit(DayOfWeek.TUESDAY)
        val next = NextTriggerCalculator.nextTrigger(
            alarm(7, 0, mask),
            at(2026, 3, 10, 9, 0),
        )
        assertEquals(at(2026, 3, 17, 7, 0), next)
    }

    @Test
    fun `weekdays preset jumps from saturday to monday`() {
        // 2026-03-14 是周六。
        val next = NextTriggerCalculator.nextTrigger(
            alarm(6, 45, Weekdays.WEEKDAYS),
            at(2026, 3, 14, 10, 0),
        )
        assertEquals(DayOfWeek.MONDAY, next?.dayOfWeek)
        assertEquals(at(2026, 3, 16, 6, 45), next)
    }

    @Test
    fun `weekend preset fires on saturday`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(9, 0, Weekdays.WEEKEND),
            at(2026, 3, 13, 23, 0), // 周五深夜
        )
        assertEquals(at(2026, 3, 14, 9, 0), next)
    }

    @Test
    fun `every day fires today when the time is still ahead`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(23, 59, Weekdays.ALL),
            at(2026, 3, 10, 0, 1),
        )
        assertEquals(at(2026, 3, 10, 23, 59), next)
    }

    @Test
    fun `millis helper agrees with the zoned result`() {
        val from = at(2026, 3, 10, 6, 0)
        val millis = NextTriggerCalculator.nextTriggerMillis(alarm(7, 0, Weekdays.NONE), from)
        assertEquals(at(2026, 3, 10, 7, 0).toInstant().toEpochMilli(), millis)
    }

    @Test
    fun `invalid mask yields no trigger instead of throwing`() {
        assertNull(NextTriggerCalculator.nextTrigger(alarm(7, 0, 0b1000_0000), at(2026, 3, 10, 6, 0)))
    }

    @Test
    fun `spring-forward gap shifts the alarm forward instead of crashing`() {
        // 2026-03-08 02:00 美东进入夏令时，02:30 这个本地时间根本不存在。
        val newYork = ZoneId.of("America/New_York")
        val from = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, newYork)
        val next = NextTriggerCalculator.nextTrigger(alarm(2, 30, Weekdays.ALL), from)

        assertNotNull(next)
        assertTrue("必须晚于起点", next!!.isAfter(from))
        assertEquals(8, next.dayOfMonth)
        // 被顺延了整整一个小时的跳变间隔。
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun `fall-back overlap still produces a valid trigger`() {
        // 2026-11-01 01:30 美东出现两次。
        val newYork = ZoneId.of("America/New_York")
        val from = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, newYork)
        val next = NextTriggerCalculator.nextTrigger(alarm(1, 30, Weekdays.ALL), from)

        assertNotNull(next)
        assertTrue(next!!.isAfter(from))
        assertEquals(1, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun `out of range hour and minute are clamped rather than throwing`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(99, 99, Weekdays.ALL),
            at(2026, 3, 10, 6, 0),
        )
        assertEquals(at(2026, 3, 10, 23, 59), next)
    }
}
