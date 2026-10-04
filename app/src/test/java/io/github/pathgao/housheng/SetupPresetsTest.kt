package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class SetupPresetsTest {
    private val all = SetupPresets.packs.indices.toSet()

    @Test fun freshRulesStartWithEveryGroup() {
        assertEquals(all, SetupPresets.chosen("震惊内幕"))
        assertEquals(all, SetupPresets.chosen(""))
    }
    @Test fun mergeKeepsOwnWordsAndRoundTrips() {
        val rules = SetupPresets.merge("我的词\n红包", setOf(1))
        assertTrue(rules.lines().first() == "我的词")
        assertFalse("红包" in rules.lines())
        assertEquals(setOf(1), SetupPresets.chosen(rules))
        assertEquals("我的词", SetupPresets.merge(rules, emptySet()))
    }
    @Test fun partialGroupIsNotChosen() {
        assertEquals(emptySet<Int>(), SetupPresets.chosen("红包"))
    }
    @Test fun everyGroupFitsTheRuleLimit() {
        assertTrue(SetupPresets.merge("", all).lines().size <= 32)
    }
}
