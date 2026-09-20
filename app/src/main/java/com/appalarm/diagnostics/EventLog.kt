package com.appalarm.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 只记「响铃链路关键节点」的轻量日志，落在应用私有目录里。
 *
 * 为什么需要它：闹钟失效的原因绝大多数发生在**用户的手机上**，而那里通常没有 adb。
 * 只拿到「没响」这一个信息，是无法区分下面两种完全不同的情况的：
 *
 *  - 「系统根本没唤醒应用」→ 闹钟被取消了（被强行停止、被 ROM 后台管控）
 *  - 「唤醒了，但服务没起来 / 没声音」→ 应用侧的问题
 *
 * 把关键节点落到文件里，用户就能在设置页直接看到，念出来即可定位。
 * 每一条都带时间戳，所以「到点了却什么都没有」本身就是最强的证据。
 *
 * 写入失败一律吞掉：诊断日志绝不能影响响铃本身。
 */
object EventLog {

    private const val FILE_NAME = "ring-events.log"

    /** 只保留最近这么多条，避免无限增长。 */
    private const val MAX_LINES = 300

    private fun stamp(): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())

    fun record(context: Context, message: String) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val existing = if (file.exists()) file.readLines() else emptyList()
            val combined = existing + "${stamp()}  $message"
            val trimmed = if (combined.size > MAX_LINES) combined.takeLast(MAX_LINES) else combined
            file.writeText(trimmed.joinToString("\n"))
        }
    }

    /** 最近 [limit] 条，最新的在最后。 */
    fun read(context: Context, limit: Int = 60): List<String> =
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.readLines().takeLast(limit) else emptyList()
        }.getOrElse { emptyList() }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
