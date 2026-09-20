package com.appalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArithmeticGeneratorTest {

    /**
     * 独立的题干求值器。
     *
     * 用它来验证生成器给的答案确实等于题干算出来的值 —— 如果只检查「答案非负」，
     * 一个把 `a - b` 写成 `a + b` 的 bug 是发现不了的。
     */
    private fun evaluate(prompt: String): Long {
        val body = prompt.removeSuffix(" = ?")

        // (a + b) × c - d
        Regex("""\((\d+) \+ (\d+)\) × (\d+) - (\d+)""").matchEntire(body)?.let { match ->
            val (a, b, c, d) = match.destructured
            return (a.toLong() + b.toLong()) * c.toLong() - d.toLong()
        }
        // a ÷ b + c × d
        Regex("""(\d+) ÷ (\d+) \+ (\d+) × (\d+)""").matchEntire(body)?.let { match ->
            val (a, b, c, d) = match.destructured
            val dividend = a.toLong()
            val divisor = b.toLong()
            assertTrue("除法必须整除，题干：$body", dividend % divisor == 0L)
            return dividend / divisor + c.toLong() * d.toLong()
        }
        // a × b + c
        Regex("""(\d+) × (\d+) \+ (\d+)""").matchEntire(body)?.let { match ->
            val (a, b, c) = match.destructured
            return a.toLong() * b.toLong() + c.toLong()
        }
        // a <op> b
        Regex("""(\d+) (\D) (\d+)""").matchEntire(body)?.let { match ->
            val (a, op, b) = match.destructured
            val left = a.toLong()
            val right = b.toLong()
            return when (op.trim()) {
                "+" -> left + right
                "-" -> left - right
                "×" -> left * right
                "÷" -> {
                    assertTrue("除法必须整除，题干：$body", left % right == 0L)
                    left / right
                }

                else -> throw AssertionError("未知运算符 '$op'，题干：$body")
            }
        }
        throw AssertionError("无法解析的题干：$prompt")
    }

    @Test
    fun `every generated question is self-consistent and non-negative`() {
        for (difficulty in 1..3) {
            val random = Random(difficulty * 7919)
            repeat(400) { index ->
                val question = ArithmeticGenerator.generate(difficulty, random)
                assertTrue(
                    "难度 $difficulty 第 $index 题的答案是负数：${question.prompt}",
                    question.answer >= 0,
                )
                assertTrue("题干格式不对：${question.prompt}", question.prompt.endsWith(" = ?"))
                assertEquals(
                    "难度 $difficulty 第 $index 题的答案与题干不符：${question.prompt}",
                    evaluate(question.prompt),
                    question.answer,
                )
            }
        }
    }

    @Test
    fun `difficulty one stays within the twenty range`() {
        val random = Random(1234)
        repeat(300) {
            val question = ArithmeticGenerator.generate(1, random)
            val body = question.prompt.removeSuffix(" = ?")
            val operands = Regex("""\d+""").findAll(body).map { it.value.toInt() }.toList()
            assertTrue("难度 1 的操作数应不超过 20：$body", operands.all { it <= 20 })
        }
    }

    @Test
    fun `difficulty is clamped instead of throwing`() {
        val tooLow = ArithmeticGenerator.generate(0, Random(1))
        val tooHigh = ArithmeticGenerator.generate(99, Random(1))
        assertTrue(tooLow.answer >= 0)
        assertTrue(tooHigh.answer >= 0)
        assertEquals("难度 0 应当等同难度 1", evaluate(tooLow.prompt), tooLow.answer)
        assertEquals("难度 99 应当等同难度 3", evaluate(tooHigh.prompt), tooHigh.answer)
    }

    @Test
    fun `the same seed reproduces the same sequence`() {
        val first = List(20) { ArithmeticGenerator.generate(3, Random(42)) }
        val second = List(20) { ArithmeticGenerator.generate(3, Random(42)) }
        assertEquals(first, second)
    }

    @Test
    fun `the generator does not get stuck on one shape`() {
        // 同难度下应当出现多种题干，否则「随机」是假的。
        val random = Random(2026)
        val prompts = List(200) { ArithmeticGenerator.generate(2, random).prompt }
        assertTrue("难度 2 的题干过于单一", prompts.distinct().size > 20)
    }
}
