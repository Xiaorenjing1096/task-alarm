package com.appalarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 这个应用需要的三样「特殊」授权。
 *
 * 它们都不是普通的运行时权限，缺了任何一样闹钟都会以不明显的方式变弱：
 *
 *  - 精确闹钟：缺了会被系统推迟最多十几分钟，闹钟就不叫闹钟了。
 *  - 通知：Android 13+ 不授权就完全看不到响铃通知（也就没有全屏界面）。
 *  - 全屏通知：Android 14+ 默认不授予，缺了响铃只能是一条普通通知，
 *    不会自动盖在锁屏上。
 *
 * 所以主页要显式地把状态列出来，并给出一键跳转。
 */
data class PermissionStatus(
    val exactAlarmOk: Boolean,
    val notificationsOk: Boolean,
    val fullScreenIntentOk: Boolean,
) {
    val allOk: Boolean get() = exactAlarmOk && notificationsOk && fullScreenIntentOk
}

fun Context.readPermissionStatus(): PermissionStatus {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val exactAlarmOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else {
        true
    }

    val notificationsOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val fullScreenIntentOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    } else {
        true
    }

    return PermissionStatus(exactAlarmOk, notificationsOk, fullScreenIntentOk)
}

/** 跳转到「精确闹钟」授权页；系统版本不支持时返回 null。 */
fun Context.exactAlarmSettingsIntent(): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", packageName, null))
}

/** 跳转到本应用的通知设置页。 */
fun Context.notificationSettingsIntent(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

/** 跳转到「全屏通知」授权页；Android 14 以下不需要，返回 null。 */
fun Context.fullScreenIntentSettingsIntent(): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        .setData(Uri.fromParts("package", packageName, null))
}
