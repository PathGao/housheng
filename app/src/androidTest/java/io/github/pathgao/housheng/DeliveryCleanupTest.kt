package io.github.pathgao.housheng

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class DeliveryCleanupTest {
    @Test fun removeTestStateAndKeepCredentials() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("cleanup") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val credentials = ClefCredentials(context).load()
        instrumentation.runOnMainSync {
            Session.stop(); Session.clear()
            ModelValidation.realEnabled = false; ModelValidation.enabled = false
            for (kind in listOf("pages", "notifications")) Preferences(context).select(kind, Session.FIXTURE, false)
        }
        assertTrue(context.getSharedPreferences("settings", 0).edit().commit())
        ReportStore(context).use { store ->
            if (store.snapshot().notifications.isEmpty()) store.clear()
            else {
                store.writableDatabase.delete("notices", "source = ?", arrayOf(Session.FIXTURE))
                store.writableDatabase.delete("recording", "test = 1", null)
            }
        }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        File(context.filesDir, "clef-test.json").delete()
        for (name in listOf("family-home.png", "family-notifications.png", "family-report.png")) File(context.getExternalFilesDir(null), name).delete()
        assertTrue(ClefCredentials(context).load() == credentials)
        assertFalse(Session.FIXTURE in Preferences(context).selected("pages"))
        assertFalse(Session.FIXTURE in Preferences(context).selected("notifications"))
    }
}
