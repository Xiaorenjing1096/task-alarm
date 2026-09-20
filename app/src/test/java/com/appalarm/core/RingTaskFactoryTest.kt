package com.appalarm.core

import com.appalarm.data.model.Alarm
import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RingTaskFactoryTest {

    private fun alarm(
        type: TaskType,
        difficulty: Int = 2,
        questionId: String? = null,
        gesture: String = "",
    ) = Alarm(
        id = "a",
        hour = 7,
        minute = 0,
        taskType = type,
        arithmeticDifficulty = difficulty,
        questionId = questionId,
        gesturePattern = gesture,
    )

    private fun singleChoice(id: String) = Question(
        id = id,
        type = TaskType.SINGLE_CHOICE,
        prompt = "题目 $id",
        options = listOf("A", "B", "C", "D"),
        answers = listOf("A"),
    )

    private fun fillBlank(id: String) = Question(
        id = id,
        type = TaskType.FILL_BLANK,
        prompt = "填空 $id",
        answers = listOf("x"),
    )

    // ---------------------------------------------------------- 正常路径

    @Test
    fun `an arithmetic alarm always yields an arithmetic task`() {
        repeat(50) { seed ->
            val task = RingTaskFactory.create(
                alarm = alarm(TaskType.ARITHMETIC),
                bank = emptyList(),
                random = Random(seed),
            )
            assertTrue("第 $seed 次没有生成计算题", task is RingTask.Arithmetic)
        }
    }

    @Test
    fun `a gesture alarm uses the recorded pattern`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.GESTURE, gesture = "0,1,2,5"),
            bank = emptyList(),
            allowReverseGesture = true,
        )
        assertEquals(RingTask.Gesture(listOf(0, 1, 2, 5), true), task)
    }

    @Test
    fun `a bank alarm picks a question of the configured type`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.SINGLE_CHOICE),
            bank = listOf(singleChoice("q1")),
        )
        assertEquals(RingTask.Choice(singleChoice("q1")), task)
    }

    @Test
    fun `a fill blank alarm yields a fill blank task`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.FILL_BLANK),
            bank = listOf(fillBlank("b1")),
        )
        assertEquals(RingTask.FillBlank(fillBlank("b1")), task)
    }

    @Test
    fun `a pinned question is used when it exists`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.SINGLE_CHOICE, questionId = "q2"),
            bank = listOf(singleChoice("q1"), singleChoice("q2")),
        )
        assertEquals("q2", (task as RingTask.Choice).question.id)
    }

    // ---------------------------------------------------------- 兜底路径
    // 这一组是安全网：任何一条失效都会让用户面对一个关不掉的闹钟。

    @Test
    fun `an empty bank falls back to arithmetic instead of trapping the user`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.SINGLE_CHOICE),
            bank = emptyList(),
        )
        assertTrue("题库为空时必须是可解的计算题", task is RingTask.Arithmetic)
    }

    @Test
    fun `a bank with no question of the needed type falls back to arithmetic`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.FILL_BLANK),
            bank = listOf(singleChoice("q1")),
        )
        assertTrue("没有填空题时必须回退", task is RingTask.Arithmetic)
    }

    @Test
    fun `a gesture alarm without a recorded pattern falls back to arithmetic`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.GESTURE, gesture = ""),
            bank = emptyList(),
        )
        assertTrue("没录手势时必须回退", task is RingTask.Arithmetic)
    }

    @Test
    fun `a corrupt gesture pattern falls back to arithmetic`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.GESTURE, gesture = "9,9,9"),
            bank = emptyList(),
        )
        assertTrue("坏掉的手势必须回退", task is RingTask.Arithmetic)
    }

    @Test
    fun `a pinned question that was deleted falls back to a random one of the same type`() {
        val task = RingTaskFactory.create(
            alarm = alarm(TaskType.SINGLE_CHOICE, questionId = "gone"),
            bank = listOf(singleChoice("q1")),
        )
        assertEquals("q1", (task as RingTask.Choice).question.id)
    }

    @Test
    fun `every fallback arithmetic task is still answerable`() {
        repeat(30) { seed ->
            val task = RingTaskFactory.create(
                alarm = alarm(TaskType.SINGLE_CHOICE),
                bank = emptyList(),
                random = Random(seed),
            ) as RingTask.Arithmetic
            assertTrue(task.prompt.endsWith(" = ?"))
            assertTrue(AnswerChecker.checkArithmetic(task.answer, task.answer.toString()))
        }
    }

    // ---------------------------------------------------------- 抽题

    @Test
    fun `picking from the bank never returns a question of another type`() {
        val bank = listOf(singleChoice("q1"), singleChoice("q2"), fillBlank("b1"))
        repeat(40) { seed ->
            val picked = RingTaskFactory.pickFromBank(
                alarm = alarm(TaskType.SINGLE_CHOICE),
                bank = bank,
                random = Random(seed),
            )
            assertEquals(TaskType.SINGLE_CHOICE, picked?.type)
        }
    }

    @Test
    fun `picking from an empty bank yields null`() {
        assertEquals(
            null,
            RingTaskFactory.pickFromBank(alarm(TaskType.MULTI_CHOICE), emptyList()),
        )
    }
}
