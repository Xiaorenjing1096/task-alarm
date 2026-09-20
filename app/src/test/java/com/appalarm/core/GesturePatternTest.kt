package com.appalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePatternTest {

    // ---------- 编解码 ----------

    @Test
    fun `encode and decode round trip`() {
        val points = listOf(0, 1, 2, 5, 8)
        assertEquals(points, GesturePattern.decode(GesturePattern.encode(points)))
    }

    @Test
    fun `decode tolerates spaces and junk segments`() {
        assertEquals(listOf(0, 1, 2), GesturePattern.decode(" 0 , 1 ,2 "))
        assertEquals(listOf(0, 2), GesturePattern.decode("0,x,2"))
    }

    @Test
    fun `decode of an empty string is empty`() {
        assertTrue(GesturePattern.decode("").isEmpty())
    }

    // ---------- 合法性 ----------

    @Test
    fun `a gesture needs at least four distinct points`() {
        assertFalse(GesturePattern.isValid(listOf(0, 1, 2)))
        assertTrue(GesturePattern.isValid(listOf(0, 1, 2, 3)))
    }

    @Test
    fun `all nine points is valid and ten is not`() {
        assertTrue(GesturePattern.isValid((0..8).toList()))
        assertFalse(GesturePattern.isValid((0..8).toList() + 0))
    }

    @Test
    fun `repeated points are invalid`() {
        assertFalse(GesturePattern.isValid(listOf(0, 1, 1, 2)))
    }

    @Test
    fun `points outside the grid are invalid`() {
        assertFalse(GesturePattern.isValid(listOf(0, 1, 2, 9)))
        assertFalse(GesturePattern.isValid(listOf(0, 1, 2, -1)))
    }

    // ---------- 命中判定 ----------

    @Test
    fun `the centre of every cell maps to that cell`() {
        val size = 300f
        for (index in 0 until GesturePattern.CELL_COUNT) {
            val (centerX, centerY) = GesturePattern.center(index, size)
            assertEquals(
                "格心应命中第 $index 格",
                index,
                GesturePattern.hitTest(centerX, centerY, size),
            )
        }
    }

    @Test
    fun `small imprecision still hits the intended cell`() {
        // 格边长 100，容差 42；偏移 (20, -15) 距离 25，应该命中。
        val size = 300f
        val (centerX, centerY) = GesturePattern.center(4, size)
        assertEquals(4, GesturePattern.hitTest(centerX + 20f, centerY - 15f, size))
    }

    @Test
    fun `a point too far from every centre hits nothing`() {
        // (50, 95) 仍在第 0 格范围内，但离格心 45 > 42，不该被算作点中。
        val size = 300f
        assertNull(GesturePattern.hitTest(50f, 95f, size))
    }

    @Test
    fun `points outside the drawing area hit nothing`() {
        val size = 300f
        assertNull(GesturePattern.hitTest(-5f, 50f, size))
        assertNull(GesturePattern.hitTest(50f, -5f, size))
        assertNull(GesturePattern.hitTest(350f, 50f, size))
        assertNull(GesturePattern.hitTest(50f, 350f, size))
    }

    @Test
    fun `a degenerate size hits nothing`() {
        assertNull(GesturePattern.hitTest(10f, 10f, 0f))
        assertNull(GesturePattern.hitTest(10f, 10f, -1f))
    }

    // ---------- 比对 ----------

    @Test
    fun `matches requires the exact sequence by default`() {
        val expected = listOf(0, 1, 2, 5)
        assertTrue(GesturePattern.matches(expected, listOf(0, 1, 2, 5)))
        assertFalse(GesturePattern.matches(expected, listOf(0, 1, 2, 4)))
        assertFalse(GesturePattern.matches(expected, listOf(5, 2, 1, 0)))
    }

    @Test
    fun `matches can accept the reverse direction when enabled`() {
        val expected = listOf(0, 1, 2, 5)
        val reversed = listOf(5, 2, 1, 0)
        assertTrue(GesturePattern.matches(expected, reversed, allowReverse = true))
        assertFalse(GesturePattern.matches(expected, reversed, allowReverse = false))
    }

    @Test
    fun `matches rejects attempts that are too short or malformed`() {
        val expected = listOf(0, 1, 2, 5)
        assertFalse("只有三个点不算手势", GesturePattern.matches(expected, listOf(0, 1, 2)))
        assertFalse("重复点不算手势", GesturePattern.matches(expected, listOf(0, 1, 1, 2)))
        assertFalse(GesturePattern.matches(expected, listOf(0, 1, 2, 9)))
    }

    @Test
    fun `matches fails when there is no expected pattern`() {
        assertFalse(GesturePattern.matches(emptyList(), listOf(0, 1, 2, 5)))
    }
}
