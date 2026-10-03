package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

/** Synthetic trees shaped like the surveyed screens; no real dump or caption is copied here. */
class RealFeedsTest {
    private val douyin = "com.ss.android.ugc.aweme"
    private val kuaishou = "com.smile.gifmaker"
    private val root = FeedNode(className = "android.widget.FrameLayout", box = Box(0, 0, 1080, 2340))

    private fun douyinScreen(pkg: String = douyin, caption: String = "合成标题 #话题 @朋友 展开", tab: String = "推荐，已选中，按钮") = listOf(
        root,
        FeedNode(desc = tab, box = Box(800, 90, 940, 210)),
        FeedNode(desc = "评论12，按钮", box = Box(914, 1300, 1079, 1500)),
        FeedNode(text = "@合成作者", id = "$pkg:id/title", box = Box(33, 1800, 300, 1870)),
        FeedNode(text = caption, id = "$pkg:id/desc", box = Box(33, 1870, 820, 2010)),
        FeedNode(text = "首页", desc = "首页，按钮", box = Box(60, 2100, 150, 2170))
    )

    private fun kuaishouScreen(pkg: String = kuaishou, search: String = "nasa_featured_default_search_view") = listOf(
        root,
        FeedNode(desc = "查找", id = "$pkg:id/$search", box = Box(940, 95, 1050, 205)),
        FeedNode(text = "@合成作者", id = "$pkg:id/user_name_text_view", box = Box(30, 1820, 380, 1890)),
        FeedNode(text = "合成文案 #话题​… 展开", id = "$pkg:id/element_caption_label", box = Box(30, 1910, 800, 2040)),
        FeedNode(desc = "拍摄", id = "$pkg:id/shoot_container", box = Box(437, 2076, 643, 2210))
    )

    private fun node(text: String = "", desc: String = "") = FeedNode(text = text, desc = desc, box = Box(300, 1800, 500, 1850))

    @Test fun douyinReadsOnlyTheCurrentCaptionAndCoversTheFeedArea() {
        for (pkg in listOf(douyin, "com.ss.android.ugc.aweme.lite")) {
            assertEquals(listOf(FeedCard("合成标题 #话题", Box(0, 210, 1080, 2100))), RealFeeds.read(pkg, douyinScreen(pkg)))
        }
        assertEquals("another app's ids never match", emptyList<FeedCard>(), RealFeeds.read(douyin, douyinScreen("com.ss.android.ugc.aweme.lite")))
    }

    @Test fun neighbouringItemsOutsideTheScreenAreIgnoredButTwoVisibleCaptionsAreRejected() {
        val offscreen = listOf(Box(33, 3812, 824, 2075), Box(33, 0, 824, -97), Box(33, 2180, 824, 2300)).map { FeedNode(text = "下一条", id = "$douyin:id/desc", box = it) }
        assertEquals("合成标题 #话题", RealFeeds.read(douyin, douyinScreen() + offscreen).single().title)
        val second = FeedNode(text = "下一条", id = "$douyin:id/desc", box = Box(33, 1500, 820, 1600))
        assertTrue(RealFeeds.read(douyin, douyinScreen() + second).isEmpty())
    }

    @Test fun douyinNeedsTheSelectedRecommendTabAndTheBottomNavigation() {
        assertTrue(RealFeeds.read(douyin, douyinScreen(tab = "推荐，按钮")).isEmpty())
        assertTrue(RealFeeds.read(douyin, douyinScreen(tab = "关注，已选中，按钮")).isEmpty())
        assertTrue(RealFeeds.read(douyin, douyinScreen().filterNot { it.desc == "首页，按钮" }).isEmpty())
        val lowTab = douyinScreen().map { if (it.desc.startsWith("推荐")) it.copy(box = Box(800, 1200, 940, 1320)) else it }
        assertTrue("a tab bar halfway down is not the full-screen feed", RealFeeds.read(douyin, lowTab).isEmpty())
    }

