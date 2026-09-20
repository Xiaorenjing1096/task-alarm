package com.appalarm.core

import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType

/**
 * 判题。
 *
 * 判题宽严的分寸很重要：判太严，用户明明答对了却关不掉闹钟，体验立刻崩掉；
 * 判太松，随便乱按就能关，闹钟就失去意义了。
 *
 * 这里的取舍是：**输入格式一律宽容**（全角半角、大小写、首尾空白、结尾标点
 * 都不计较），**语义必须准确**（选择题多选少选都算错）。
 */
object AnswerChecker {

    private val WHITESPACE = Regex("\\s+")

    private val TRAILING_PUNCTUATION = charArrayOf(
        '.', '。', ',', '，', '!', '！', '?', '？', ';', '；', ':', '：',
    )

    /**
     * 规范化用户输入：全角转半角 → 去掉首尾空白 → 连续空白压成一个空格 →
     * 去掉结尾的标点 → 按需转小写。
     */
    fun normalize(raw: String, caseSensitive: Boolean = false): String {
        val halfWidth = buildString(raw.length) {
            for (ch in raw) append(toHalfWidth(ch))
        }
        var text = halfWidth.trim().replace(WHITESPACE, " ")
        text = text.trimEnd(*TRAILING_PUNCTUATION)
        return if (caseSensitive) text else text.lowercase()
    }

    private fun toHalfWidth(ch: Char): Char = when (ch) {
        '\u3000' -> ' ' // 全角空格
        in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar() // 全角 ASCII
        else -> ch
    }

    /** 填空题：命中任意一个可接受答案即算对。 */
    fun checkFillBlank(question: Question, input: String): Boolean {
        val normalizedInput = normalize(input, question.caseSensitive)
        if (normalizedInput.isEmpty()) return false
        return question.answers.any {
            normalize(it, question.caseSensitive) == normalizedInput
        }
    }

    /**
     * 选择题：所选集合必须与正确答案集合完全一致。
     * 单选题也走这里，多选或少选都判错。
     */
    fun checkChoice(question: Question, selected: Collection<String>): Boolean {
        if (selected.isEmpty()) return false
        val caseSensitive = question.caseSensitive
        val expected = question.answers.map { normalize(it, caseSensitive) }.toSet()
        val actual = selected.map { normalize(it, caseSensitive) }.toSet()
        return expected.isNotEmpty() && expected == actual
    }

    /** 计算题：比对数值，输入支持全角数字。 */
    fun checkArithmetic(expected: Long, input: String): Boolean {
        val text = normalize(input)
        if (text.isEmpty()) return false
        return text.toLongOrNull() == expected
    }

    /**
     * 按题型分派。
     *
     * 计算题的正确答案是生成时拿到的（题库里没有），所以由调用方通过
     * [arithmeticAnswer] 传进来；手势题不走这里，见 [GesturePattern.matches]。
     */
    fun check(
        question: Question,
        input: String = "",
        selected: Collection<String> = emptyList(),
        arithmeticAnswer: Long? = null,
    ): Boolean = when (question.type) {
        TaskType.SINGLE_CHOICE, TaskType.MULTI_CHOICE -> checkChoice(question, selected)
        TaskType.FILL_BLANK -> checkFillBlank(question, input)
        TaskType.ARITHMETIC -> arithmeticAnswer?.let { checkArithmetic(it, input) } ?: false
        TaskType.GESTURE -> false
    }
}
