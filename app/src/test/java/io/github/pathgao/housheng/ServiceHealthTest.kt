package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class ServiceHealthTest {
    @Test fun authorizationLossAlwaysDisablesActions() {
        for (connected in listOf(false, true)) for (requested in listOf(false, true)) {
            val status = ServiceStatus.resolve(false, connected, requested)
            assertEquals(ServiceStatus.UNAUTHORIZED, status)
            assertFalse(status.executing)
        }
    }

    @Test fun disconnectRetainsChoiceButCannotExecuteUntilReconnected() {
        var saved = false
        val choice = ExecutionChoice(saved) { saved = it }
        choice.requested = true
        assertEquals(ServiceStatus.DISCONNECTED, ServiceStatus.resolve(true, false, choice.requested))
        val rebuilt = ExecutionChoice(saved) { saved = it }
        assertEquals(ServiceStatus.EXECUTING, ServiceStatus.resolve(true, true, rebuilt.requested))
        rebuilt.requested = false
        val afterPause = ExecutionChoice(saved) { saved = it }
        assertEquals(ServiceStatus.OBSERVING, ServiceStatus.resolve(true, true, afterPause.requested))
    }

    @Test fun missingSettingsPageFallsBackToAPageWithTheSameSwitch() {
        val shipped = setOf(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        for (action in listOf(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) {
            val tried = mutableListOf<String>()
            assertTrue(launchFirst(settingsPages(action)) { tried.add(it); if (it !in shipped) throw IllegalStateException("no activity") })
            assertEquals(action, tried.first())
            assertTrue(tried.last() in shipped)
        }
        assertFalse(launchFirst(settingsPages(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) { throw SecurityException("not exported") })
    }

    @Test fun firstUseObservesAndDisconnectedDoesNotLookUnauthorized() {
        assertEquals(ServiceStatus.OBSERVING, ServiceStatus.resolve(true, true, false))
        val disconnected = ServiceStatus.resolve(true, false, false)
        assertTrue(disconnected.authorized)
        assertFalse(disconnected.connected)
        assertFalse(disconnected.executing)
    }
}
