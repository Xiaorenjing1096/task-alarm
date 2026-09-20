package com.appalarm.core

import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType

/**
 * 校验从外部导入的题目。
 *
 * 导入的文件是用户手改过的，什么都有可能：选项和答案对不上、选择题没有选项、
 * 把计算题写进题库、id 重复…… 这些如果放进去，就会变成「闹钟响起来却永远
 * 关不掉」，所以必须在这里拦掉，并且把原因清楚地报给用户。
 */
object QuestionBankValidator {

    /** 校验结果：能用的题目 + 每一道被拒绝的题目的原因。 */
    data class Result(val accepted: List<Question>, val errors: List<String>)

    fun validate(incoming: List<Question>): Result {
        val accepted = mutableListOf<Question>()
        val errors = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()

        incoming.forEachIndexed { index, question ->
            val label = question.prompt.take(24).ifBlank { "第 ${index + 1} 条" }

            if (question.prompt.isBlank()) {
                errors.append("$label：题干不能为空")
                return@forEachIndexed
            }
            if (question.type == TaskType.ARITHMETIC || question.type == TaskType.GESTURE) {
                errors.append("$label：题库只支持单选、多选和填空题")
                return@forEachIndexed
            }
            if (question.answers.isEmpty() || question.answers.all { it.isBlank() }) {
                errors.append("$label：没有配置答案")
                return@forEachIndexed
            }
            if (question.answers.any { it.isBlank() }) {
                errors.append("$label：答案里有空白项")
                return@forEachIndexed
            }

            if (question.type == TaskType.SINGLE_CHOICE || question.type == TaskType.MULTI_CHOICE) {
                if (question.options.size < 2) {
                    errors.append("$label：选择题至少需要 2 个选项")
                    return@forEachIndexed
                }
                if (question.options.any { it.isBlank() }) {
                    errors.append("$label：选项里有空白项")
                    return@forEachIndexed
                }
                val normalizedOptions = question.options.map { AnswerChecker.normalize(it) }.toSet()
                val orphan = question.answers.firstOrNull {
                    AnswerChecker.normalize(it) !in normalizedOptions
                }
                if (orphan != null) {
                    errors.append("$label：答案「$orphan」不在选项里")
                    return@forEachIndexed
                }
                if (question.type == TaskType.SINGLE_CHOICE && question.answers.size != 1) {
                    errors.append("$label：单选题只能有 1 个答案，这里有 ${question.answers.size} 个")
                    return@forEachIndexed
                }
                if (question.type == TaskType.MULTI_CHOICE && question.answers.size < 2) {
                    errors.append("$label：多选题至少需要 2 个答案")
                    return@forEachIndexed
                }
                if (question.options.distinct().size != question.options.size) {
                    errors.append("$label：选项有重复")
                    return@forEachIndexed
                }
            }

            if (!seenIds.add(question.id)) {
                errors.append("$label：id「${question.id}」重复，后一条已跳过")
                return@forEachIndexed
            }

            // 填空题的 options 没有意义，统一清掉，保证落库的数据是自洽的。
            accepted += if (question.type == TaskType.FILL_BLANK) {
                question.copy(options = emptyList())
            } else {
                question
            }
        }

        return Result(accepted, errors)
    }

    private fun MutableList<String>.append(message: String) = add(message)
}
