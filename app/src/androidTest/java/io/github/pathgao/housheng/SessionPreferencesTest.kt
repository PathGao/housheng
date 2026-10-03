package io.github.pathgao.housheng

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SessionPreferencesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @After fun restoreObservation() {
        instrumentation.runOnMainSync {
            Session.stop()
            Preferences(context).setAlwaysAllow(Session.FIXTURE, false)
        }
    }

    @Test fun explicitPauseIsPersistedAndRepeatedInitializationCannotResumeIt() {
        instrumentation.runOnMainSync {
            Session.initialize(context)
            Session.notificationExecution = true
            assertTrue(prefs.getBoolean("notificationExecution", false))
            Session.stop()
            assertFalse(prefs.getBoolean("notificationExecution", true))
            Session.initialize(context)
            assertFalse(Session.notificationExecution)
            assertFalse(Session.feedExecution)
        }
    }

    @Test fun alwaysAllowSurvivesPreferencesRecreationAndRejectsUnknownApps() {
        val preferences = Preferences(context)
        preferences.setAlwaysAllow(Session.FIXTURE, true)
        assertTrue(Preferences(context).alwaysAllow(Session.FIXTURE))
        preferences.setAlwaysAllow("com.android.chrome", true)
        assertFalse(Preferences(context).alwaysAllow("com.android.chrome"))
        preferences.setAlwaysAllow(Session.FIXTURE, false)
        assertFalse(Preferences(context).alwaysAllow(Session.FIXTURE))
    }

    @Test fun disconnectCallbackKeepsChoiceAndPauseWinsAtReconnect() {
        instrumentation.runOnMainSync {
            val service = requireNotNull(NotificationService.instance) { "Run after DeviceValidationTest connects services" }
            Session.notificationExecution = true
            try {
                service.onListenerDisconnected()
                assertTrue(Session.notificationExecution)
                assertTrue(prefs.getBoolean("notificationExecution", false))
                assertEquals(ServiceStatus.DISCONNECTED, ServiceHealth(context).notificationStatus())
                service.onListenerConnected()
                assertEquals(ServiceStatus.EXECUTING, ServiceHealth(context).notificationStatus())
                service.onListenerDisconnected()
                Session.stop()
                service.onListenerConnected()
                assertEquals(ServiceStatus.OBSERVING, ServiceHealth(context).notificationStatus())
            } finally { service.onListenerConnected() }
        }
    }
}
