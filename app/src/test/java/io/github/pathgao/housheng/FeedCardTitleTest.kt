package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class FeedCardTitleTest {
    @Test fun onlyPublicCardTitlesReachTheModel() {
        assertEquals("针线小技巧", publicFeedTitle("视频  针线小技巧 来自作者 2305赞"))
        assertEquals("标题里提到 来自远方", publicFeedTitle("笔记  标题里提到 来自远方 来自作者 1.2万赞"))
        for (value in listOf("消息，6条未读", "作者发来一条私信", "视频  标题", "视频  标题 来自作者", "视频  来自作者 1赞", "视频  " + "字".repeat(2000) + " 来自作者 1赞")) {
            assertNull(value.take(60), publicFeedTitle(value))
        }
    }
}
