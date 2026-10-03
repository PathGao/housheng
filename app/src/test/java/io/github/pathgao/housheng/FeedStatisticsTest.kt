package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class FeedStatisticsTest {
    private val item = PageToken(AppCatalog.FIXTURE, "post", 1, 1000, ContentKind.USER_CONTENT)
    @Test fun unclassifiedIsNotInventedAsAContentCategory() {
        val statistics = FeedStatistics()
        statistics.record(item)
        assertEquals(mapOf(ContentTopic.UNCLASSIFIED to 1), statistics.counts(true))
        assertTrue(statistics.counts(false).isEmpty())
    }
    @Test fun sameDisplayIsCountedOnceAndClearRemovesCounts() {
        val statistics = FeedStatistics()
        statistics.record(item)
        statistics.record(item)
        assertEquals(1, statistics.counts(true).values.sum())
        statistics.record(item.copy(item = "next", generation = 2), ContentTopic.LIFE)
        assertEquals(2, statistics.counts(true).values.sum())
        statistics.clear()
        assertTrue(statistics.counts(true).isEmpty())
    }
    @Test fun adsUnknownAndUnadaptedSourcesAreNotContentSamples() {
        val statistics = FeedStatistics()
        for (kind in listOf(ContentKind.AD, ContentKind.UNKNOWN)) statistics.record(item.copy(kind = kind))
        statistics.record(item.copy(source = "com.android.chrome"))
        statistics.record(item.copy(source = "com.xingin.xhs"))
        assertTrue(statistics.counts(true).isEmpty())
        assertTrue(statistics.counts(false).isEmpty())
    }
}
