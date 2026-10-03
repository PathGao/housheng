package io.github.pathgao.housheng

/** Screen rectangle with android.graphics.Rect semantics, usable in JVM tests where Rect is a stub. */
internal data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    operator fun contains(other: Box) = left < right && top < bottom &&
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

/** One visible accessibility node, copied out of the live tree. */
internal data class FeedNode(
    val text: String = "", val desc: String = "", val id: String = "", val className: String = "",
    val box: Box, val selected: Boolean = false, val scrollable: Boolean = false
)

internal data class FeedCard(val title: String, val box: Box)

internal object RealFeeds {
    const val XIAOHONGSHU = "com.xingin.xhs"
    private val douyin = setOf("com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite")
    private val kuaishou = setOf("com.smile.gifmaker", "com.kuaishou.nebula")
    private val versions = mapOf(XIAOHONGSHU to "9.49.0") + douyin.associateWith { "40.6.0" } + kuaishou.associateWith { "14.8.40" }
    val packages = versions.keys.toList()

    fun version(pkg: String) = versions[pkg]

    /** Page structure differs between releases, so only the surveyed version is read; Kuaishou appends a build number. */
    fun supports(pkg: String, installed: String?): Boolean {
        val expected = versions[pkg] ?: return false
        return installed == expected || pkg in kuaishou && installed?.startsWith("$expected.") == true
    }

    /** Items to judge on the current screen, in pre-order [nodes] with the root first; empty whenever the page is not a confirmed public feed. */
    fun read(pkg: String, nodes: List<FeedNode>): List<FeedCard> {
        if (nodes.isEmpty() || nodes.any { it.className == "android.webkit.WebView" || it.className == "android.widget.EditText" }) return emptyList()
        return when (pkg) {
            XIAOHONGSHU -> xiaohongshu(nodes)
            in douyin -> video(nodes, "$pkg:id/desc", { it.desc.startsWith("推荐，已选中") }, { it.desc == "首页，按钮" })
            in kuaishou -> video(nodes, "$pkg:id/element_caption_label",
                { it.id == "$pkg:id/nasa_featured_default_search_view" || it.id == "$pkg:id/thanos_home_top_search" },
                { it.id == "$pkg:id/shoot_container" })
            else -> emptyList()
        }
    }

    private fun FeedNode.mentions(words: List<String>) = words.any { text.contains(it) || desc.contains(it) }

    private fun xiaohongshu(nodes: List<FeedNode>): List<FeedCard> {
        if (!listOf("首页", "发现").all { label -> nodes.any { it.selected && it.desc == label } }) return emptyList()
        val area = nodes.singleOrNull { it.className == "androidx.recyclerview.widget.RecyclerView" && it.scrollable }?.box ?: return emptyList()
        return nodes.mapNotNull { node ->
            val title = publicFeedTitle(node.desc) ?: return@mapNotNull null
            val box = node.box
            if (box !in area || box.width !in area.width / 3..area.width * 2 / 3 || box.height < box.width / 2) return@mapNotNull null
            if (nodes.any { it.box in box && it.mentions(listOf("广告", "赞助", "推广", "品牌合作")) }) null else FeedCard(title, box)
        }.distinct().take(4)
    }

    // Ads, live previews, comment panels and login prompts share the feed screen; any of these markers means the screen is not read.
    private val videoBlockers = listOf("广告", "开发者：", "立即下载", "直播中", "直播间", "条评论", "一键登录", "用户协议")

    /** The covered area runs from the top tab bar to the bottom navigation. Neighbouring items stay in the tree, so exactly one caption must lie inside it. */
    private fun video(nodes: List<FeedNode>, captionId: String, isTop: (FeedNode) -> Boolean, isBottom: (FeedNode) -> Boolean): List<FeedCard> {
        if (nodes.any { it.mentions(videoBlockers) }) return emptyList()
        val top = nodes.singleOrNull(isTop) ?: return emptyList()
        val bottom = nodes.singleOrNull(isBottom) ?: return emptyList()
        val screen = nodes.first().box
        val area = Box(screen.left, top.box.bottom, screen.right, bottom.box.top)
        if (area.height < screen.height / 2) return emptyList()
        val caption = nodes.singleOrNull { it.id == captionId && it.box.width > 0 && it.box.height > 0 && it.box in area } ?: return emptyList()
        return listOfNotNull(videoTitle(caption.text)?.let { FeedCard(it, area) })
    }
}

/** Caption text without the expand marker or @mentions, which name other accounts. */
internal fun videoTitle(caption: String): String? = caption.replace("​", "").trim()
    .removeSuffix(">").trim().removeSuffix("展开").trim().removeSuffix("...").removeSuffix("…")
    .replace(Regex("@\\S+"), " ").replace(Regex("\\s+"), " ").trim()
    .takeIf { it.length in 2..1000 }
