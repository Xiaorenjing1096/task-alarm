package com.appalarm.core

import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import kotlin.random.Random

/** 响铃时要在屏幕上呈现的一道题。 */
sealed interface RingTask {
    /** 单选或多选，由 `question.type` 区分。 */
    data class Choice(val question: Question) : RingTask

    data class FillBlank(val question: Question) : RingTask

    data class Arithmetic(val prompt: String, val answer: Long) : RingTask

    data class Gesture(val pattern: List<Int>, val allowReverse: Boolean) : RingTask
}

/**
 * 决定响铃时出哪道题。
 *
 * 这个类最重要的一条规则是：**永远不能造出一个解不开的题目**。题库空了、题目被
 * 删了、手势没录或者坏了 —— 任何一个环节出问题，都不能让用户面对一个无法完成
 * 因而无法关闭的闹钟。所以任何异常情况一律回退到随机计算题。
 */
object RingTaskFactory {

    fun create(
        alarm: Alarm,
        bank: List<Question>,
        allowReverseGesture: Boolean = false,
        random: Random = Random.Default,
    ): RingTask = build(alarm, bank, allowReverseGesture, random)
        ?: arithmetic(alarm, random)

    private fun build(
        alarm: Alarm,
        bank: List<Question>,
        allowReverseGesture: Boolean,
        random: Random,
    ): RingTask? = when (alarm.taskType) {
        TaskType.ARITHMETIC -> arithmetic(alarm, random)

        TaskType.GESTURE -> {
            val pattern = GesturePattern.decode(alarm.gesturePattern)
            if (GesturePattern.isValid(pattern)) {
                RingTask.Gesture(pattern, allowReverseGesture)
            } else {
                null // 手势没录或录坏了，交给兜底
            }
        }

        TaskType.SINGLE_CHOICE, TaskType.MULTI_CHOICE, TaskType.FILL_BLANK -> {
            val question = pickFromBank(alarm, bank, random) ?: return null
            if (question.type == TaskType.FILL_BLANK) {
                RingTask.FillBlank(question)
            } else {
                RingTask.Choice(question)
            }
        }
    }

    /**
     * 从题库里挑一道题。
     *
     * 闹钟指定了 [Alarm.questionId] 就优先用它（考试、单词卡这类固定场景），
     * 找不到就退回随机抽 —— 指定题被删掉时不该让闹钟失效。
     */
    fun pickFromBank(
        alarm: Alarm,
        bank: List<Question>,
        random: Random = Random.Default,
    ): Question? {
        alarm.questionId?.let { preferredId ->
            bank.firstOrNull { it.id == preferredId && it.type == alarm.taskType }?.let { return it }
        }
        val pool = bank.filter { it.type == alarm.taskType }
        if (pool.isEmpty()) return null
        return pool[random.nextInt(pool.size)]
    }

    private fun arithmetic(alarm: Alarm, random: Random): RingTask.Arithmetic {
        val question = ArithmeticGenerator.generate(alarm.arithmeticDifficulty, random)
        return RingTask.Arithmetic(question.prompt, question.answer)
    }
}
