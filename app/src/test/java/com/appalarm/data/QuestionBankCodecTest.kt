package com.appalarm.data

import com.appalarm.data.model.Question
import com.appalarm.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBankCodecTest {

    private val sample = listOf(
        Question(
            id = "s1",
            type = TaskType.SINGLE_CHOICE,
            prompt = "中国的首都是？",
            options = listOf("北京", "上海", "广州", "深圳"),
            answers = listOf("北京"),
            difficulty = 2,
            tags = listOf("常识"),
        ),
        Question(
            id = "b1",
            type = TaskType.FILL_BLANK,
            prompt = "12 + 1 = ?",
            answers = listOf("13", "十三"),
            builtIn = true,
        ),
    )

    @Test
    fun `exporting then importing gives back exactly the same questions`() {
        val text = QuestionBankCodec.encode(sample, "2026-01-01T00:00:00Z")
        assertEquals(sample, QuestionBankCodec.decode(text).getOrThrow())
    }

    @Test
    fun `the exported file is readable by a human`() {
        val text = QuestionBankCodec.encode(sample, "2026-01-01T00:00:00Z")
        assertTrue("应当包含格式版本", text.contains("schemaVersion"))
        assertTrue("应当是美化过的多行 JSON", text.contains("\n"))
        assertTrue("应当能看到中文题干", text.contains("中国的首都是？"))
        assertTrue("应当带上答案", text.contains("北京"))
    }

    @Test
    fun `a file saved with a utf-8 bom still imports`() {
        // 记事本、PowerShell 的 Set-Content、Excel 导出的 UTF-8 都会带 BOM。
        // 不处理的话表现成「内容看着没问题却导不进来」，极难排查。
        val text = "\uFEFF" + QuestionBankCodec.encode(sample, "2026-01-01T00:00:00Z")
        assertEquals(sample, QuestionBankCodec.decode(text).getOrThrow())
    }

    @Test
    fun `unknown fields are ignored so newer exports still load`() {
        // 别人用更新版本导出的文件，旧版本也要能读进来，而不是整份报废。
        val text = """
            {
              "schemaVersion": 1,
              "app": "AppAlarm",
              "somethingFromTheFuture": 42,
              "questions": [
                {
                  "id": "x",
                  "type": "FILL_BLANK",
                  "prompt": "1 + 1 = ?",
                  "answers": ["2"],
                  "extraField": {"nested": true}
                }
              ]
            }
        """.trimIndent()

        val decoded = QuestionBankCodec.decode(text).getOrThrow()
        assertEquals(1, decoded.size)
        assertEquals("1 + 1 = ?", decoded.single().prompt)
        assertEquals(listOf("2"), decoded.single().answers)
    }

    @Test
    fun `a newer schema version is rejected with an actionable message`() {
        val text = """{ "schemaVersion": 99, "questions": [] }"""
        val result = QuestionBankCodec.decode(text)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("错误信息里应当带上版本号：$message", message.contains("99"))
    }

    @Test
    fun `the current schema version is accepted`() {
        val text = """{ "schemaVersion": 1, "questions": [] }"""
        assertTrue(QuestionBankCodec.decode(text).isSuccess)
    }

    @Test
    fun `malformed json fails instead of throwing`() {
        assertTrue(QuestionBankCodec.decode("{ this is not json").isFailure)
        assertTrue(QuestionBankCodec.decode("").isFailure)
    }

    @Test
    fun `a file with no questions array decodes to an empty list`() {
        val decoded = QuestionBankCodec.decode("""{ "schemaVersion": 1 }""").getOrThrow()
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `an unknown task type fails cleanly rather than silently mangling data`() {
        val text = """
            {
              "schemaVersion": 1,
              "questions": [
                { "id": "x", "type": "SOMETHING_NEW", "prompt": "?", "answers": ["a"] }
              ]
            }
        """.trimIndent()
        assertTrue(QuestionBankCodec.decode(text).isFailure)
    }
}
