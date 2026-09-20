package com.appalarm.data

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 把一个 JSON 文件当小型数据库用。
 *
 * 写盘走「先写临时文件、再改名」：闹钟应用如果写盘写到一半被系统杀死，
 * 留下半个损坏的文件就意味着所有闹钟一起消失，这个代价太高了。
 *
 * 读取失败时返回 [fallback] 而不是抛异常 —— 一个格式不对的文件不应该让 app
 * 起不来，用户至少还能进去重新设置。但失败会记进 logcat，不会无声无息地丢数据。
 */
class JsonStore(private val file: File, private val json: Json) {

    fun <T> read(serializer: KSerializer<T>, fallback: T): T {
        if (!file.exists()) return fallback
        return runCatching {
            // 剥掉 BOM：外部工具写出来的文件常常带一个，JSON 解析器不认。
            json.decodeFromString(serializer, file.readText().stripBom())
        }
            .onFailure { Log.w(TAG, "无法解析 ${file.name}，已回退到默认值", it) }
            .getOrElse { fallback }
    }

    fun <T> write(serializer: KSerializer<T>, value: T) {
        val text = json.encodeToString(serializer, value)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text)
        if (temp.renameTo(file)) return
        // 目标已存在时某些文件系统上 rename 会失败，退化成直接覆盖。
        file.writeText(text)
        temp.delete()
    }

    private companion object {
        const val TAG = "AppAlarm/JsonStore"
    }
}
