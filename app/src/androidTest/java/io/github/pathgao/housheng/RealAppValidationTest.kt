package io.github.pathgao.housheng

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Runs only when explicitly selected on an unlocked phone with Xiaohongshu 9.49.0. */
class RealAppValidationTest {
    @Test fun classifyVisibleDiscoveryCardsInTheRealApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        DeviceUi.automation.apply {
            serviceInfo = serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }
        DevicePreparation().reconnectServicesForInstrumentation()
        instrumentation.runOnMainSync {
            Session.stop(); Session.clear()
            ModelValidation.realEnabled = true
            Session.feedExecution = true
        }
        DeviceUi.shell("am start -W -f 0x10008000 -n com.xingin.xhs/.index.v2.IndexActivityV2")
        val end = Session.now() + 15000
        var classified = false
        while (Session.now() < end) {
            instrumentation.runOnMainSync { classified = Session.journal().contains("小红书 Clef ·") }
            if (classified) break
            Thread.sleep(100)
        }
        assertTrue("Real discovery cards must reach Clef: ${Session.journal()}", classified)
        Thread.sleep(4000)
        instrumentation.sendStatus(0, Bundle().apply { putString("realFeed", Session.journal()) })
        val root = DeviceUi.automation.rootInActiveWindow ?: error("No public feed")
        val card = try { XiaohongshuFeed.read(root).first() } finally { root.recycle() }
        val prefs = Preferences(instrumentation.targetContext)
        val originalRules = prefs.ruleText()
        try {
            instrumentation.runOnMainSync {
                prefs.saveRules(card.title)
                FeedService.instance!!.invalidate()
                FeedService.instance!!.onAccessibilityEvent(android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            }
            Thread.sleep(800)
            assertTrue(Session.journal(), Session.journal().contains("小红书 · 卡片已遮挡"))
            fun revealNode(): android.view.accessibility.AccessibilityNodeInfo? {
                for (window in DeviceUi.automation.windows) {
                    val windowRoot = window.root ?: continue
                    try {
                        if (windowRoot.packageName?.toString() != instrumentation.targetContext.packageName) continue
                        val nodes = windowRoot.findAccessibilityNodeInfosByText("显示这条")
                        val found = nodes.firstOrNull { it.text?.toString() == "显示这条" }
                        val copy = found?.let { android.view.accessibility.AccessibilityNodeInfo.obtain(it) }
                        nodes.forEach { it.recycle() }
                        if (copy != null) return copy
                    } finally { windowRoot.recycle() }
                }
                return null
            }
            DeviceUi.automation.takeScreenshot()?.let { bitmap ->
                java.io.File(instrumentation.targetContext.cacheDir, "real-mask.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val reveal = revealNode()
            assertNotNull("Real accessibility overlay must be visible", reveal)
            try { assertTrue(reveal!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) } finally { reveal?.recycle() }
            Thread.sleep(300)
            assertNull("Reveal must remove the mask", revealNode())
            assertTrue(Session.journal().contains("用户恢复显示"))
            instrumentation.runOnMainSync { FeedService.instance!!.onAccessibilityEvent(android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED)) }
            assertNull("Scrolling must leave no stale overlay", revealNode())
            instrumentation.sendStatus(0, Bundle().apply { putString("realMask", "Real card keyword mask and reveal passed") })
        } finally {
            instrumentation.runOnMainSync { prefs.saveRules(originalRules); Session.stop(); ModelValidation.realEnabled = false }
        }
    }
}
