package com.appalarm.core

import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBankValidatorTest {

    private fun single(
        id: String = "s1",
        prompt: String = "中国的首都是？",
        options: List<String> = listOf("北京", "上海"),
        answers: List<String> = listOf("北京"),
    ) = Question(
        id = id,
        type = TaskType.SINGLE_CHOICE,
        prompt = prompt,
        options = options,
        answers = answers,
    )

    private fun multi(
        id: String = "m1",
        options: List<String> = listOf("苹果", "香蕉", "白菜"),
        answers: List<String> = listOf("苹果", "香蕉"),
    ) = Question(
        id = id,
        type = TaskType.MULTI_CHOICE,
        prompt = "哪些是水果？",
        options = options,
        answers = answers,
    )

    private fun blank(
        id: String = "b1",
        answers: List<String> = listOf("13"),
        options: List<String> = emptyList(),
    ) = Question(
        id = id,
        type = TaskType.FILL_BLANK,
        prompt = "12 + 1 = ?",
        options = options,
        answers = answers,
    )

    @Test
    fun `a well formed single choice question is accepted`() {
        val result = QuestionBankValidator.validate(listOf(single()))
        assertEquals(1, result.accepted.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `an answer that is not among the options is rejected`() {
        val result = QuestionBankValidator.validate(listOf(single(answers = listOf("广州"))))
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.single().contains("广州"))
    }

    @Test
    fun `a single choice question with two answers is rejected`() {
        val result = QuestionBankValidator.validate(
            listOf(single(answers = listOf("北京", "上海"))),
        )
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.single().contains("单选题"))
    }

    @Test
    fun `a multi choice question with only one answer is rejected`() {
        val result = QuestionBankValidator.validate(listOf(multi(answers = listOf("苹果"))))
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.single().contains("多选题"))
    }

    @Test
    fun `a choice question with fewer than two options is rejected`() {
        val result = QuestionBankValidator.validate(
            listOf(single(options = listOf("北京"), answers = listOf("北京"))),
        )
        assertTrue(result.accepted.isEmpty())
    }

    @Test
    fun `duplicate options are rejected`() {
        val result = QuestionBankValidator.validate(
            listOf(single(options = listOf("北京", "北京"), answers = listOf("北京"))),
        )
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.single().contains("重复"))
    }

    @Test
    fun `a question with no answers is rejected`() {
        val result = QuestionBankValidator.validate(listOf(blank(answers = emptyList())))
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.single().contains("答案"))
    }

    @Test
    fun `a blank prompt is rejected`() {
        val result = QuestionBankValidator.validate(listOf(blank().copy(prompt = "   ")))
        assertTrue(result.accepted.isEmpty())
    }

    @Test
    fun `arithmetic questions are not allowed in the bank`() {
        val arithmetic = Question(
            id = "x",
            type = TaskType.ARITHMETIC,
            prompt = "1 + 1",
            answers = listOf("2"),
        )
        val result = QuestionBankValidator.validate(listOf(arithmetic))
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.single().contains("题库只支持"))
    }

    @Test
    fun `gesture questions are not allowed in the bank`() {
        val gesture = Question(
            id = "x",
            type = TaskType.GESTURE,
            prompt = "手势",
            answers = listOf("0,1,2,5"),
        )
        assertTrue(QuestionBankValidator.validate(listOf(gesture)).accepted.isEmpty())
    }

    @Test
    fun `duplicate ids are rejected`() {
        val result = QuestionBankValidator.validate(listOf(single(id = "dup"), single(id = "dup")))
        assertEquals(1, result.accepted.size)
        assertTrue(result.errors.single().contains("dup"))
    }

    @Test
    fun `fill blank questions accept several equivalent answers`() {
        val result = QuestionBankValidator.validate(listOf(blank(answers = listOf("13", "十三"))))
        assertEquals(1, result.accepted.size)
        assertEquals(listOf("13", "十三"), result.accepted.single().answers)
    }

    @Test
    fun `fill blank questions have their options stripped`() {
        // 填空题的 options 没有意义，落库前统一清掉。
        val result = QuestionBankValidator.validate(
            listOf(blank(options = listOf("没用的选项"))),
        )
        assertTrue(result.accepted.single().options.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `answers are matched ignoring formatting differences`() {
        // 全角「Ａ」和半角「A」视为同一个选项。
        val result = QuestionBankValidator.validate(
            listOf(single(options = listOf("A", "B"), answers = listOf("Ａ"))),
        )
        assertEquals(1, result.accepted.size)
    }

    @Test
    fun `a blank answer entry is rejected`() {
        val result = QuestionBankValidator.validate(listOf(blank(answers = listOf("13", "  "))))
        assertTrue(result.accepted.isEmpty())
    }

    @Test
    fun `valid questions survive alongside invalid ones`() {
        val result = QuestionBankValidator.validate(
            listOf(
                single(id = "ok"),
                blank(id = "bad", answers = emptyList()),
                multi(id = "ok2"),
            ),
        )
        assertEquals(2, result.accepted.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `an empty import is accepted and reports nothing`() {
        val result = QuestionBankValidator.validate(emptyList())
        assertTrue(result.accepted.isEmpty())
        assertTrue(result.errors.isEmpty())
    }
}
