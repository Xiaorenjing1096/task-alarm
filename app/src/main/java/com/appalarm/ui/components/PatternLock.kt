package com.appalarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appalarm.core.GesturePattern

/**
 * 3×3 九宫格手势控件。
 *
 * 既可以用来录手势（[onPatternFinished] 回调），也可以只作展示
 * （传 [previewPattern] 时禁用输入）。
 *
 * 命中判定交给 [GesturePattern.hitTest]，和运行时的判题逻辑完全同一份代码 ——
 * 手势题最容易出的 bug 就是「录制时和校验时的判定标准不一致」，共用一份实现可以
 * 从根上避免。
 */
@Composable
fun PatternLock(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    previewPattern: List<Int>? = null,
    onPatternFinished: (List<Int>) -> Unit = {},
) {
    val isInteractive = enabled && previewPattern == null

    var livePattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }

    val displayed = previewPattern ?: livePattern

    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(isInteractive) {
                if (!isInteractive) return@pointerInput

                // PointerInputScope 自带 size，不用再去 onSizeChanged 里记一遍。
                val side = minOf(size.width, size.height).toFloat()

                // 刻意不用 detectDragGestures：它回调的起点是「超过触摸斜率之后」的
                // 位置，而不是手指真正按下的位置。手指一开始动得快一点，第一个点就会
                // 被整格丢掉（实测画 0-3-6-7 会退化成 3-6-7，不足 4 点直接判错）。
                // 图案锁的正确语义是「按下即命中」，所以这里自己处理原始事件。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 消费掉，免得外层垂直滚动容器把手势抢去当滚动。
                    down.consume()

                    cursor = down.position
                    livePattern = listOfNotNull(
                        GesturePattern.hitTest(down.position.x, down.position.y, side),
                    )

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        cursor = change.position
                        val hit = GesturePattern.hitTest(
                            change.position.x,
                            change.position.y,
                            side,
                        )
                        if (hit != null && hit !in livePattern) {
                            livePattern = livePattern + hit
                        }
                        change.consume()
                    }

                    val finished = livePattern
                    livePattern = emptyList()
                    cursor = null
                    if (finished.isNotEmpty()) onPatternFinished(finished)
                }
            },
    ) {
        val side = minOf(size.width, size.height)
        val cell = side / GesturePattern.GRID
        val strokeWidth = cell * 0.06f
        val dotRadius = cell * 0.13f

        // 已连好的线段
        displayed.zipWithNext().forEach { (from, to) ->
            val (fromX, fromY) = GesturePattern.center(from, side)
            val (toX, toY) = GesturePattern.center(to, side)
            drawLine(
                color = activeColor,
                start = Offset(fromX, fromY),
                end = Offset(toX, toY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        // 从最后一个点到手指当前位置
        val last = displayed.lastOrNull()
        if (last != null && cursor != null) {
            val (lastX, lastY) = GesturePattern.center(last, side)
            drawLine(
                color = activeColor.copy(alpha = 0.45f),
                start = Offset(lastX, lastY),
                end = cursor!!,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        for (index in 0 until GesturePattern.CELL_COUNT) {
            val (x, y) = GesturePattern.center(index, side)
            val center = Offset(x, y)
            if (index in displayed) {
                drawCircle(activeColor, radius = dotRadius * 2.1f, center = center)
                drawCircle(Color.White, radius = dotRadius * 0.85f, center = center)
            } else {
                drawCircle(idleColor, radius = dotRadius, center = center)
            }
        }
    }
}

@Preview(name = "九宫格 · 已画出的手势", showBackground = true, widthDp = 320)
@Composable
private fun PreviewPatternLock() {
    MaterialTheme {
        PatternLock(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            previewPattern = listOf(0, 3, 6, 7),
        )
    }
}
