package com.appalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appalarm.appRepository

/**
 * 开机、应用升级、改时间、换时区之后重新对齐所有闹钟。
 *
 * 系统的闹钟排期不跨重启也不跨时区变化，必须由应用自己重排。
 *
 * 注意这里**不处理** LOCKED_BOOT_COMPLETED：数据存在凭据加密存储里，开机解锁前
 * 读不到，硬排反而会读到空数据。等用户解锁后系统会再发 BOOT_COMPLETED。
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = context.appRepository
        AlarmScheduler(context).rescheduleAll(repository.alarms)
    }
}
