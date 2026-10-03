package io.github.pathgao.housheng

internal fun publicFeedTitle(description: String): String? {
    if (description.length > 2000 || !description.startsWith("视频  ") && !description.startsWith("笔记  ")) return null
    val end = description.lastIndexOf(" 来自")
    if (end <= 4 || !Regex(" \\d+(?:\\.\\d+)?[万亿]?赞$").containsMatchIn(description)) return null
    return description.substring(4, end).trim().takeIf { it.length in 2..1000 }
}