    @Test fun adsLivePreviewsCommentsAndLoginAreNeverRead() {
        assertTrue(RealFeeds.read(douyin, douyinScreen(caption = "合成短剧，赶紧看 广告 ")).isEmpty())
        val blockers = listOf(node(text = "开发者："), node(text = "立即下载"), node(desc = "立即下载"), node(desc = "点击进入直播间按钮"),
            node(text = "@合成店铺", desc = "@合成店铺直播中，按钮"), node(text = "12条评论"), node(text = "一键登录"), node(text = "已阅读并同意 用户协议"))
        for (blocker in blockers) assertTrue(blocker.toString(), RealFeeds.read(douyin, douyinScreen() + blocker).isEmpty())
        assertTrue(RealFeeds.read(kuaishou, kuaishouScreen() + node(text = "广告")).isEmpty())
        assertTrue(RealFeeds.read(douyin, douyinScreen() + FeedNode(className = "android.widget.EditText", box = Box(0, 2000, 1080, 2100))).isEmpty())
    }

    @Test fun kuaishouReadsTheCaptionWithoutTheAuthor() {
        assertEquals(listOf(FeedCard("合成文案 #话题", Box(0, 205, 1080, 2076))), RealFeeds.read(kuaishou, kuaishouScreen()))
        assertEquals("合成文案 #话题", RealFeeds.read("com.kuaishou.nebula", kuaishouScreen("com.kuaishou.nebula", "thanos_home_top_search")).single().title)
        assertTrue("no home search bar", RealFeeds.read(kuaishou, kuaishouScreen(search = "left_btn")).isEmpty())
        assertTrue("no bottom navigation", RealFeeds.read(kuaishou, kuaishouScreen().dropLast(1)).isEmpty())
        assertTrue("no caption, e.g. a short drama", RealFeeds.read(kuaishou, kuaishouScreen().filterNot { "caption" in it.id }).isEmpty())
    }

    @Test fun onlyTheSurveyedVersionsAreRead() {
        assertTrue(RealFeeds.supports(douyin, "40.6.0"))
        assertTrue(RealFeeds.supports("com.kuaishou.nebula", "14.8.40.10322"))
        assertTrue(RealFeeds.supports(kuaishou, "14.8.40"))
        assertTrue(RealFeeds.supports(RealFeeds.XIAOHONGSHU, "9.49.0"))
        for ((pkg, version) in listOf(douyin to "40.6.1", douyin to "40.6.0.1", "com.ss.android.ugc.aweme.lite" to null, kuaishou to "14.8.401",
            kuaishou to "14.8.4", RealFeeds.XIAOHONGSHU to "9.49.0.1", "com.tencent.mm" to "40.6.0")) assertFalse("$pkg $version", RealFeeds.supports(pkg, version))
    }

    @Test fun captionsLoseTheExpandMarkerAndMentions() {
        assertEquals("合成文案 #话题", videoTitle("合成文案 #话题​… 展开"))
        assertEquals("合成文案 问一下", videoTitle("合成文案 @朋友 问一下... 展开>"))
        assertEquals("第一行 第二行", videoTitle("第一行\n第二行 >"))
        for (value in listOf("字", "@朋友 展开", "字".repeat(1001))) assertNull(value.take(20), videoTitle(value))
    }

    @Test fun xiaohongshuKeepsItsDiscoveryCardRules() {
        val tabs = listOf(FeedNode(desc = "首页", box = Box(0, 2100, 200, 2200), selected = true), FeedNode(desc = "发现", box = Box(400, 90, 520, 200), selected = true))
        val list = FeedNode(className = "androidx.recyclerview.widget.RecyclerView", box = Box(0, 300, 1080, 2100), scrollable = true)
        val left = FeedNode(desc = "笔记  合成卡片 来自作者 12赞", box = Box(10, 320, 535, 1100))
        val right = FeedNode(desc = "视频  合成视频 来自作者 3万赞", box = Box(545, 320, 1070, 1100))
        val screen = listOf(root) + tabs + list + left + right
        assertEquals(listOf("合成卡片", "合成视频"), RealFeeds.read(RealFeeds.XIAOHONGSHU, screen).map { it.title })
        assertEquals(listOf("合成卡片"), RealFeeds.read(RealFeeds.XIAOHONGSHU, screen + FeedNode(text = "广告", box = Box(600, 1000, 700, 1050))).map { it.title })
        assertTrue(RealFeeds.read(RealFeeds.XIAOHONGSHU, screen - tabs[1]).isEmpty())
        assertTrue(RealFeeds.read(RealFeeds.XIAOHONGSHU, screen + list).isEmpty())
    }
}
