package com.appalarm.data.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/**
 * 关闭闹钟需要完成的题型。
 *
 * [ARITHMETIC] 的题目在响铃时随机生成，不需要题库；
 * [GESTURE] 的手势由用户自己录制，存放在 [Alarm.gesturePattern] 上。
 */
@Serializable
enum class TaskType {
    SINGLE_CHOICE,
    MULTI_CHOICE,
    FILL_BLANK,
    ARITHMETIC,
    GESTURE,
    ;

    val displayName: String
        get() = when (this) {
            SINGLE_CHOICE -> "单选题"
            MULTI_CHOICE -> "多选题"
            FILL_BLANK -> "填空题"
            ARITHMETIC -> "计算题"
            GESTURE -> "手势解锁"
        }

    /** 计算题随机生成、手势题用户录制，只有这三种题型要用题库。 */
    val usesQuestionBank: Boolean
        get() = this == SINGLE_CHOICE || this == MULTI_CHOICE || this == FILL_BLANK
}

/**
 * 一周重复规则用位掩码表示，bit0 = 周一 … bit6 = 周日（ISO 顺序，和
 * [DayOfWeek.getValue] 一致）。
 */
object Weekdays {
    /** 不重复：只在下一次到达该时刻时响一次。 */
    const val NONE = 0
    const val ALL = 0b111_1111
    const val WEEKDAYS = 0b001_1111
    const val WEEKEND = 0b110_0000

    private val SHORT_NAMES = arrayOf("一", "二", "三", "四", "五", "六", "日")

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = (mask and bit(day)) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    /** 按周一→周日的顺序返回被选中的星期。 */
    fun days(mask: Int): List<DayOfWeek> =
        (1..7).map { DayOfWeek.of(it) }.filter { contains(mask, it) }

    fun shortName(day: DayOfWeek): String = SHORT_NAMES[day.value - 1]

    /** 生成给人看的描述，例如「每天」「工作日」「周一 周三」。 */
    fun describe(mask: Int): String = when (mask) {
        NONE -> "仅一次"
        ALL -> "每天"
        WEEKDAYS -> "工作日"
        WEEKEND -> "周末"
        else -> days(mask).joinToString(" ") { "周${shortName(it)}" }
    }
}

@Serializable
data class Alarm(
    val id: String,
    /** 0..23 */
    val hour: Int,
    /** 0..59 */
    val minute: Int,
    /** 见 [Weekdays]；[Weekdays.NONE] 表示响一次后自动停用。 */
    val repeatDaysMask: Int = Weekdays.NONE,
    val enabled: Boolean = true,
    val label: String = "",
    /** null 表示跟随系统默认闹钟铃声。 */
    val ringtoneUri: String? = null,
    /** 仅用于显示，方便用户认出选的是哪首。 */
    val ringtoneTitle: String = "",
    /** 铃声片段起点，毫秒。 */
    val clipStartMs: Long = 0L,
    /** 铃声片段终点，毫秒；-1 表示一直播到文件结尾。 */
    val clipEndMs: Long = -1L,
    /** 音量渐强秒数；0 表示不渐强。 */
    val volumeRampSeconds: Int = 0,
    val vibrate: Boolean = true,
    val taskType: TaskType = TaskType.ARITHMETIC,
    /** 需要连续答对几题才能关闭闹钟。 */
    val taskCount: Int = 1,
    /** 计算题难度 1..3。 */
    val arithmeticDifficulty: Int = 2,
    /** 指定使用哪道题；null 表示从题库随机抽。 */
    val questionId: String? = null,
    /** 九宫格手势的点位序列，逗号分隔，例如 "0,1,2,5,8"。 */
    val gesturePattern: String = "",
    val createdAt: Long = 0L,
) {
    val timeLabel: String
        get() = "%02d:%02d".format(hour, minute)
}
