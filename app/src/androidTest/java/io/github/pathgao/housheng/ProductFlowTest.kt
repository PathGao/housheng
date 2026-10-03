package io.github.pathgao.housheng

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import java.io.File
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.runner.intent.IntentStubberRegistry
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ProductFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun texts(activity: Activity) = views(activity.window.decorView).filterIsInstance<TextView>().joinToString("\n") { it.text }
    private fun button(activity: Activity, text: String) = views(activity.window.decorView).filterIsInstance<Button>().single { it.text == text }
    private fun row(activity: Activity, label: String): View {
        var view: View = views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == label }
        while (!view.isClickable) view = view.parent as View
        return view
    }
    private fun home(): Activity {
        DeviceUi.shell("am start -W -f 0x10008000 -n ${context.packageName}/.MainActivity")
        var activity: Activity? = null
        val deadline = Session.now() + 5000
        while (Session.now() < deadline) {
            onMain {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull { it is MainActivity && it.hasWindowFocus() }
                activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            if (activity != null) return activity!!
            Thread.sleep(50)
        }
        error("Home must be resumed and focused after shell launch")
    }
    private fun waitFocused(activity: Activity) {
        val deadline = Session.now() + 5000
        while (Session.now() < deadline) {
            var focused = false
            onMain { focused = activity.hasWindowFocus() }
            if (focused) return
            Thread.sleep(50)
        }
        fail("Activity must be focused before interaction")
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        val bitmap = requireNotNull(DeviceUi.automation.takeScreenshot())
        try {
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun homeNavigatesToNotificationSettingsWithoutAskingForAccessibility() {
        val main = home()
        val monitor = instrumentation.addMonitor(NotificationsActivity::class.java.name, null, false)
        var target: Activity? = null
        try {
            capture("family-home")
            onMain {
                assertFalse(texts(main).contains("最近页面"))
                assertFalse(texts(main).contains("5秒后截图"))
                row(main, "通知清理").performClick()
            }
            target = monitor.waitForActivityWithTimeout(5000)
            assertNotNull("应进入通知管理", target)
            waitFocused(target!!)
            capture("family-notifications")
            onMain {
                val labels = texts(target!!)
                assertTrue(labels.contains("管理的应用"))
                assertFalse(labels.contains("打开无障碍设置"))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            onMain { target?.finish(); main.finish() }
        }
    }

    @Test fun foregroundStatusTracksDisconnectionAndReconnection() {
        val main = home()
        try {
            onMain {
                val service = requireNotNull(NotificationService.instance)
                Session.notificationExecution = true
                try {
                    service.onListenerDisconnected()
                    assertTrue(texts(main).contains(notificationCopy(ServiceStatus.DISCONNECTED).headline))
                    service.onListenerConnected()
                    assertTrue(texts(main).contains(notificationCopy(ServiceStatus.EXECUTING).headline))
                    button(main, "暂停自动清理").performClick()
                    assertTrue(texts(main).contains(notificationCopy(ServiceStatus.OBSERVING).headline))
                    assertFalse(Preferences(context).selected("pages").any { it != Session.FIXTURE })
                } finally { service.onListenerConnected(); Session.stop() }
            }
        } finally { onMain { main.finish() } }
    }

    @Test fun reportMustLoadBeforeSharingAndDoesNotReadInventoryByDefault() {
        val main = home()
        val monitor = instrumentation.addMonitor(ReportActivity::class.java.name, null, false)
        var activity: Activity? = null
        var shared: Intent? = null
        val attempted = mutableListOf<String?>()
        IntentStubberRegistry.load { intent ->
            attempted.add(intent.action)
            if (intent.action in setOf(Intent.ACTION_CHOOSER, "miui.intent.action.MIUI_CHOOSER")) {
                shared = intent.getParcelableExtra(Intent.EXTRA_INTENT)
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            } else null
        }
        try {
            onMain { row(main, "家庭报告").performClick() }
            activity = monitor.waitForActivityWithTimeout(5000)
            assertNotNull(activity)
            val reportActivity = activity!!
            waitFocused(reportActivity)
            val deadline = Session.now() + 10000
            var ready = false
            while (Session.now() < deadline) {
                onMain { ready = button(reportActivity, "分享给家人").isEnabled }
                if (ready) break
                Thread.sleep(50)
            }
            assertTrue("报告应完成生成", ready)
            capture("family-report")
            onMain {
                assertTrue(views(reportActivity.window.decorView).filterIsInstance<Switch>().none { it.isChecked })
                button(reportActivity, "复制").performClick()
                val clipboard = reportActivity.getSystemService(android.content.ClipboardManager::class.java)
                assertTrue(clipboard.primaryClip?.getItemAt(0)?.text.toString().contains("后生 · 家庭报告"))
                clipboard.clearPrimaryClip()
                button(reportActivity, "分享给家人").performClick()
            }
            assertEquals("Intents: $attempted", Intent.ACTION_SEND, shared?.action)
            assertEquals("text/plain", shared?.type)
            val text = shared?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            assertTrue(text.contains("后生 · 家庭报告"))
            assertTrue(text.contains("尚未读取安装清单"))
            assertFalse(text.contains("验证场测试数据"))
        } finally {
            instrumentation.removeMonitor(monitor); IntentStubberRegistry.reset()
            onMain { activity?.finish(); main.finish() }
        }
    }
}
