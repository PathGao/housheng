package io.github.pathgao.housheng

import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class PlatformValidationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun open(type: Class<out Activity>): Activity {
        // MIUI blocks launches from the background after instrumentation force-stops its target.
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .executeShellCommand("am start -W -n ${context.packageName}/.MainActivity").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        return instrumentation.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Test fun realAppPagesCannotBeSelected() {
        val activity = open(DiagnosticsActivity::class.java)
        try {
            onMain {
                val choices = views(activity.window.decorView).filterIsInstance<CheckBox>()
                for ((source, name) in AppCatalog.sources) {
                    val choice = choices.single { it.contentDescription == "${name}页面" }
                    assertEquals(source, source == AppCatalog.FIXTURE, choice.isEnabled)
                }
            }
        } finally { onMain { activity.finish() } }
    }

    @Test fun staleSelectionsCannotAuthorizeBrowserOrUnadaptedPages() {
        val prefs = context.getSharedPreferences("settings", 0)
        val previous = prefs.getStringSet("pages", emptySet())!!.toSet()
        try {
            prefs.edit().putStringSet("pages", setOf("com.android.chrome", "com.xingin.xhs", AppCatalog.FIXTURE)).commit()
            assertEquals(setOf(AppCatalog.FIXTURE), Preferences(context).selected("pages"))
        } finally { prefs.edit().putStringSet("pages", previous).commit() }
    }

    @Test fun inventoryRequiresExplicitScan() {
        val activity = open(InventoryActivity::class.java)
        try {
            onMain {
                val all = views(activity.window.decorView)
                assertTrue(all.filterIsInstance<TextView>().any { it.text.contains("还没读取") })
                all.filterIsInstance<Button>().single { it.text == "读取应用清单" }.performClick()
            }
            val deadline = Session.now() + 10000
            var count = 0
            var includesSelf = false
            while (Session.now() < deadline) {
                onMain {
                    val adapter = views(activity.window.decorView).filterIsInstance<ListView>().single().adapter
                    count = adapter?.count ?: 0
                    includesSelf = (0 until count).any { adapter.getItem(it)?.toString()?.contains(context.packageName) == true }
                }
                if (includesSelf) break
                Thread.sleep(50)
            }
            assertTrue("应查询到本应用的启动器入口，实际数量=$count", includesSelf)
        } finally { onMain { activity.finish() } }
    }

    @Test fun inventoryRowsRenderAtLargestFontScale() {
        val previous = DeviceUi.shell("settings get system font_scale").trim().takeUnless { it == "null" } ?: "1.0"
        DeviceUi.shell("settings put system font_scale 2.0")
        try {
            val deadline = Session.now() + 10000
            while (context.resources.configuration.fontScale < 2f && Session.now() < deadline) Thread.sleep(50)
            val activity = open(InventoryActivity::class.java)
            try {
                onMain {
                    assertTrue(ProductUi.largeText(activity))
                    views(activity.window.decorView).filterIsInstance<Button>().single { it.text == "读取应用清单" }.performClick()
                }
                var rendered = false
                while (!rendered && Session.now() < deadline) {
                    Thread.sleep(50)
                    onMain { rendered = views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.startsWith("后生\n") } }
                }
                assertTrue("大字号下应用行应正常显示", rendered)
            } finally { onMain { activity.finish() } }
        } finally { DeviceUi.shell("settings put system font_scale $previous") }
    }
}
