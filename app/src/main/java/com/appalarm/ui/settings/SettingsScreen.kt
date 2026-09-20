package com.appalarm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appalarm.appRepository
import com.appalarm.data.model.TaskType
import com.appalarm.ui.exactAlarmSettingsIntent
import com.appalarm.ui.fullScreenIntentSettingsIntent
import com.appalarm.ui.notificationSettingsIntent
import com.appalarm.ui.readPermissionStatus

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = context.appRepository
    val settings = repository.settings

    var permissions by remember { mutableStateOf(context.readPermissionStatus()) }
    var toast by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.weight(1f))
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(48.dp))
        }

        // ---------------- 权限 ----------------
        Text("响铃权限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (permissions.allOk) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionLine(
                    label = "通知权限",
                    ok = permissions.notificationsOk,
                    hint = "没有通知就看不到响铃提醒",
                    actionLabel = "去授权",
                    onAction = {
                        context.startActivity(context.notificationSettingsIntent())
                        permissions = context.readPermissionStatus()
                    },
                )
                PermissionLine(
                    label = "精确闹钟",
                    ok = permissions.exactAlarmOk,
                    hint = "未允许时系统可能把闹钟推迟十几分钟",
                    actionLabel = "去设置",
                    onAction = {
                        context.exactAlarmSettingsIntent()?.let(context::startActivity)
                        permissions = context.readPermissionStatus()
                    },
                )
                PermissionLine(
                    label = "全屏通知",
                    ok = permissions.fullScreenIntentOk,
                    hint = "未允许时响铃不会自动盖在锁屏上",
                    actionLabel = "去设置",
                    onAction = {
                        context.fullScreenIntentSettingsIntent()?.let(context::startActivity)
                        permissions = context.readPermissionStatus()
                    },
                )
                OutlinedButton(onClick = { permissions = context.readPermissionStatus() }) {
                    Text("重新检查")
                }
            }
        }

        HorizontalDivider()

        // ---------------- 手势 ----------------
        Text("手势题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("允许反着画")
                Text(
                    text = "手势画反方向也算通过。半睡半醒时容易画反，开了更宽容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.allowReverseGesture,
                onCheckedChange = { value ->
                    repository.updateSettings { it.copy(allowReverseGesture = value) }
                },
            )
        }

        HorizontalDivider()

        // ---------------- 新建闹钟默认值 ----------------
        Text("新建闹钟的默认值", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Text("默认题型", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskType.entries.take(3).forEach { type ->
                DefaultChip(type.displayName, settings.defaultTaskType == type) {
                    repository.updateSettings { it.copy(defaultTaskType = type) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskType.entries.drop(3).forEach { type ->
                DefaultChip(type.displayName, settings.defaultTaskType == type) {
                    repository.updateSettings { it.copy(defaultTaskType = type) }
                }
            }
        }

        Text("计算题难度：${settings.defaultArithmeticDifficulty}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { level ->
                DefaultChip(
                    label = when (level) { 1 -> "简单"; 2 -> "中等"; else -> "较难" },
                    selected = settings.defaultArithmeticDifficulty == level,
                ) {
                    repository.updateSettings { it.copy(defaultArithmeticDifficulty = level) }
                }
            }
        }

        HorizontalDivider()

        // ---------------- 题库 ----------------
        Text("题库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "当前共 ${repository.questions.size} 道题。删除过的内置题目可以在这里补回来。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = {
                val restored = repository.restoreBuiltInQuestions()
                toast = if (restored == 0) "内置题目都在，不需要恢复" else "已补回 $restored 道内置题目"
            },
        ) { Text("恢复内置题目") }

        toast?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider()

        // ---------------- 已知限制 ----------------
        Text("已知限制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Limitation(
                    "无法阻止强行关闭",
                    "用户仍然可以从系统设置里强行停止应用或重启手机来绕过题目。" +
                        "这是 Android 的设计，任何闹钟应用都绕不过去，本应用不做激进的对抗，以免变得不可靠。",
                )
                Limitation(
                    "铃声片段只在本应用内有效",
                    "截取是「记录起止时间、播放时循环这一段」，不会生成新的音频文件，" +
                        "所以系统铃声设置里看不到这个片段。这样不需要音频转码，也不占额外存储。",
                )
                Limitation(
                    "题库为空时会自动改用计算题",
                    "如果某个闹钟要用的题型在题库里一道题都没有，响铃时会自动出计算题，" +
                        "以保证闹钟一定关得掉。",
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionLine(
    label: String,
    ok: Boolean,
    hint: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (ok) "$label · 已就绪" else "$label · 未授予",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!ok) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!ok) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun DefaultChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun Limitation(title: String, detail: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
