package io.github.pathgao.housheng

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ReportStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = "report-store-test.db"
    private var now = System.currentTimeMillis()
    private lateinit var store: ReportStore
    private val source = "com.xingin.xhs"

    @Before fun prepare() {
        context.deleteDatabase(database)
        store = ReportStore(context, database) { now }
    }
    @After fun cleanup() { store.close(); context.deleteDatabase(database) }

    @Test fun replayIsDeduplicatedAndUpdatedNotificationCountsAgain() {
        store.recordNotification(source, "private-key:100", NoticeOutcome.REMOVAL_REQUESTED, now)
        store.recordNotification(source, "private-key:100", NoticeOutcome.REMOVAL_REQUESTED, now)
        store.recordNotification(source, "private-key:101", NoticeOutcome.PROTECTED, now)
        assertEquals(NoticeSummary(source, 2, 1, 1, 1), store.snapshot().notifications.single())
        store.close()
        assertFalse(context.getDatabasePath(database).readBytes().toString(Charsets.ISO_8859_1).contains("private-key"))
        store = ReportStore(context, database) { now }
        store.recordNotification(source, "private-key:100", NoticeOutcome.ALLOWED, now)
        assertEquals(2, store.snapshot().notifications.single().received)
    }

    @Test fun fixtureAndUnknownSourcesCannotPolluteFamilyTotals() {
        store.recordNotification(source, "real", NoticeOutcome.MATCHED, now)
        store.recordNotification(AppCatalog.FIXTURE, "test", NoticeOutcome.ALLOWED, now)
        store.recordNotification("com.android.chrome", "browser", NoticeOutcome.ALLOWED, now)
        assertEquals(listOf(source), store.snapshot().notifications.map { it.source })
        assertEquals(listOf(AppCatalog.FIXTURE), store.snapshot(testData = true).notifications.map { it.source })
    }

    @Test fun sameEventInDifferentSourcesAndExpiredReplayHaveExplicitCounting() {
        store.recordNotification(source, "same-event", NoticeOutcome.ALLOWED, now)
        store.recordNotification("com.smile.gifmaker", "same-event", NoticeOutcome.ALLOWED, now)
        assertEquals(2, store.snapshot().notifications.sumOf { it.received })
        now += 86400001L
        store.recordNotification(source, "same-event", NoticeOutcome.ALLOWED, now)
        assertEquals(2, store.snapshot().notifications.single { it.source == source }.received)
    }

    @Test fun timeWindowUsesCalendarDaysAndPrunesOldRecords() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        store.recordNotification(source, "before", NoticeOutcome.ALLOWED, start - 1)
        store.recordNotification(source, "boundary", NoticeOutcome.ALLOWED, start)
        store.recordNotification(source, "today", NoticeOutcome.FAILED, now)
        store.recordNotification(source, "future", NoticeOutcome.ALLOWED, now + 1)
        assertEquals(start, store.snapshot().startAt)
        assertEquals(NoticeSummary(source, 2, 1, 0, 0), store.snapshot().notifications.single())
        now = today.plusDays(31).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(store.snapshot(30).notifications.isEmpty())
        now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(store.snapshot(30).notifications.isEmpty())
    }

    @Test fun concurrentCallbacksRemainAtomicAndClearRemovesAllReports() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until 40).map { index -> executor.submit {
                store.recordNotification(source, "key-${index % 20}", NoticeOutcome.ALLOWED, now)
            } }
            futures.forEach { it.get() }
            assertEquals(20, store.snapshot().notifications.single().received)
        } finally { executor.shutdownNow() }
        store.recordService("pages", "connected", now)
        store.recordService("pages", "private arbitrary message", now)
        assertEquals(1, store.snapshot().serviceEvents.size)
        store.clear()
        assertTrue(store.snapshot().notifications.isEmpty())
        assertTrue(store.snapshot().serviceEvents.isEmpty())
        assertNull(store.snapshot().firstRecordedAt)
        store.recordNotification(source, "key-0", NoticeOutcome.ALLOWED, now)
        assertEquals(1, store.snapshot().notifications.single().received)
    }
}
