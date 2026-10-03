package io.github.pathgao.housheng

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ClefConfigurationTest {
    @Test fun installPrivateTestCredentials() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.filesDir, "clef-test.json")
        val config = try { JSONObject(file.readText()) } finally { file.delete() }
        val store = ClefCredentials(context)
        store.save(config.getString("account"), config.getString("token"))
        store.direct = InstrumentationRegistry.getArguments().getString("modelTransport") == "direct"
        val loaded = ClefCredentials(context).load()
        assertEquals(config.getString("account"), loaded?.first)
        assertTrue("encrypted credentials must round-trip", loaded?.second == config.getString("token"))
        val persisted = File(context.applicationInfo.dataDir, "shared_prefs/clef.xml").readText()
        assertFalse("token must not be stored as plaintext", persisted.contains(config.getString("token")))
        assertFalse("account must not be stored as plaintext", persisted.contains(config.getString("account")))
    }

    @Test fun configurationPageSavesAndMasksCredentials() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = ClefCredentials(context)
        val original = store.load()
        DeviceUi.shell("am start -W -f 0x10008000 -n io.github.pathgao.housheng/.MainActivity")
        val monitor = instrumentation.addMonitor(ClefSettingsActivity::class.java.name, null, false)
        instrumentation.runOnMainSync {
            context.startActivity(android.content.Intent(context, ClefSettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val activity = monitor.waitForActivityWithTimeout(5000)
        assertNotNull("configuration page must open", activity)
        try {
            instrumentation.runOnMainSync {
                val views = mutableListOf<android.view.View>()
                fun visit(view: android.view.View) {
                    views.add(view)
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
                visit(activity.window.decorView)
                val inputs = views.filterIsInstance<android.widget.EditText>()
                assertEquals(2, inputs.size)
                inputs[0].setText("b".repeat(32))
                inputs[1].setText("synthetic-test-token")
                assertTrue(inputs[1].transformationMethod is android.text.method.PasswordTransformationMethod)
                views.filterIsInstance<android.widget.Button>().single { it.text == "保存凭据" }.performClick()
                assertTrue("token input must clear after saving", inputs[1].text.isEmpty())
            }
            assertEquals("b".repeat(32), store.load()?.first)
            assertTrue(store.load()?.second == "synthetic-test-token")
        } finally {
            if (original != null) store.save(original.first, original.second) else store.clear()
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.removeMonitor(monitor)
        }
    }
}
