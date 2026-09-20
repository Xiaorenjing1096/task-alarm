package com.appalarm.data.model

import kotlinx.serialization.Serializable

/**
 * 全局设置。所有字段都有默认值，所以旧版本的 settings.json 反序列化不会失败。
 */
@Serializable
data class AppSettings(
    /** 九宫格手势是否接受「反着画一遍」。 */
    val allowReverseGesture: Boolean = false,
    /** 新建闹钟时的默认题型。 */
    val defaultTaskType: TaskType = TaskType.ARITHMETIC,
    /** 新建闹钟时计算题的默认难度 1..3。 */
    val defaultArithmeticDifficulty: Int = 2,
    /** 新建闹钟时默认需要答对几题。 */
    val defaultTaskCount: Int = 1,
    /**
     * 是否已经把内置题库灌进去了。
     *
     * 用它而不是「题库为空就重新灌」来判断：否则用户如果把题目全删光，
     * 下次启动内置题目会全部复活。
     */
    val questionsSeeded: Boolean = false,
)
