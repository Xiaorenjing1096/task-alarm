package com.appalarm.ui.bank

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.appalarm.core.QuestionBankValidator
import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import java.util.UUID

/**
 * 题库管理：查看、筛选、增删改，以及 JSON 导入导出。
 *
 * 导出用的是用户能直接读写的 JSON（而不是序列化后的二进制/数据库），因为这个需求
 * 的本质是「让用户能自己批量整理题目」—— 用 Excel 或文本编辑器改完再导回来，
 * 比在手机上一条条敲快得多。
 */
@Composable
fun QuestionBankScreen(
    onEditQuestion: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = context.appRepository

    var typeFilter by remember { mutableStateOf<TaskType?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    val visible = repository.questions.filter { typeFilter == null || it.type == typeFilter }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(repository.exportBankText().toByteArray(Charsets.UTF_8))
            } ?: error("无法写入所选位置")
        }
            .onSuccess { dialogMessage = "已导出 ${repository.questions.size} 道题。" }
            .onFailure { dialogMessage = "导出失败：${it.message}" }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("无法读取该文件")
        }
            .mapCatching { text -> repository.parseBankText(text).getOrThrow() }
            .onSuccess { questions ->
                val outcome = repository.importQuestions(questions)
                dialogMessage = buildString {
                    append("新增 ${outcome.added} 道")
                    if (outcome.replaced > 0) append("，覆盖 ${outcome.replaced} 道")
                    if (outcome.rejected.isNotEmpty()) {
                        append("，跳过 ${outcome.rejected.size} 道：\n")
                        append(outcome.rejected.take(6).joinToString("\n") { "· $it" })
                        if (outcome.rejected.size > 6) append("\n· …")
                    }
                }
            }
            .onFailure { dialogMessage = "导入失败：${it.message}" }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditQuestion(null) }) {
                Icon(Icons.Default.Add, contentDescription = "新建题目")
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Spacer(Modifier.weight(1f))
                Text("题库", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { exportLauncher.launch("appalarm-questions.json") }) {
                    Text("导出")
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                    Text("导入")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterButton("全部", typeFilter == null) { typeFilter = null }
                FilterButton("单选", typeFilter == TaskType.SINGLE_CHOICE) {
                    typeFilter = TaskType.SINGLE_CHOICE
                }
                FilterButton("多选", typeFilter == TaskType.MULTI_CHOICE) {
                    typeFilter = TaskType.MULTI_CHOICE
                }
                FilterButton("填空", typeFilter == TaskType.FILL_BLANK) {
                    typeFilter = TaskType.FILL_BLANK
                }
            }

            Spacer(Modifier.height(8.dp))

            if (visible.isEmpty()) {
                Column(Modifier.padding(24.dp)) {
                    Text("这里还没有题目。", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "可以用右下角的按钮新增，也可以点右上角「导入」批量导入 JSON。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { question ->
                        QuestionRow(
                            question = question,
                            onEdit = { onEditQuestion(question.id) },
                            onDelete = { repository.deleteQuestion(question.id) },
                        )
                    }
                }
            }
        }
    }

    dialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            title = { Text("题库") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun QuestionRow(question: Question, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(question.prompt, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append(question.type.displayName)
                        append(" · 难度 ")
                        append(question.difficulty)
                        if (question.builtIn) append(" · 内置")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

/**
 * 题目编辑页。
 *
 * 选择题的答案用「勾选选项」而不是「再手打一遍答案」来收集 —— 手打的话，
 * 答案和选项差一个字就会永远判错，而且用户在响铃时才会发现。
 */
@Composable
fun QuestionEditScreen(questionId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = context.appRepository
    val existing = questionId?.let { repository.questionById(it) }

    var type by remember { mutableStateOf(existing?.type ?: TaskType.SINGLE_CHOICE) }
    var prompt by remember { mutableStateOf(existing?.prompt ?: "") }
    var options by remember {
        mutableStateOf(existing?.options?.takeIf { it.isNotEmpty() } ?: listOf("", "", "", ""))
    }
    var selectedIndices by remember {
        mutableStateOf(
            existing?.let { question ->
                question.options.mapIndexedNotNull { index, option ->
                    index.takeIf { option in question.answers }
                }.toSet()
            } ?: emptySet(),
        )
    }
    var blankAnswersText by remember {
        mutableStateOf(
            existing?.takeIf { it.type == TaskType.FILL_BLANK }
                ?.answers?.joinToString("\n") ?: "",
        )
    }
    var difficulty by remember { mutableStateOf(existing?.difficulty ?: 1) }
    var caseSensitive by remember { mutableStateOf(existing?.caseSensitive ?: false) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    val isChoice = type == TaskType.SINGLE_CHOICE || type == TaskType.MULTI_CHOICE

    fun save() {
        val answers = if (isChoice) {
            selectedIndices.sorted().mapNotNull { options.getOrNull(it) }
        } else {
            blankAnswersText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        }

        val candidate = Question(
            id = existing?.id ?: "user-${UUID.randomUUID()}",
            type = type,
            prompt = prompt.trim(),
            options = if (isChoice) options.map { it.trim() } else emptyList(),
            answers = answers,
            caseSensitive = if (isChoice) false else caseSensitive,
            difficulty = difficulty,
            tags = emptyList(),
            builtIn = existing?.builtIn ?: false,
        )

        val result = QuestionBankValidator.validate(listOf(candidate))
        if (result.accepted.isEmpty()) {
            errors = result.errors.ifEmpty { listOf("这道题还不能保存") }
            return
        }
        repository.upsertQuestion(result.accepted.first())
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (existing == null) "新建题目" else "编辑题目",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(48.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton("单选", type == TaskType.SINGLE_CHOICE) { type = TaskType.SINGLE_CHOICE }
            FilterButton("多选", type == TaskType.MULTI_CHOICE) { type = TaskType.MULTI_CHOICE }
            FilterButton("填空", type == TaskType.FILL_BLANK) { type = TaskType.FILL_BLANK }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("题干") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (isChoice) {
            Text("选项（至少 2 个）", style = MaterialTheme.typography.titleSmall)
            options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(                        checked = index in selectedIndices,
                        onCheckedChange = { checked ->
                            selectedIndices = if (checked) {
                                if (type == TaskType.SINGLE_CHOICE) setOf(index)
                                else selectedIndices + index
                            } else {
                                selectedIndices - index
                            }
                        },
                    )
                    OutlinedTextField(
                        value = option,
                        onValueChange = { newValue ->
                            options = options.toMutableList().also { it[index] = newValue }
                        },
                        label = { Text("选项 ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (options.size > 2) {
                        IconButton(
                            onClick = {
                                options = options.toMutableList().also { it.removeAt(index) }
                                selectedIndices = selectedIndices
                                    .filter { it != index }
                                    .map { if (it > index) it - 1 else it }
                                    .toSet()
                            },
                        ) { Icon(Icons.Default.Delete, contentDescription = "删除选项") }
                    }
                }
            }
            OutlinedButton(
                onClick = { options = options + "" },
                enabled = options.size < 8,
            ) { Text("加一个选项") }
            Text(
                text = "勾选左边的方框表示正确答案" +
                    if (type == TaskType.MULTI_CHOICE) "（多选题至少要勾 2 个）" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedTextField(
                value = blankAnswersText,
                onValueChange = { blankAnswersText = it },
                label = { Text("可接受的答案（一行一个）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
            )
            Text(
                text = "同一个答案的不同写法都写进来，例如「12」和「十二」。判题时会忽略首尾空格、大小写和结尾标点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("区分大小写", Modifier.weight(1f))
                Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("难度", Modifier.weight(1f))
            OutlinedButton(
                onClick = { if (difficulty > 1) difficulty-- },
                enabled = difficulty > 1,
            ) { Text("−") }
            Text("$difficulty", Modifier.padding(horizontal = 12.dp))
            OutlinedButton(
                onClick = { if (difficulty < 3) difficulty++ },
                enabled = difficulty < 3,
            ) { Text("+") }
        }

        if (errors.isNotEmpty()) {
            Column {
                errors.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        Spacer(Modifier.height(16.dp))
    }
}
