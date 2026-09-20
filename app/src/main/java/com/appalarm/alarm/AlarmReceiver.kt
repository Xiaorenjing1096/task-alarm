package com.appalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appalarm.appRepository
import com.appalarm.data.model.Weekdays
import com.appalarm.diagnostics.EventLog

/**
 * 闹钟到点时由系统唤醒。
 *
 * 这里只做三件事，而且顺序有讲究：**先把下一次排好，再开始响**。因为响铃会拉起
 * 前台服务和全屏界面，进程随时可能被系统回收；如果先响后排期，一旦在这中间被杀，
 * 明天的闹钟就永远丢了。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

        // 这一条是整个诊断里最关键的一行：如果到点了却没有它，就说明系统**根本没有
        // 唤醒应用** —— 那问题在系统侧（闹钟被取消 / 应用被强行停止），不在响铃链路。
        EventLog.record(context, "收到闹钟广播 id=${alarmId.take(8)}")

        val repository = context.appRepository
        val alarm = repository.alarmById(alarmId)
        if (alarm == null) {
            EventLog.record(context, "该闹钟已不存在，忽略")
            return
        }
        if (!alarm.enabled) {
            EventLog.record(context, "该闹钟已被停用，忽略")
            return
        }

        if (alarm.repeatDaysMask == Weekdays.NONE) {
            // 一次性闹钟：响过就关掉，否则明天同一时刻还会再响一遍。
            repository.setAlarmEnabled(alarmId, false)
            // 还必须把 AlarmManager 里的排期一并撤掉。因为进程很可能正是被这次闹钟
            // 拉起来的，而 AppAlarmApplication.onCreate 里的 rescheduleAll 会顺手
            // 按「下一次」再排一遍（对一次性闹钟就是明天）。不撤销的话，明天到点会
            // 把进程唤醒一次却什么都不做。
            AlarmScheduler(context).cancel(alarmId)
            EventLog.record(context, "一次性闹钟已停用并撤销排期")
        } else {
            AlarmScheduler(context).schedule(alarm)
        }

        EventLog.record(context, "启动响铃服务")
        RingService.start(context, alarmId)
    }

    companion object {
        const val ACTION_FIRE = "com.appalarm.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
