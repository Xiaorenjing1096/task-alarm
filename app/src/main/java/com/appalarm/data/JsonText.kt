package com.appalarm.data

/**
 * 去掉 UTF-8 BOM（U+FEFF）。
 *
 * Windows 记事本、PowerShell 的 `Set-Content -Encoding utf8`、Excel 导出等等都会在
 * UTF-8 文件开头塞一个 BOM。JSON 解析器会把它当成非法字符直接报错，于是表现成
 * 「文件内容明明是对的，就是导不进来」—— 这是最让人摸不着头脑的一类 bug。
 *
 * 用户手改过的题库文件几乎必然会踩到，所以凡是从外部读进来的 JSON 一律先剥 BOM。
 */
internal fun String.stripBom(): String =
    if (isNotEmpty() && this[0] == '\uFEFF') substring(1) else this
