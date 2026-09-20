package com.appalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appalarm.appRepository
import com.appalarm.data.model.Weekdays

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

        val repository = context.appRepository
        val alarm = repository.alarmById(alarmId) ?: return
        if (!alarm.enabled) return

        if (alarm.repeatDaysMask == Weekdays.NONE) {
            // 一次性闹钟：响过就关掉，否则明天同一时刻还会再响一遍。
            repository.setAlarmEnabled(alarmId, false)
        } else {
            AlarmScheduler(context).schedule(alarm)
        }

        RingService.start(context, alarmId)
    }

    companion object {
        const val ACTION_FIRE = "com.appalarm.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
