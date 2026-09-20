package com.appalarm

import android.app.Application
import android.content.Context
import com.appalarm.alarm.AlarmScheduler
import com.appalarm.alarm.RingNotifier
import com.appalarm.data.AppRepository

class AppAlarmApplication : Application() {

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this).also { it.load() }
        RingNotifier.ensureChannels(this)

        // 幂等的安全网：无论之前是因为重装、升级还是进程被杀导致排期丢失，
        // 每次启动都把闹钟重新对齐一遍。开机和时区变化由 RescheduleReceiver 负责。
        AlarmScheduler(this).rescheduleAll(repository.alarms)
    }
}

/** 在 Activity / Service / Receiver 里拿仓库的快捷方式。 */
val Context.appRepository: AppRepository
    get() = (applicationContext as AppAlarmApplication).repository
