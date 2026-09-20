package com.appalarm.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appalarm.ui.ring.RingScreen
import com.appalarm.ui.theme.AppAlarmTheme

/**
 * 响铃时盖在锁屏上的答题界面。
 *
 * 在清单里声明为 singleInstance + taskAffinity=""，所以它独占一个任务栈：
 * 全屏 Intent 和「补一次 startActivity」两条路径同时生效时也不会出现两个实例，
 * 而且不会混进主界面的最近任务里。
 */
class RingActivity : ComponentActivity() {

    private var ringingAlarmId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        // 取到局部 val 再用：ringingAlarmId 是委托属性，编译器无法对它做智能转换。
        val requestedId = intent?.getStringExtra(RingService.EXTRA_ALARM_ID)
        ringingAlarmId = requestedId
        if (requestedId == null) {
            finish()
            return
        }

        setContent {
            AppAlarmTheme {
                RingScreen(
                    alarmId = requestedId,
                    onSolved = {
                        RingService.stop(this)
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleInstance：闹钟 A 正在响、闹钟 B 又到点时，会复用这个实例。
        ringingAlarmId = intent.getStringExtra(RingService.EXTRA_ALARM_ID) ?: ringingAlarmId
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        // 答题时屏幕不能自己灭掉。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
