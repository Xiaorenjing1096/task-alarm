package com.appalarm.core

import kotlin.random.Random

/** 一道运行时生成的计算题。 */
data class ArithmeticQuestion(val prompt: String, val answer: Long)

/**
 * 随机生成计算题。
 *
 * 计算题不放进题库：随机生成意味着同一个闹钟每天早上都不一样，用户没法
 * 「背答案」，比固定题库更能逼人清醒。
 *
 * 所有难度都保证结果非负、除法整除，避免出现让用户怀疑自己算错了的题。
 */
object ArithmeticGenerator {

    fun generate(difficulty: Int, random: Random = Random.Default): ArithmeticQuestion =
        when (difficulty.coerceIn(1, 3)) {
            1 -> easy(random)
            2 -> medium(random)
            else -> hard(random)
        }

    /** 难度 1：20 以内加减法。 */
    private fun easy(random: Random): ArithmeticQuestion {
        val a = random.nextInt(2, 21)
        val b = random.nextInt(1, 21)
        return if (a >= b && random.nextBoolean()) {
            ArithmeticQuestion("$a - $b = ?", (a - b).toLong())
        } else {
            ArithmeticQuestion("$a + $b = ?", (a + b).toLong())
        }
    }

    /** 难度 2：两位数加减、表内乘法、整除。 */
    private fun medium(random: Random): ArithmeticQuestion = when (random.nextInt(4)) {
        0 -> {
            val a = random.nextInt(11, 100)
            val b = random.nextInt(11, 100)
            ArithmeticQuestion("$a + $b = ?", (a + b).toLong())
        }

        1 -> {
            val a = random.nextInt(21, 100)
            val b = random.nextInt(1, a)
            ArithmeticQuestion("$a - $b = ?", (a - b).toLong())
        }

        2 -> {
            val a = random.nextInt(3, 10)
            val b = random.nextInt(3, 10)
            ArithmeticQuestion("$a × $b = ?", (a * b).toLong())
        }

        else -> {
            val b = random.nextInt(3, 10)
            val quotient = random.nextInt(3, 10)
            ArithmeticQuestion("${b * quotient} ÷ $b = ?", quotient.toLong())
        }
    }

    /** 难度 3：三步混合运算。 */
    private fun hard(random: Random): ArithmeticQuestion = when (random.nextInt(3)) {
        0 -> {
            val a = random.nextInt(3, 10)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(10, 60)
            ArithmeticQuestion("$a × $b + $c = ?", (a * b + c).toLong())
        }

        1 -> {
            // 先算 base 再把减数限制在 base 以内，保证结果为正。
            val a = random.nextInt(3, 10)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(2, 8)
            val base = (a + b) * c
            val d = random.nextInt(1, base)
            ArithmeticQuestion("($a + $b) × $c - $d = ?", (base - d).toLong())
        }

        else -> {
            val b = random.nextInt(3, 10)
            val quotient = random.nextInt(4, 15)
            val c = random.nextInt(2, 10)
            val d = random.nextInt(1, 40)
            ArithmeticQuestion(
                "${b * quotient} ÷ $b + $c × $d = ?",
                (quotient + c * d).toLong(),
            )
        }
    }
}
