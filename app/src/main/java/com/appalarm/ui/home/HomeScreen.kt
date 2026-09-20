package com.appalarm.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.appalarm.alarm.AlarmScheduler
import com.appalarm.appRepository
import com.appalarm.core.NextTriggerLabel
import com.appalarm.data.model.Alarm
import com.appalarm.data.model.TaskType
import com.appalarm.data.model.Weekdays
import com.appalarm.ui.PermissionStatus
import com.appalarm.ui.exactAlarmSettingsIntent
import com.appalarm.ui.fullScreenIntentSettingsIntent
import com.appalarm.ui.notificationSettingsIntent
import com.appalarm.ui.readPermissionStatus
import com.appalarm.ui.theme.AppAlarmTheme

@Composable
fun HomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (String) -> Unit,
    onOpenQuestionBank: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appRepository
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissions by remember { mutableStateOf(context.readPermissionStatus()) }
    var alarmPendingDelete by remember { mutableStateOf<Alarm?>(null) }

    // 从系统设置页返回时重新检查：用户很可能刚在那里授过权。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = context.readPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissions = context.readPermissionStatus() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Default.Add, contentDescription = "新建闹钟")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "任务闹钟",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenQuestionBank) {
                    Icon(Icons.Default.List, contentDescription = "题库")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }

            if (!permissions.allOk) {
                PermissionCard(
                    permissions = permissions,
                    onFixNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(context.notificationSettingsIntent())
                        }
                    },
                    onFixExactAlarm = {
                        context.exactAlarmSettingsIntent()?.let(context::startActivity)
                    },
                    onFixFullScreen = {
                        context.fullScreenIntentSettingsIntent()?.let(context::startActivity)
                    },
                )
            }

            if (repository.alarms.isEmpty()) {
                Column(Modifier.padding(24.dp)) {
                    Text("还没有闹钟。", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "点右下角的加号新建一个。响铃后会要求你做完题目才能关掉。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(repository.alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { enabled ->
                                repository.setAlarmEnabled(alarm.id, enabled)
                                repository.alarmById(alarm.id)?.let {
                                    AlarmScheduler(context).schedule(it)
                                }
                            },
                            onClick = { onEditAlarm(alarm.id) },
                            onDelete = { alarmPendingDelete = alarm },
                        )
                    }
                }
            }
        }
    }

    alarmPendingDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { alarmPendingDelete = null },
            title = { Text("删除 ${alarm.timeLabel} 的闹钟？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AlarmScheduler(context).cancel(alarm.id)
                        repository.deleteAlarm(alarm.id)
                        alarmPendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { alarmPendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    permissions: PermissionStatus,
    onFixNotifications: () -> Unit,
    onFixExactAlarm: () -> Unit,
    onFixFullScreen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("还需要授权，否则闹钟可能不可靠", fontWeight = FontWeight.SemiBold)
            }

            if (!permissions.notificationsOk) {
                PermissionAction(
                    detail = "通知权限未授予，响铃时不会有任何提醒",
                    actionLabel = "去授权",
                    onAction = onFixNotifications,
                )
            }
            if (!permissions.exactAlarmOk) {
                PermissionAction(
                    detail = "精确闹钟未允许，闹钟可能被系统推迟十几分钟",
                    actionLabel = "去设置",
                    onAction = onFixExactAlarm,
                )
            }
            if (!permissions.fullScreenIntentOk) {
                PermissionAction(
                    detail = "全屏通知未允许，响铃不会自动盖在锁屏上",
                    actionLabel = "去设置",
                    onAction = onFixFullScreen,
                )
            }
        }
    }
}

@Composable
private fun PermissionAction(detail: String, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = alarm.timeLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(Weekdays.describe(alarm.repeatDaysMask))
                        append(" · ")
                        append(alarm.taskType.displayName)
                        if (alarm.taskCount > 1) append(" ×${alarm.taskCount}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = NextTriggerLabel.describe(alarm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 预览：这两块都不依赖仓库，可以直接渲染。
// PermissionCard 尤其值得预览 —— 它只在缺权限时才出现，平时很难看到。
// ---------------------------------------------------------------------------

@Preview(name = "闹钟列表项", showBackground = true, widthDp = 380)
@Composable
private fun PreviewAlarmRow() {
    AppAlarmTheme {
        Column(Modifier.padding(16.dp)) {
            AlarmRow(
                alarm = Alarm(
                    id = "preview",
                    hour = 7,
                    minute = 30,
                    repeatDaysMask = Weekdays.WEEKDAYS,
                    label = "上班",
                    taskType = TaskType.ARITHMETIC,
                    taskCount = 3,
                ),
                onToggle = {},
                onClick = {},
                onDelete = {},
            )
        }
    }
}

@Preview(name = "权限提醒卡片", showBackground = true, widthDp = 380)
@Composable
private fun PreviewPermissionCard() {
    AppAlarmTheme {
        Column(Modifier.padding(16.dp)) {
            PermissionCard(
                permissions = PermissionStatus(
                    exactAlarmOk = false,
                    notificationsOk = false,
                    fullScreenIntentOk = false,
                ),
                onFixNotifications = {},
                onFixExactAlarm = {},
                onFixFullScreen = {},
            )
        }
    }
}
