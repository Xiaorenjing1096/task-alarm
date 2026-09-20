package com.appalarm.core

import kotlin.math.hypot

/**
 * 3×3 九宫格手势。
 *
 * 点位编号 0..8，先行后列，左上角为 0：
 * ```
 * 0 1 2
 * 3 4 5
 * 6 7 8
 * ```
 *
 * 关于「容差」：容差体现在**命中判定**上 —— 触点离格心太远就不算点中这一格；
 * 而序列比对是精确的。这跟系统图案解锁的行为一致：格子足够大，用户不需要点得
 * 绝对精准，但点错格子就是错。如果改成对坐标做模糊比对，反而会在两个相邻格子
 * 之间产生「算对还是算错」的灰区。
 */
object GesturePattern {

    const val GRID = 3
    const val CELL_COUNT = GRID * GRID
    const val MIN_POINTS = 4
    const val MAX_POINTS = CELL_COUNT

    /** 命中半径占格边长的比例。0.5 表示铺满整格，这里留一点余量。 */
    const val HIT_TOLERANCE = 0.42f

    fun encode(points: List<Int>): String = points.joinToString(",")

    fun decode(raw: String): List<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }

    /** 合法手势：4~9 个点、都在盘内、且不重复。 */
    fun isValid(points: List<Int>): Boolean =
        points.size in MIN_POINTS..MAX_POINTS &&
            points.all { it in 0 until CELL_COUNT } &&
            points.distinct().size == points.size

    /**
     * 把触点映射成格子序号，落在容差外返回 null。
     * [size] 是正方形绘制区域的边长（像素）。
     */
    fun hitTest(x: Float, y: Float, size: Float, tolerance: Float = HIT_TOLERANCE): Int? {
        if (size <= 0f) return null
        // toInt() 对负数会向零截断，所以先挡掉区域外的点。
        if (x < 0f || y < 0f || x > size || y > size) return null

        val cell = size / GRID
        val col = (x / cell).toInt().coerceAtMost(GRID - 1)
        val row = (y / cell).toInt().coerceAtMost(GRID - 1)
        val centerX = (col + 0.5f) * cell
        val centerY = (row + 0.5f) * cell
        val distance = hypot((x - centerX).toDouble(), (y - centerY).toDouble()).toFloat()
        return if (distance <= tolerance * cell) row * GRID + col else null
    }

    /** 格子中心坐标，供绘制使用。 */
    fun center(index: Int, size: Float): Pair<Float, Float> {
        val cell = size / GRID
        return ((index % GRID) + 0.5f) * cell to ((index / GRID) + 0.5f) * cell
    }

    /**
     * 实际手势是否与预期一致。
     *
     * [allowReverse] 打开后，反着画一遍也算对 —— 半睡半醒时画反方向是常事，
     * 由用户自己决定要不要宽容这一档。
     */
    fun matches(
        expected: List<Int>,
        actual: List<Int>,
        allowReverse: Boolean = false,
    ): Boolean {
        if (expected.isEmpty() || !isValid(actual)) return false
        if (expected == actual) return true
        return allowReverse && expected == actual.reversed()
    }
}
