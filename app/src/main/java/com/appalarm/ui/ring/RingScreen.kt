package com.appalarm.ui.ring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appalarm.appRepository
import com.appalarm.core.AnswerChecker
import com.appalarm.core.GesturePattern
import com.appalarm.core.RingTask
import com.appalarm.core.RingTaskFactory
import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import com.appalarm.ui.components.PatternLock
import com.appalarm.ui.theme.AppAlarmTheme

/**
 * 响铃时盖在锁屏上的答题界面。
 *
 * 设计上刻意「不给人留退路」：返回键被拦下，答错只会提示重来，题目由
 * [RingTaskFactory] 保证一定有解。但也不做过度对抗 —— 用户可以强杀应用或
 * 重启手机，这是 Android 的设计，硬扛只会让应用变得不可靠。
 */
@Composable
fun RingScreen(
    alarmId: String,
    onSolved: () -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appRepository
    val alarm = repository.alarmById(alarmId)

    if (alarm == null) {
        // 闹钟已经被删掉了，没什么可答的。
        LaunchedEffect(Unit) { onSolved() }
        return
    }

    val allowReverseGesture = repository.settings.allowReverseGesture
    val totalTasks = alarm.taskCount.coerceAtLeast(1)

    var solvedCount by remember(alarmId) { mutableStateOf(0) }
    var backPressed by remember(alarmId) { mutableStateOf(false) }
    var task by remember(alarmId) {
        mutableStateOf(
            RingTaskFactory.create(
                alarm = alarm,
                bank = repository.questionsOfType(alarm.taskType),
                allowReverseGesture = allowReverseGesture,
            ),
        )
    }

    fun nextTask() {
        task = RingTaskFactory.create(
            alarm = alarm,
            bank = repository.questionsOfType(alarm.taskType),
            allowReverseGesture = allowReverseGesture,
        )
    }

    fun handleSolved() {
        backPressed = false
        if (solvedCount + 1 >= totalTasks) onSolved() else {
            solvedCount += 1
            nextTask()
        }
    }

    BackHandler(enabled = true) { backPressed = true }

    RingScreenContent(
        alarm = alarm,
        task = task,
        solvedCount = solvedCount,
        totalTasks = totalTasks,
        backPressed = backPressed,
        onSolved = ::handleSolved,
    )
}

/**
 * 响铃界面的纯展示部分。
 *
 * 把「状态」和「长什么样」拆开，是为了能用 @Preview 直接渲染 —— 响铃这一屏是整个
 * 应用最核心、也最难在没有设备时验证的一屏：它只在闹钟响的那几分钟存在，而且要求
 * 锁屏状态下才看得到。拆开之后，改样式不用装到设备上就能看。
 */
