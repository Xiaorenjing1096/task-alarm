package com.appalarm.ui.edit

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appalarm.alarm.AlarmScheduler
import com.appalarm.appRepository
import com.appalarm.core.GesturePattern
import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import com.appalarm.data.model.Weekdays
import com.appalarm.ui.components.PatternLock
import com.appalarm.ui.ringtone.ClipRangePicker
import com.appalarm.ui.ringtone.RingtonePickerDialog
import com.appalarm.ui.ringtone.RingtoneSupport
import java.time.DayOfWeek

@Composable
fun AlarmEditScreen(alarmId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = context.appRepository

    // 新建时用 remember 固定住这一次生成的草稿，否则每次重组都会换一个 id。
    var draft by remember(alarmId) {
        mutableStateOf(alarmId?.let { repository.alarmById(it) } ?: repository.newAlarm())
    }

    var showRingtonePicker by remember { mutableStateOf(false) }
    var showQuestionPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var clipDurationMs by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 换铃声后重新读时长；读不到就隐藏截取功能。
    LaunchedEffect(draft.ringtoneUri) {
        clipDurationMs = draft.ringtoneUri
            ?.takeIf { it.isNotBlank() }
            ?.let { RingtoneSupport.durationMs(context, it) }
    }

    val bankQuestions = repository.questionsOfType(draft.taskType)

    fun save() {
        if (draft.taskType == TaskType.GESTURE &&
            !GesturePattern.isValid(GesturePattern.decode(draft.gesturePattern))
        ) {
            errorMessage = "请先录一个至少 4 个点的手势，否则闹钟响起来没法关"
            return
        }
        repository.upsertAlarm(draft)
        repository.alarmById(draft.id)?.let { AlarmScheduler(context).schedule(it) }
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---------------- 顶部操作栏 ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (alarmId == null) "新建闹钟" else "编辑闹钟",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.weight(1f))
            if (alarmId != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // ---------------- 时间 ----------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> draft = draft.copy(hour = hour, minute = minute) },
                        draft.hour,
                        draft.minute,
                        true,
                    ).show()
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = draft.timeLabel,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("点击修改时间", style = MaterialTheme.typography.labelMedium)
            }
        }

        // ---------------- 重复 ----------------
        SectionTitle("重复")
        WeekdaySelector(draft.repeatDaysMask) { draft = draft.copy(repeatDaysMask = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip("仅一次", draft.repeatDaysMask == Weekdays.NONE) {
                draft = draft.copy(repeatDaysMask = Weekdays.NONE)
            }
            SelectableChip("每天", draft.repeatDaysMask == Weekdays.ALL) {
                draft = draft.copy(repeatDaysMask = Weekdays.ALL)
            }
            SelectableChip("工作日", draft.repeatDaysMask == Weekdays.WEEKDAYS) {
                draft = draft.copy(repeatDaysMask = Weekdays.WEEKDAYS)
            }
            SelectableChip("周末", draft.repeatDaysMask == Weekdays.WEEKEND) {
                draft = draft.copy(repeatDaysMask = Weekdays.WEEKEND)
            }
        }
        Text(
            text = "当前：${Weekdays.describe(draft.repeatDaysMask)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---------------- 标签 ----------------
        OutlinedTextField(
            value = draft.label,
            onValueChange = { draft = draft.copy(label = it) },
            label = { Text("标签（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        // ---------------- 铃声 ----------------
        SectionTitle("铃声")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = draft.ringtoneTitle.ifBlank {
                        if (draft.ringtoneUri.isNullOrBlank()) "系统默认闹钟铃声" else "已选择"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                val clipText = when {
                    draft.clipEndMs > 0L ->
                        "片段 ${RingtoneSupport.formatMs(draft.clipStartMs)} – " +
                            RingtoneSupport.formatMs(draft.clipEndMs)

                    draft.clipStartMs > 0L -> "从 ${RingtoneSupport.formatMs(draft.clipStartMs)} 开始"
                    else -> "整首播放"
                }
                Text(
                    text = clipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { showRingtonePicker = true }) { Text("更换") }
        }

        val duration = clipDurationMs
        val ringtoneUri = draft.ringtoneUri
        if (duration != null && !ringtoneUri.isNullOrBlank()) {
            ClipRangePicker(
                uriString = ringtoneUri,
                durationMs = duration,
                startMs = draft.clipStartMs,
                endMs = draft.clipEndMs,
                onRangeChange = { start, end ->
                    draft = draft.copy(clipStartMs = start, clipEndMs = end)
                },
            )
        }

        HorizontalDivider()

        // ---------------- 响铃方式 ----------------
        SectionTitle("响铃方式")
        Text("音量渐强：${if (draft.volumeRampSeconds == 0) "关闭" else "${draft.volumeRampSeconds} 秒"}")
        Slider(
            value = draft.volumeRampSeconds.toFloat(),
            onValueChange = { draft = draft.copy(volumeRampSeconds = it.toInt()) },
            valueRange = 0f..60f,
            steps = 11,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("震动", Modifier.weight(1f))
            Switch(
                checked = draft.vibrate,
                onCheckedChange = { draft = draft.copy(vibrate = it) },
            )
        }

        HorizontalDivider()

        // ---------------- 任务 ----------------
        SectionTitle("关闭闹钟的任务")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskType.entries.take(3).forEach { type ->
                    SelectableChip(type.displayName, draft.taskType == type) {
                        draft = draft.copy(taskType = type)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskType.entries.drop(3).forEach { type ->
                    SelectableChip(type.displayName, draft.taskType == type) {
                        draft = draft.copy(taskType = type)
                    }
                }
            }
        }

        if (draft.taskType == TaskType.ARITHMETIC) {
            StepperRow(
                label = "难度",
                value = draft.arithmeticDifficulty,
                range = 1..3,
                describe = { when (it) { 1 -> "简单"; 2 -> "中等"; else -> "较难" } },
                onChange = { draft = draft.copy(arithmeticDifficulty = it) },
            )
        }

        if (draft.taskType == TaskType.GESTURE) {
            GestureRecorder(
                pattern = GesturePattern.decode(draft.gesturePattern),
                onRecorded = { draft = draft.copy(gesturePattern = GesturePattern.encode(it)) },
            )
        }

        if (draft.taskType.usesQuestionBank) {
            QuestionSelector(
                currentId = draft.questionId,
                questions = bankQuestions,
                onChoose = { showQuestionPicker = true },
                onClear = { draft = draft.copy(questionId = null) },
            )
            if (bankQuestions.isEmpty()) {
                Text(
                    text = "题库里还没有${draft.taskType.displayName}，响铃时会自动改用计算题，" +
                        "你也可以先去题库里加几道。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        StepperRow(
            label = "需要答对",
            value = draft.taskCount,
            range = 1..5,
            describe = { "$it 题" },
            onChange = { draft = draft.copy(taskCount = it) },
        )

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
            Text("保存")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showRingtonePicker) {
        RingtonePickerDialog(
            currentUri = draft.ringtoneUri,
            onDismiss = { showRingtonePicker = false },
            onPicked = { option ->
                showRingtonePicker = false
                draft = if (option.uri.isBlank()) {
                    draft.copy(ringtoneUri = null, ringtoneTitle = "", clipStartMs = 0L, clipEndMs = -1L)
                } else {
                    draft.copy(
                        ringtoneUri = option.uri,
                        ringtoneTitle = option.title,
                        clipStartMs = 0L,
                        clipEndMs = -1L,
                    )
                }
            },
        )
    }

    if (showQuestionPicker) {
        QuestionPickerDialog(
            questions = bankQuestions,
            onDismiss = { showQuestionPicker = false },
            onPick = { id ->
                showQuestionPicker = false
                draft = draft.copy(questionId = id)
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这个闹钟？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        AlarmScheduler(context).cancel(draft.id)
                        repository.deleteAlarm(draft.id)
                        onDone()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun WeekdaySelector(mask: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (dayNumber in 1..7) {
            val day = DayOfWeek.of(dayNumber)
            val selected = Weekdays.contains(mask, day)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onChange(Weekdays.toggle(mask, day)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Weekdays.shortName(day),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    describe: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value > range.first) onChange(value - 1) },
            enabled = value > range.first,
        ) { Text("−") }
        Text(
            text = describe(value),
            modifier = Modifier
                .width(64.dp)
                .padding(horizontal = 8.dp),
        )
        OutlinedButton(
            onClick = { if (value < range.last) onChange(value + 1) },
            enabled = value < range.last,
        ) { Text("+") }
    }
}

@Composable
private fun GestureRecorder(pattern: List<Int>, onRecorded: (List<Int>) -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (pattern.isEmpty()) {
                "在下面画出解锁手势（至少 4 个点）"
            } else {
                "已录制 ${pattern.size} 个点；重新画一次即可覆盖"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        PatternLock(
            modifier = Modifier.fillMaxWidth(0.7f),
            previewPattern = if (message == null) pattern.takeIf { it.isNotEmpty() } else null,
            onPatternFinished = { attempted ->
                if (GesturePattern.isValid(attempted)) {
                    message = null
                    onRecorded(attempted)
                } else {
                    message = "太短了，至少要连 4 个点"
                }
            },
        )
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuestionSelector(
    currentId: String?,
    questions: List<Question>,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    val current = questions.firstOrNull { it.id == currentId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("出题方式", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = current?.prompt ?: "从题库随机抽题",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (current != null) {
            TextButton(onClick = onClear) { Text("改回随机") }
        } else {
            OutlinedButton(onClick = onChoose) { Text("指定题目") }
        }
    }
}

@Composable
private fun QuestionPickerDialog(
    questions: List<Question>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("指定一道题") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextButton(onClick = { onPick(null) }) { Text("从题库随机抽") }
                HorizontalDivider()
                questions.forEach { question ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(question.id) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(question.prompt, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "难度 ${question.difficulty}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
