package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class DecisionPolicyTest {
    private val page = PageToken("fixture", "post-1", 1, 1000, ContentKind.USER_CONTENT)
    private fun gate() = ActionGate().apply { enabled = true; current = page }

    @Test fun keywordMatchRequiresNonemptyRule() {
        assertEquals(Decision.SKIP, KeywordClassifier(listOf("限时抢购")).classify("广告 限时抢购"))
        assertEquals(Decision.KEEP, KeywordClassifier(listOf("", "  ")).classify("普通内容"))
        assertEquals(Decision.KEEP, KeywordClassifier(listOf("限时抢购")).classify("散步"))
    }
    @Test fun currentDecisionActsOnce() {
        val gate = gate()
        assertTrue(gate.claim(page, Decision.SKIP, 1200))
        assertFalse(gate.claim(page, Decision.SKIP, 1250))
    }
    @Test fun advertisementsAndUnknownContentNeverAct() {
        for (kind in listOf(ContentKind.AD, ContentKind.UNKNOWN)) {
            val protected = page.copy(kind = kind)
            assertFalse(gate().apply { current = protected }.claim(protected, Decision.SKIP, 1200))
        }
    }
    @Test fun observeAndKeepNeverAct() {
        assertFalse(gate().apply { enabled = false }.claim(page, Decision.SKIP, 1200))
        assertFalse(gate().claim(page, Decision.KEEP, 1200))
        assertFalse(gate().apply { current = null }.claim(page, Decision.SKIP, 1200))
    }
    @Test fun stalePageSourceOrGenerationNeverActs() {
        for (next in listOf(page.copy(item = "next"), page.copy(source = "other"), page.copy(generation = 2))) {
            assertFalse(gate().apply { current = next }.claim(page, Decision.SKIP, 1200))
        }
    }
    @Test fun deadlineIsExclusiveAndRejectsClockReversal() {
        assertTrue(gate().claim(page, Decision.SKIP, 1499))
        assertFalse(gate().claim(page, Decision.SKIP, 1500))
        assertFalse(gate().claim(page, Decision.SKIP, 999))
    }
    @Test fun nextPageCanActButRejectedDecisionDoesNotConsumePage() {
        val gate = gate()
        assertFalse(gate.claim(page, Decision.KEEP, 1200))
        assertTrue(gate.claim(page, Decision.SKIP, 1200))
        val next = page.copy(item = "next", generation = 2, observedAt = 1250)
        gate.current = next
        assertTrue(gate.claim(next, Decision.SKIP, 1300))
    }
    @Test fun dismissOnlyOrdinaryClearableNotifications() {
        assertTrue(canDismissNotification(true, false, false, "promo"))
        assertTrue(canDismissNotification(true, false, false, null))
        assertFalse(canDismissNotification(false, false, false, null))
        assertFalse(canDismissNotification(true, true, false, null))
        assertFalse(canDismissNotification(true, false, true, null))
        for (category in listOf("call", "alarm", "msg", "email", "event", "reminder", "transport", "service", "navigation", "sys", "err", "progress")) {
            assertFalse(category, canDismissNotification(true, false, false, category))
        }
    }
}
