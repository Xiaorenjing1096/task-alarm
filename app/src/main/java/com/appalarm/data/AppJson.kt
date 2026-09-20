package com.appalarm.data

import kotlinx.serialization.json.Json

/**
 * 全应用统一的 JSON 配置。
 *
 * 导出和导入必须共用同一份配置，否则会出现「自己导出的文件自己导不回来」这种
 * 最糟糕的情况。放在这里而不是各自 new 一个 Json，就是为了杜绝这种可能。
 *
 * - prettyPrint：导出的文件要能让人直接读和改。
 * - ignoreUnknownKeys：别人用更新版本导出的文件，旧版本也要能读进来。
 * - encodeDefaults：写全所有字段，让文件本身自解释。
 */
val AppJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
