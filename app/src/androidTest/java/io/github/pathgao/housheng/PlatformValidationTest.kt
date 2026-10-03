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

    @Test fun inventoryRequiresExplicitScanAndCanBeCleared() {
        val activity = open(InventoryActivity::class.java)
        try {
            onMain {
                val all = views(activity.window.decorView)
                assertTrue(all.filterIsInstance<TextView>().any { it.text.contains("尚未读取") })
                all.filterIsInstance<Button>().single { it.text == "读取 / 刷新清单" }.performClick()
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
            onMain {
                views(activity.window.decorView).filterIsInstance<Button>().single { it.text == "清空本页清单" }.performClick()
                val list = views(activity.window.decorView).filterIsInstance<ListView>().single()
                assertEquals(0, list.adapter.count - list.headerViewsCount - list.footerViewsCount)
            }
        } finally { onMain { activity.finish() } }
    }
}
