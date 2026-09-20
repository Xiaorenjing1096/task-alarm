package com.appalarm.data

import com.appalarm.core.QuestionBankValidator
import com.appalarm.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置题库的回归测试。
 *
 * 这批内容是「数据」而不是「代码」，正因为如此更容易悄悄写错：答案打错一个字、
 * 选项和答案对不上，编译器完全不会发现，只有用户早上被闹钟吵醒、怎么也答不对的
 * 时候才会暴露。所以这里用代码把它钉死。
 */
class BuiltInQuestionsTest {

    @Test
    fun `the bank is not suspiciously small`() {
        assertTrue(
            "内置题库只有 ${BuiltInQuestions.ALL.size} 道题",
            BuiltInQuestions.ALL.size >= 30,
        )
    }

    @Test
    fun `every built-in question passes the same validation applied to imports`() {
        val result = QuestionBankValidator.validate(BuiltInQuestions.ALL)
        assertEquals("内置题库里有无效题目：${result.errors}", emptyList<String>(), result.errors)
    }

    @Test
    fun `ids are unique`() {
        val ids = BuiltInQuestions.ALL.map { it.id }
        assertEquals("内置题目有重复 id", ids.size, ids.distinct().size)
    }

    @Test
    fun `every question is flagged as built in`() {
        assertTrue(BuiltInQuestions.ALL.all { it.builtIn })
    }

    @Test
    fun `the bank contains only the three bank task types`() {
        val allowed = setOf(TaskType.SINGLE_CHOICE, TaskType.MULTI_CHOICE, TaskType.FILL_BLANK)
        BuiltInQuestions.ALL.forEach {
            assertTrue("${it.id} 的题型 ${it.type} 不该出现在题库里", it.type in allowed)
        }
    }

    @Test
    fun `each bank task type has enough questions to be useful`() {
        TaskType.entries.filter { it.usesQuestionBank }.forEach { type ->
            val count = BuiltInQuestions.ALL.count { it.type == type }
            assertTrue("${type.displayName} 只有 $count 道内置题，随机抽题会很单调", count >= 5)
        }
    }

    @Test
    fun `choice questions all offer four options`() {
        BuiltInQuestions.ALL
            .filter { it.type == TaskType.SINGLE_CHOICE || it.type == TaskType.MULTI_CHOICE }
            .forEach { question ->
                assertEquals("${question.id} 的选项数不是 4", 4, question.options.size)
            }
    }

    @Test
    fun `every choice answer can actually be selected`() {
        BuiltInQuestions.ALL
            .filter { it.type == TaskType.SINGLE_CHOICE || it.type == TaskType.MULTI_CHOICE }
            .forEach { question ->
                question.answers.forEach { answer ->
                    assertTrue(
                        "${question.id} 的答案「$answer」在选项里找不到",
                        answer in question.options,
                    )
                }
            }
    }

    @Test
    fun `difficulty is spread across the bank instead of being uniform`() {
        val levels = BuiltInQuestions.ALL.map { it.difficulty }.distinct()
        assertTrue("内置题目的难度只有 $levels", levels.size >= 2)
        assertTrue(BuiltInQuestions.ALL.all { it.difficulty in 1..3 })
    }

    @Test
    fun `no prompt is blank`() {
        BuiltInQuestions.ALL.forEach {
            assertTrue("${it.id} 的题干是空的", it.prompt.isNotBlank())
        }
    }
}
