package com.appalarm.ui.ringtone

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getStringOrNull

/** 一个可选的铃声。 */
data class RingtoneOption(val uri: String, val title: String)

/**
 * 铃声来源。
 *
 * 三条路径各自解决的问题不同：
 *  - 系统铃声：开箱可用，不需要任何权限。
 *  - 媒体库音频：用户手机里的歌，需要 READ_MEDIA_AUDIO。
 *  - SAF 选文件：任何位置的音频都能选，**不需要权限**，代价是要持久化 URI 授权，
 *    否则重启后就失效了。
 *
 * 不做任何音频转码：时长只用来给「截取」提供滑动范围，播放时靠 seekTo 定位。
 */
object RingtoneSupport {

    fun systemAlarmOptions(context: Context): List<RingtoneOption> = try {
        // RingtoneManager 没有配对的 getType()，所以 Kotlin 合成属性用不了，只能显式调 setter。
        val manager = RingtoneManager(context)
        manager.setType(RingtoneManager.TYPE_ALARM)
        val cursor = manager.cursor
        buildList {
            for (index in 0 until cursor.count) {
                val uri = manager.getRingtoneUri(index) ?: continue
                val title = cursor.getStringOrNull(RingtoneManager.TITLE_COLUMN_INDEX)
                    ?: "铃声 ${index + 1}"
                add(RingtoneOption(uri.toString(), title))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun deviceAudioOptions(context: Context): List<RingtoneOption> = try {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: continue
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    )
                    add(RingtoneOption(uri.toString(), title))
                }
            }
        } ?: emptyList()
    } catch (_: Exception) {
        // 没授权或媒体库不可用时安静降级成空列表。
        emptyList()
    }

    /**
     * 读取音频时长，用来决定截取滑杆的范围。
     * 读不到就返回 null，界面上会隐藏截取功能（而不是显示一个没用的滑杆）。
     */
    fun durationMs(context: Context, uriString: String): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun formatMs(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
