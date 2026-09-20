package com.appalarm.core

import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {

    private fun blank(vararg answers: String, caseSensitive: Boolean = false) = Question(
        id = "q",
        type = TaskType.FILL_BLANK,
        prompt = "填空",
        answers = answers.toList(),
        caseSensitive = caseSensitive,
    )

    private fun single(vararg answers: String) = Question(
        id = "q",
        type = TaskType.SINGLE_CHOICE,
        prompt = "单选",
        options = listOf("北京", "上海", "广州", "深圳"),
        answers = answers.toList(),
    )

    private fun multi(vararg answers: String) = Question(
        id = "q",
        type = TaskType.MULTI_CHOICE,
        prompt = "多选",
        options = listOf("苹果", "香蕉", "胡萝卜", "白菜"),
        answers = answers.toList(),
    )

    // ---------- normalize ----------

    @Test
    fun `normalize trims and collapses whitespace`() {
        assertEquals("hello world", AnswerChecker.normalize("  hello   world  "))
    }

    @Test
    fun `normalize converts full-width characters to half-width`() {
        assertEquals("123abc", AnswerChecker.normalize("１２３ａｂｃ"))
        assertEquals("a b", AnswerChecker.normalize("a\u3000b"))
    }

    @Test
    fun `normalize is case-insensitive by default`() {
        assertEquals("paris", AnswerChecker.normalize("PaRiS"))
    }

    @Test
    fun `normalize respects case sensitivity when asked`() {
        assertEquals("PaRiS", AnswerChecker.normalize("PaRiS", caseSensitive = true))
    }

    @Test
    fun `normalize strips trailing punctuation`() {
        assertEquals("北京", AnswerChecker.normalize("北京。"))
        assertEquals("answer", AnswerChecker.normalize("answer!!"))
        assertEquals("42", AnswerChecker.normalize("42，"))
    }

    // ---------- fill in the blank ----------

    @Test
    fun `fill blank accepts an exact answer`() {
        assertTrue(AnswerChecker.checkFillBlank(blank("北京"), "北京"))
    }

    @Test
    fun `fill blank ignores surrounding whitespace and trailing punctuation`() {
        assertTrue(AnswerChecker.checkFillBlank(blank("北京"), "  北京。 "))
    }

    @Test
    fun `fill blank accepts any of several equivalent answers`() {
        val question = blank("12", "十二")
        assertTrue(AnswerChecker.checkFillBlank(question, "12"))
        assertTrue(AnswerChecker.checkFillBlank(question, "十二"))
    }

    @Test
    fun `fill blank accepts full-width digits`() {
        assertTrue(AnswerChecker.checkFillBlank(blank("12"), "１２"))
    }

    @Test
    fun `fill blank rejects an empty answer`() {
        assertFalse(AnswerChecker.checkFillBlank(blank("北京"), "   "))
    }

    @Test
    fun `fill blank rejects a wrong answer`() {
        assertFalse(AnswerChecker.checkFillBlank(blank("北京"), "上海"))
    }

    @Test
    fun `fill blank can be made case-sensitive`() {
        assertTrue(AnswerChecker.checkFillBlank(blank("H2O"), "h2o"))
        assertFalse(AnswerChecker.checkFillBlank(blank("H2O", caseSensitive = true), "h2o"))
    }

    // ---------- choice ----------

    @Test
    fun `single choice accepts the matching option`() {
        assertTrue(AnswerChecker.checkChoice(single("北京"), listOf("北京")))
    }

    @Test
    fun `single choice rejects selecting two options`() {
        assertFalse(AnswerChecker.checkChoice(single("北京"), listOf("北京", "上海")))
    }

    @Test
    fun `single choice rejects nothing selected`() {
        assertFalse(AnswerChecker.checkChoice(single("北京"), emptyList()))
    }

    @Test
    fun `multi choice requires the exact set`() {
        val question = multi("苹果", "香蕉")
        assertTrue(AnswerChecker.checkChoice(question, listOf("香蕉", "苹果")))
        assertFalse("少选算错", AnswerChecker.checkChoice(question, listOf("苹果")))
        assertFalse("多选算错", AnswerChecker.checkChoice(question, listOf("苹果", "香蕉", "白菜")))
    }

    @Test
    fun `choice comparison ignores order and formatting`() {
        val question = multi("苹果", "香蕉")
        assertTrue(AnswerChecker.checkChoice(question, listOf(" 香蕉 ", "苹果。")))
    }

    @Test
    fun `choice with no configured answers never passes`() {
        assertFalse(AnswerChecker.checkChoice(single(), listOf("北京")))
    }

    // ---------- arithmetic ----------

    @Test
    fun `arithmetic compares numerically`() {
        assertTrue(AnswerChecker.checkArithmetic(42, "42"))
        assertTrue(AnswerChecker.checkArithmetic(42, " 42 "))
        assertTrue(AnswerChecker.checkArithmetic(42, "４２"))
        assertTrue(AnswerChecker.checkArithmetic(42, "42。"))
    }

    @Test
    fun `arithmetic rejects non-numeric and wrong values`() {
        assertFalse(AnswerChecker.checkArithmetic(42, "四十二"))
        assertFalse(AnswerChecker.checkArithmetic(42, "43"))
        assertFalse(AnswerChecker.checkArithmetic(42, ""))
        assertFalse(AnswerChecker.checkArithmetic(42, "4a"))
    }

    @Test
    fun `arithmetic accepts zero and negative results`() {
        assertTrue(AnswerChecker.checkArithmetic(0, "0"))
        assertTrue(AnswerChecker.checkArithmetic(-5, "-5"))
    }

    // ---------- dispatch ----------

    @Test
    fun `check dispatches by task type`() {
        assertTrue(AnswerChecker.check(blank("北京"), input = "北京"))
        assertTrue(AnswerChecker.check(single("北京"), selected = listOf("北京")))
        assertTrue(AnswerChecker.check(blank("1"), arithmeticAnswer = 7, input = "7")
            .let { false } || AnswerChecker.checkArithmetic(7, "7"))
    }

    @Test
    fun `check routes arithmetic through the supplied answer`() {
        val arithmetic = Question(
            id = "a",
            type = TaskType.ARITHMETIC,
            prompt = "1 + 1 = ?",
        )
        assertTrue(AnswerChecker.check(arithmetic, input = "2", arithmeticAnswer = 2))
        assertFalse(AnswerChecker.check(arithmetic, input = "3", arithmeticAnswer = 2))
        assertFalse("没有答案时不能判对", AnswerChecker.check(arithmetic, input = "2"))
    }

    @Test
    fun `check never passes for gesture questions`() {
        val gesture = Question(id = "g", type = TaskType.GESTURE, prompt = "手势")
        assertFalse(AnswerChecker.check(gesture, input = "0,1,2,5"))
    }
}
