package com.appalarm.data

import com.appalarm.data.model.QUESTION_BANK_APP_TAG
import com.appalarm.data.model.QUESTION_BANK_SCHEMA_VERSION
import com.appalarm.data.model.Question
import com.appalarm.data.model.QuestionBankFile

/**
 * 题库文件的编解码。
 *
 * 特意做成不依赖 Context 的纯对象：这样「导出再导入是否等价」「各种坏文件会怎样」
 * 都能用普通 JVM 单元测试覆盖。导入的文件是用户手改过的，什么都可能遇到。
 */
object QuestionBankCodec {

    fun encode(questions: List<Question>, exportedAt: String): String =
        AppJson.encodeToString(
            QuestionBankFile.serializer(),
            QuestionBankFile(
                schemaVersion = QUESTION_BANK_SCHEMA_VERSION,
                app = QUESTION_BANK_APP_TAG,
                exportedAt = exportedAt,
                questions = questions,
            ),
        )

    /**
     * 解析导入的文件。[Result] 的失败信息是可以直接展示给用户的中文原因。
     */
    fun decode(text: String): Result<List<Question>> = runCatching {
        // 用户多半是用记事本或 Excel 改过这个文件的，开头很可能有 BOM。
        val bank = AppJson.decodeFromString(QuestionBankFile.serializer(), text.stripBom())
        require(bank.schemaVersion <= QUESTION_BANK_SCHEMA_VERSION) {
            "题库文件版本为 ${bank.schemaVersion}，高于当前应用支持的 " +
                "$QUESTION_BANK_SCHEMA_VERSION，请先升级应用再导入"
        }
        bank.questions
    }
}
