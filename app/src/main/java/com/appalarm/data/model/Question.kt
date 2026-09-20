package com.appalarm.data.model

import kotlinx.serialization.Serializable

/** 题库导出文件的格式版本。导入时用来判断能不能读懂。 */
const val QUESTION_BANK_SCHEMA_VERSION = 1

/** 导出文件里的应用标记，用来识别文件来源。 */
const val QUESTION_BANK_APP_TAG = "AppAlarm"

/**
 * 一道题。
 *
 * [answers] 存的是**答案原文而不是选项下标**，这样导出的 JSON 用文本编辑器
 * 就能看懂和修改，选项顺序调整后也不会失效。选择题的每个答案都必须与
 * [options] 中的某一项完全一致。
 */
@Serializable
data class Question(
    val id: String,
    val type: TaskType,
    val prompt: String,
    /** 仅选择题使用。 */
    val options: List<String> = emptyList(),
    /** 可接受的答案；填空题可以列多个等价写法。 */
    val answers: List<String> = emptyList(),
    /** 仅填空题关心；默认忽略大小写。 */
    val caseSensitive: Boolean = false,
    val difficulty: Int = 1,
    val tags: List<String> = emptyList(),
    /** 内置题目不能删除，但可以停用。 */
    val builtIn: Boolean = false,
)

/**
 * 题库导入/导出文件。
 *
 * 用 [Json] 解析时会开启 ignoreUnknownKeys，所以后续加字段不会让旧版本导入失败。
 */
@Serializable
data class QuestionBankFile(
    val schemaVersion: Int = QUESTION_BANK_SCHEMA_VERSION,
    val app: String = QUESTION_BANK_APP_TAG,
    /** ISO-8601 时间戳，仅作记录。 */
    val exportedAt: String = "",
    /** 给手动编辑这个文件的人看的说明。 */
    val note: String = DEFAULT_NOTE,
    val questions: List<Question> = emptyList(),
) {
    companion object {
        const val DEFAULT_NOTE: String =
            "此文件由「任务闹钟」导出，可直接用文本编辑器增删改后重新导入。" +
                "answers 是可接受答案列表；选择题的答案必须与 options 中某一项完全一致。" +
                "type 取值为 SINGLE_CHOICE / MULTI_CHOICE / FILL_BLANK。"
    }
}
