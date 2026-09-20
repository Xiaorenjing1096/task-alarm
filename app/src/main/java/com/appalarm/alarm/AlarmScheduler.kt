package com.appalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appalarm.MainActivity
import com.appalarm.core.NextTriggerCalculator
import com.appalarm.data.model.Alarm
import com.appalarm.diagnostics.EventLog
import java.time.ZonedDateTime

/**
 * 把闹钟交给系统的 AlarmManager。
 *
 * 用 [AlarmManager.setAlarmClock] 而不是 setExact：这是唯一能穿透 Doze、
 * 并且会在状态栏显示闹钟图标（用户能一眼看到「下一个闹钟是几点」）的方式，
 * 也正是系统认可的闹钟应用该用的 API。
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Android 12+ 精确闹钟权限是否可用。声明了 USE_EXACT_ALARM 时始终为 true。 */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    fun schedule(alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return
        }
        val triggerAt = NextTriggerCalculator.nextTriggerMillis(alarm, ZonedDateTime.now())
        if (triggerAt == null) {
            cancel(alarm.id)
            return
        }

        val operation = firePendingIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT)
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(alarm.id),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 记下真正排给系统的时刻（不是闹钟自身的 timeLabel），这样对比「到点有没有
        // 触发」时不会因为跨天而看错。
        val whenText = java.time.Instant.ofEpochMilli(triggerAt)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
            EventLog.record(context, "已排期(精确) $whenText  id=${alarm.id.take(8)}")
        } catch (_: SecurityException) {
            // 精确闹钟权限被撤销时退化：晚几分钟响，总比完全不响好。
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            EventLog.record(
                context,
                "已排期(不精确：精确闹钟未授权，可能被推迟) $whenText  id=${alarm.id.take(8)}",
            )
        }
    }

    fun cancel(alarmId: String) {
        val pending = firePendingIntentOrNull(alarmId, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    private fun requestCode(alarmId: String): Int = alarmId.hashCode()

    private fun firePendingIntent(alarmId: String, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun firePendingIntentOrNull(alarmId: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun fireIntent(alarmId: String): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
}
