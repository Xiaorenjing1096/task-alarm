package com.appalarm.ui.ringtone

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 铃声片段截取。
 *
 * 「截取」在这里的含义是**记录起止毫秒、播放时只循环这一段**，不做音频转码。
 * 好处很实在：不需要任何编解码库，不占额外存储，改起止点也是瞬时的。
 * 代价是这段片段只在本应用内有效，不会生成一个能被系统铃声设置选中的新文件 ——
 * 对一个闹钟应用来说这个取舍是划算的。
 */
@Composable
fun ClipRangePicker(
    uriString: String,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
) {
    val context = LocalContext.current

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun stopPreview() {
        player?.let { runCatching { it.release() } }
        player = null
        playing = false
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    // 预览到片段终点就停下 —— MediaPlayer 本身没有「播放到某个位置为止」的能力。
    LaunchedEffect(playing, startMs, endMs, durationMs) {
        while (playing) {
            val current = player
            if (current != null) {
                val stopAt = if (endMs > 0L) endMs else durationMs
                runCatching {
                    if (current.currentPosition >= stopAt) stopPreview()
                }
            }
            delay(120)
        }
    }

    fun startPreview() {
        stopPreview()
        val from = startMs.coerceIn(0L, durationMs)
        val mediaPlayer = MediaPlayer()
        runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mediaPlayer.setDataSource(context, Uri.parse(uriString))
            mediaPlayer.prepare()
            mediaPlayer.seekTo(from.toInt())
            mediaPlayer.start()
            player = mediaPlayer
            playing = true
        }.onFailure {
            runCatching { mediaPlayer.release() }
        }
    }

    val upperBound = durationMs.coerceAtLeast(1L).toFloat()
    val rangeStart = startMs.coerceIn(0L, durationMs).toFloat()
    val rangeEnd = (if (endMs > 0L) endMs else durationMs).coerceIn(0L, durationMs).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("截取片段", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "只播放这一段，从 ${RingtoneSupport.formatMs(rangeStart.toLong())} " +
                "到 ${RingtoneSupport.formatMs(rangeEnd.toLong())}" +
                "（总长 ${RingtoneSupport.formatMs(durationMs)}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RangeSlider(
            value = rangeStart..rangeEnd,
            onValueChange = { range ->
                onRangeChange(range.start.toLong(), range.endInclusive.toLong())
            },
            valueRange = 0f..upperBound,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { if (playing) stopPreview() else startPreview() },
            ) { Text(if (playing) "停止试听" else "试听片段") }

            OutlinedButton(
                onClick = {
                    stopPreview()
                    onRangeChange(0L, -1L)
                },
            ) { Text("用整首") }
        }

        Spacer(Modifier.height(4.dp))
    }
}
