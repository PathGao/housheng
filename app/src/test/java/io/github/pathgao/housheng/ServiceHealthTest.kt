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

    @Test fun firstUseObservesAndDisconnectedDoesNotLookUnauthorized() {
        assertEquals(ServiceStatus.OBSERVING, ServiceStatus.resolve(true, true, false))
        val disconnected = ServiceStatus.resolve(true, false, false)
        assertTrue(disconnected.authorized)
        assertFalse(disconnected.connected)
        assertFalse(disconnected.executing)
    }
}