@Composable
private fun RingScreenContent(
    alarm: Alarm,
    task: RingTask,
    solvedCount: Int,
    totalTasks: Int,
    backPressed: Boolean,
    onSolved: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = alarm.timeLabel,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "完成题目才能关闭闹钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (totalTasks > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "进度  ${solvedCount + 1} / $totalTasks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(24.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    when (task) {
                        is RingTask.Choice -> ChoiceTask(task.question, onSolved)
                        is RingTask.FillBlank -> FillBlankTask(task.question, onSolved)
                        is RingTask.Arithmetic -> ArithmeticTask(task.prompt, task.answer, onSolved)
                        is RingTask.Gesture -> GestureTask(task.pattern, task.allowReverse, onSolved)
                    }
                }
            }

            if (backPressed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "答对题目才能关闭闹钟",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 预览：不需要任何设备，在 Studio 的 Design / Split 面板里直接看。
// ---------------------------------------------------------------------------

private fun previewAlarm(taskType: TaskType, taskCount: Int = 1) = Alarm(
    id = "preview",
    hour = 7,
    minute = 30,
    label = "起床",
    taskType = taskType,
    taskCount = taskCount,
)

@Preview(name = "响铃 · 计算题", showBackground = true, heightDp = 900)
@Composable
private fun PreviewRingArithmetic() {
    AppAlarmTheme {
        RingScreenContent(
            alarm = previewAlarm(TaskType.ARITHMETIC),
            task = RingTask.Arithmetic("37 × 8 + 14 = ?", 310),
            solvedCount = 0,
            totalTasks = 1,
            backPressed = false,
            onSolved = {},
        )
    }
}

@Preview(name = "响铃 · 多选题", showBackground = true, heightDp = 900)
@Composable
private fun PreviewRingMultiChoice() {
    AppAlarmTheme {
        RingScreenContent(
            alarm = previewAlarm(TaskType.MULTI_CHOICE),
            task = RingTask.Choice(
                Question(
                    id = "preview-multi",
                    type = TaskType.MULTI_CHOICE,
                    prompt = "下列哪些是水果？",
                    options = listOf("苹果", "香蕉", "胡萝卜", "白菜"),
                    answers = listOf("苹果", "香蕉"),
                ),
            ),
            solvedCount = 0,
            totalTasks = 1,
            backPressed = false,
            onSolved = {},
        )
    }
}

@Preview(name = "响铃 · 填空题（多题进度 + 拦截返回）", showBackground = true, heightDp = 900)
@Composable
private fun PreviewRingFillBlank() {
    AppAlarmTheme {
        RingScreenContent(
            alarm = previewAlarm(TaskType.FILL_BLANK, taskCount = 3),
            task = RingTask.FillBlank(
                Question(
                    id = "preview-blank",
                    type = TaskType.FILL_BLANK,
                    prompt = "一年有多少个月？",
                    answers = listOf("12", "十二"),
                ),
            ),
            solvedCount = 1,
            totalTasks = 3,
            backPressed = true,
            onSolved = {},
        )
    }
}

@Preview(name = "响铃 · 手势解锁", showBackground = true, heightDp = 900)
@Composable
private fun PreviewRingGesture() {
    AppAlarmTheme {
        RingScreenContent(
            alarm = previewAlarm(TaskType.GESTURE),
            task = RingTask.Gesture(listOf(0, 3, 6, 7), allowReverse = false),
            solvedCount = 0,
            totalTasks = 1,
            backPressed = false,
            onSolved = {},
        )
    }
}

@Composable
private fun ChoiceTask(question: Question, onSolved: () -> Unit) {
    val multi = question.type == TaskType.MULTI_CHOICE
    var selected by remember(question.id) { mutableStateOf(emptySet<String>()) }
    var wrong by remember(question.id) { mutableStateOf(false) }

    fun submit() {
        if (AnswerChecker.checkChoice(question, selected)) onSolved() else wrong = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(question.prompt, style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (multi) "多选：全部选对才算通过" else "单选",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        question.options.forEach { option ->
            val isSelected = option in selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        wrong = false
                        selected = when {
                            !multi -> setOf(option)
                            isSelected -> selected - option
                            else -> selected + option
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Text(
                    text = option,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (wrong) {
            Text("答错了，再想想", color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { submit() },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("确认") }
    }
}

@Composable
private fun FillBlankTask(question: Question, onSolved: () -> Unit) {
    var input by remember(question.id) { mutableStateOf("") }
    var wrong by remember(question.id) { mutableStateOf(false) }

    fun submit() {
        if (AnswerChecker.checkFillBlank(question, input)) onSolved() else wrong = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(question.prompt, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                wrong = false
            },
            singleLine = true,
            label = { Text("你的答案") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (wrong) {
            Text("答错了，再想想", color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { submit() },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("确认") }
    }
}

@Composable
private fun ArithmeticTask(prompt: String, answer: Long, onSolved: () -> Unit) {
    var input by remember(prompt) { mutableStateOf("") }
    var wrong by remember(prompt) { mutableStateOf(false) }

    fun submit() {
        if (AnswerChecker.checkArithmetic(answer, input)) onSolved() else wrong = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "算一算",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(prompt, style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                wrong = false
            },
            singleLine = true,
            label = { Text("答案") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (wrong) {
            Text("算错了，再想想", color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { submit() },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("确认") }
    }
}

@Composable
private fun GestureTask(pattern: List<Int>, allowReverse: Boolean, onSolved: () -> Unit) {
    var wrong by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("画出你的解锁手势", style = MaterialTheme.typography.titleLarge)
        if (wrong) {
            Text("手势不对，再试一次", color = MaterialTheme.colorScheme.error)
        }
        PatternLock(
            modifier = Modifier.fillMaxWidth(0.85f),
            onPatternFinished = { attempted ->
                if (GesturePattern.matches(pattern, attempted, allowReverse)) {
                    wrong = false
                    onSolved()
                } else {
                    wrong = true
                }
            },
        )
    }
}
