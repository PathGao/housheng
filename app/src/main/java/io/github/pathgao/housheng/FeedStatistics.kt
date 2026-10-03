package io.github.pathgao.housheng

enum class ContentTopic(val label: String) {
    LIFE("生活知识"), HEALTH("健康养生"), NEWS("新闻时事"), ENTERTAINMENT("娱乐"),
    RURAL("三农"), PRODUCTS("商品介绍"), OTHER("其他"), UNCLASSIFIED("未分类")
}

class FeedStatistics {
    private val totals = mutableMapOf<Pair<String, ContentTopic>, Int>()
    private val lastDisplay = mutableMapOf<String, PageToken>()
    fun record(token: PageToken, topic: ContentTopic = ContentTopic.UNCLASSIFIED) {
        if (!AppCatalog.permits(token.source, "pages") || token.kind != ContentKind.USER_CONTENT || lastDisplay[token.source] == token) return
        lastDisplay[token.source] = token
        val key = token.source to topic
        totals[key] = ((totals[key] ?: 0) + 1).coerceAtMost(1000000)
    }
    fun counts(testData: Boolean): Map<ContentTopic, Int> = totals.entries
        .filter { (it.key.first == AppCatalog.FIXTURE) == testData }
        .groupBy({ it.key.second }, { it.value }).mapValues { it.value.sum() }
    fun clear() { totals.clear(); lastDisplay.clear() }
}
