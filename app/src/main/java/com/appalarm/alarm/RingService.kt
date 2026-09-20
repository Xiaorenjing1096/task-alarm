package com.appalarm.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.appalarm.appRepository
import com.appalarm.data.model.Alarm
import com.appalarm.diagnostics.EventLog

/**
 * 响铃。
 *
 * 做成前台服务而不是在广播里直接放音，原因有两个：广播接收器的生命周期只有几秒，
 * 而且从 Android 8 起后台进程会被限制，只有前台服务能保证铃声一直响到用户答题为止。
 *
 * 铃声「截取」的实现方式是记录起止时间、播放时在这段区间内循环，不做音频转码 ——
 * 这样既不需要引入编解码库，也不占额外存储。
 */
class RingService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarmId: String? = null
    private var clipStartMs = 0L
    private var clipEndMs = -1L
    private var rampSeconds = 0
    private var playbackStartedAt = 0L

    /** 每 250ms 检查一次播放进度，用来处理片段循环和音量渐强。 */
    private val ticker = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                try {
                    if (clipEndMs > 0L && player.currentPosition >= clipEndMs) {
                        player.seekTo(clipStartMs.toInt())
                    }
                    val volume = currentVolume()
                    player.setVolume(volume, volume)
                } catch (_: IllegalStateException) {
                    // 播放器已经不可用，onDestroy 会收尾
                }
            }
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedId = intent?.getStringExtra(EXTRA_ALARM_ID) ?: alarmId
        if (requestedId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarm = applicationContext.appRepository.alarmById(requestedId)
        if (alarm == null) {
            // 闹钟在响铃前被删掉了。仍要先满足前台服务的约定再退出。
            goForeground(RingNotifier.placeholderNotification(this))
            stopSelf()
            return START_NOT_STICKY
        }

        alarmId = requestedId
        EventLog.record(this, "响铃服务开始执行")
        try {
            goForeground(RingNotifier.ringingNotification(this, alarm))
            EventLog.record(this, "已进入前台服务")
        } catch (e: Exception) {
            // 进不了前台也不能就这么算了：通知照样要发出去，声音也要继续响，
            // 否则用户看到的就是「什么都没有」—— 那正是最难排查的症状。
            EventLog.record(this, "进入前台服务失败：${e.javaClass.simpleName}: ${e.message}")
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(
                    RingNotifier.NOTIFICATION_ID_RINGING,
                    RingNotifier.ringingNotification(this, alarm),
                )
            }
        }
        acquireWakeLock()
        startPlayback(alarm)
        launchRingActivity(requestedId)
        return START_NOT_STICKY
    }

    /**
     * 再补一次「拉起答题界面」。
     *
     * 这里留了两条路，因为不同系统版本的放行规则不一样：
     *
     *  1. 直接 `startActivity` —— 在「显示在其他应用上层」被授予时属于 BAL 豁免项。
     *  2. 复用全屏通知那个 PendingIntent 的 `send()`。
     *
     * **实测（Android 15 / targetSdk 37）：亮屏时两条都会被拦**，日志里是
     * `Background activity launch blocked! ... autoOptInReason: notPendingIntent`。
     * 原因是平台设计：`setFullScreenIntent` 只在**锁屏或息屏**时才会被系统自动
     * 全屏拉起，亮屏时只显示悬浮通知（这时闹钟仍在响、通知仍在，点一下即可进来）。
     *
     * 所以这两步是「能成就成」的补充，不是主路径 —— 主路径是锁屏/息屏时系统自己
     * 拉起全屏界面，实测在进程被杀的情况下也正常。
     * 失败不算错误；RingActivity 是 singleInstance，不会出现两个界面。
     */
    private fun launchRingActivity(alarmId: String) {
        runCatching {
            startActivity(
                Intent(this, RingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_ALARM_ID, alarmId),
            )
        }
        runCatching { RingNotifier.fullScreenPendingIntent(this, alarmId).send() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        stopPlayback()
        stopVibration()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        EventLog.record(this, "响铃结束（服务销毁）")
        super.onDestroy()
    }

    // ------------------------------------------------------------ 前台服务

    /**
     * 进入前台。
     *
     * 这里不用 ServiceCompat.startForeground：那个重载是 androidx.core 1.12 才加的，
     * 而本项目的 core-ktx 还是模板自带的 1.10.1。自己按版本分支既不依赖版本升级，
     * 行为也更直白。
     */
    private fun goForeground(notification: Notification) {
        val id = RingNotifier.NOTIFICATION_ID_RINGING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, notification)
        }
    }

    // ------------------------------------------------------------ 播放

    private fun startPlayback(alarm: Alarm) {
        stopPlayback()

        clipStartMs = alarm.clipStartMs.coerceAtLeast(0L)
        clipEndMs = alarm.clipEndMs
        rampSeconds = alarm.volumeRampSeconds.coerceAtLeast(0)

        // 依次尝试：用户选的铃声 → 系统闹钟 → 来电铃声 → 通知音。
        // 用户选的音频可能被删掉或权限失效，这时必须还有声音响起来。
        val candidates = buildList {
            alarm.ringtoneUri?.let { add(Uri.parse(it)) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
        }.distinct()

        val playing = candidates.firstOrNull { tryStartPlayback(it) }
        if (playing != null) {
            EventLog.record(this, "开始播放铃声：$playing")
            playbackStartedAt = SystemClock.elapsedRealtime()
            handler.post(ticker)
        } else {
            EventLog.record(this, "所有铃声都无法播放（共试了 ${candidates.size} 个）")
        }

        if (alarm.vibrate) startVibration()
    }

    private fun tryStartPlayback(uri: Uri): Boolean {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(this, uri)
            // 选了片段就自己循环那一段；播整首则交给 MediaPlayer 循环。
            player.isLooping = clipEndMs <= 0L
            player.prepare()
            if (clipStartMs > 0L) player.seekTo(clipStartMs.toInt())
            player.setVolume(0f, 0f)
            player.start()
            mediaPlayer = player
            true
        } catch (_: Exception) {
            runCatching { player.release() }
            false
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(ticker)
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun currentVolume(): Float {
        if (rampSeconds <= 0) return 1f
        val elapsedSeconds = (SystemClock.elapsedRealtime() - playbackStartedAt) / 1000f
        return (elapsedSeconds / rampSeconds).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------ 震动

    private fun startVibration() {
        val service = getSystemService(Vibrator::class.java) ?: return
        if (!service.hasVibrator()) return
        vibrator = service
        runCatching { service.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)) }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    // ------------------------------------------------------------ 唤醒锁

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                // 带超时，避免任何异常路径下留下永久唤醒锁把电耗光。
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"

        private const val TICK_INTERVAL_MS = 250L
        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L
        private const val WAKE_LOCK_TAG = "AppAlarm:ringing"

        /** 等 0ms → 震 800ms → 停 600ms，然后循环。 */
        private val VIBRATION_PATTERN = longArrayOf(0L, 800L, 600L)

        fun start(context: Context, alarmId: String) {
            val intent = Intent(context, RingService::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingService::class.java))
        }
    }
}
