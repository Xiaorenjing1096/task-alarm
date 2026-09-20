package com.appalarm.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appalarm.core.QuestionBankValidator
import com.appalarm.data.model.Alarm
import com.appalarm.data.model.AppSettings
import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 应用唯一的数据入口。
 *
 * 数据量很小（几十个闹钟、几百道题），所以没有引入 Room：三个 JSON 文件就够，
 * 而且「导出题库」这个需求本来就要序列化，用同一套 kotlinx.serialization 模型
 * 能省掉一整层转换和 Room 的 schema 迁移机制。
 *
 * 暴露给 Compose 的是 [mutableStateListOf]，读取即自动订阅变更。
 */
class AppRepository(context: Context) {

    private val filesDir = context.applicationContext.filesDir

    private val alarmStore = JsonStore(File(filesDir, FILE_ALARMS), AppJson)
    private val questionStore = JsonStore(File(filesDir, FILE_QUESTIONS), AppJson)
    private val settingsStore = JsonStore(File(filesDir, FILE_SETTINGS), AppJson)

    private val alarmListSerializer = ListSerializer(Alarm.serializer())
    private val questionListSerializer = ListSerializer(Question.serializer())

    private val _alarms = mutableStateListOf<Alarm>()
    val alarms: List<Alarm> get() = _alarms

    private val _questions = mutableStateListOf<Question>()
    val questions: List<Question> get() = _questions

    var settings: AppSettings by mutableStateOf(AppSettings())
        private set

    // ---------------------------------------------------------------- 加载

    fun load() {
        settings = settingsStore.read(AppSettings.serializer(), AppSettings())

        val loadedAlarms = alarmStore.read(alarmListSerializer, emptyList())
        _alarms.clear()
        _alarms.addAll(sorted(loadedAlarms))

        _questions.clear()
        if (!settings.questionsSeeded) {
            // 首次启动：灌入内置题库。之后用户怎么改都不会被覆盖。
            _questions.addAll(BuiltInQuestions.ALL)
            settings = settings.copy(questionsSeeded = true)
            persistQuestions()
            persistSettings()
        } else {
            _questions.addAll(questionStore.read(questionListSerializer, emptyList()))
        }
    }

    // ---------------------------------------------------------------- 闹钟

    fun alarmById(id: String): Alarm? = _alarms.firstOrNull { it.id == id }

    fun upsertAlarm(alarm: Alarm) {
        val index = _alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) _alarms[index] = alarm else _alarms.add(alarm)
        applyAlarmOrder()
        persistAlarms()
    }

    fun deleteAlarm(id: String) {
        if (_alarms.removeAll { it.id == id }) persistAlarms()
    }

    fun setAlarmEnabled(id: String, enabled: Boolean) {
        val index = _alarms.indexOfFirst { it.id == id }
        if (index < 0) return
        _alarms[index] = _alarms[index].copy(enabled = enabled)
        persistAlarms()
    }

    /** 按设置的默认值造一个新闹钟（尚未保存）。 */
    fun newAlarm(): Alarm = Alarm(
        id = UUID.randomUUID().toString(),
        hour = 7,
        minute = 0,
        taskType = settings.defaultTaskType,
        arithmeticDifficulty = settings.defaultArithmeticDifficulty,
        taskCount = settings.defaultTaskCount,
        createdAt = System.currentTimeMillis(),
    )

    // ---------------------------------------------------------------- 题库

    fun questionsOfType(type: TaskType): List<Question> = _questions.filter { it.type == type }

    fun questionById(id: String): Question? = _questions.firstOrNull { it.id == id }

    fun upsertQuestion(question: Question) {
        val index = _questions.indexOfFirst { it.id == question.id }
        if (index >= 0) _questions[index] = question else _questions.add(question)
        persistQuestions()
    }

    fun deleteQuestion(id: String) {
        if (_questions.removeAll { it.id == id }) persistQuestions()
    }

    /** 把缺失的内置题目补回来，返回补回的数量。 */
    fun restoreBuiltInQuestions(): Int {
        val existingIds = _questions.map { it.id }.toSet()
        val missing = BuiltInQuestions.ALL.filterNot { it.id in existingIds }
        if (missing.isNotEmpty()) {
            _questions.addAll(missing)
            persistQuestions()
        }
        return missing.size
    }

    /** 导入结果，用于给用户一个明确的反馈。 */
    data class ImportOutcome(
        val added: Int,
        val replaced: Int,
        val rejected: List<String>,
    ) {
        val imported: Int get() = added + replaced
    }

    fun importQuestions(incoming: List<Question>): ImportOutcome {
        val validation = QuestionBankValidator.validate(incoming)
        var added = 0
        var replaced = 0

        validation.accepted.forEach { question ->
            val index = _questions.indexOfFirst { it.id == question.id }
            if (index >= 0) {
                // 内置题目的身份不能被导入文件改掉，否则「恢复默认题库」会失效。
                _questions[index] = question.copy(builtIn = _questions[index].builtIn)
                replaced++
            } else {
                _questions.add(question)
                added++
            }
        }
        if (added > 0 || replaced > 0) persistQuestions()
        return ImportOutcome(added, replaced, validation.errors)
    }

    /** 生成可读、可手改的题库 JSON。 */
    fun exportBankText(): String =
        QuestionBankCodec.encode(_questions.toList(), ZonedDateTime.now().toString())

    /** 解析导入文件；失败时 [Result] 里带着可以直接展示给用户的原因。 */
    fun parseBankText(text: String): Result<List<Question>> = QuestionBankCodec.decode(text)

    // ---------------------------------------------------------------- 设置

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        persistSettings()
    }

    // ---------------------------------------------------------------- 落盘

    private fun sorted(source: List<Alarm>): List<Alarm> =
        source.sortedWith(compareBy({ it.hour * 60 + it.minute }, { it.createdAt }))

    private fun applyAlarmOrder() {
        val ordered = sorted(_alarms.toList())
        _alarms.clear()
        _alarms.addAll(ordered)
    }

    private fun persistAlarms() = alarmStore.write(alarmListSerializer, _alarms.toList())

    private fun persistQuestions() = questionStore.write(questionListSerializer, _questions.toList())

    private fun persistSettings() = settingsStore.write(AppSettings.serializer(), settings)

    private companion object {
        const val FILE_ALARMS = "alarms.json"
        const val FILE_QUESTIONS = "questions.json"
        const val FILE_SETTINGS = "settings.json"
    }
}
