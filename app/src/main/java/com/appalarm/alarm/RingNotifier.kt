package com.appalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.appalarm.data.model.Alarm

/** 响铃通知的频道与内容。 */
object RingNotifier {

    const val CHANNEL_RINGING = "alarm_ringing"
    const val NOTIFICATION_ID_RINGING = 1001

    private const val REQUEST_FULL_SCREEN = 2001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_RINGING,
            "闹钟响铃",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "闹钟响起时的全屏提醒。必须完成题目才能关闭。"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            // 震动由 RingService 用自己的节奏控制，这里关掉，否则会叠成两套震动。
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 响铃通知。
     *
     * 关键是 [NotificationCompat.Builder.setFullScreenIntent]：这是让响铃界面
     * 能够直接盖在锁屏上、而不是变成一条普通通知的官方途径。同时把 contentIntent
     * 也指向同一个界面，这样用户点通知也能进来。
     */
    fun ringingNotification(context: Context, alarm: Alarm): Notification {
        val fullScreen = fullScreenPendingIntent(context, alarm.id)
        val title = alarm.label.ifBlank { "闹钟 ${alarm.timeLabel}" }

        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("完成题目才能关闭闹钟")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()
    }

    /**
     * 极端竞态下用的占位通知：闹钟在响之前被删掉了。
     *
     * 之所以不直接 stopSelf，是因为 Android 规定 startForegroundService 之后
     * 必须调用过 startForeground，否则会抛 RemoteServiceException。
     */
    fun placeholderNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闹钟")
            .setContentText("正在结束响铃")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()

    fun fullScreenPendingIntent(context: Context, alarmId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_FULL_SCREEN,
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RingService.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
