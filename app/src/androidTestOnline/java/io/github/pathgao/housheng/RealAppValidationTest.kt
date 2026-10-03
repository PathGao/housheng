package io.github.pathgao.housheng

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Runs only when explicitly selected on an unlocked phone with Xiaohongshu 9.49.0. */
class RealAppValidationTest {
    @Test fun classifyVisibleDiscoveryCardsInTheRealApp() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realApps") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        DeviceUi.automation.apply {
            serviceInfo = serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }
        val prefs = Preferences(instrumentation.targetContext)
        val originalRules = prefs.ruleText()
        DevicePreparation().reconnectServicesForInstrumentation()
        // -e maskPath frame forces the screenshot blur fallback on phones that support live blur.
        val prefix = if (InstrumentationRegistry.getArguments().getString("maskPath") == "frame") "frame-" else ""
        fun snap(name: String) = DeviceUi.automation.takeScreenshot()?.let { bitmap ->
            java.io.File(instrumentation.targetContext.cacheDir, "$prefix$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            instrumentation.runOnMainSync {
            Session.stop(); Session.clear()
            prefs.saveRules("")
            ModelValidation.realEnabled = setOf(RealFeeds.XIAOHONGSHU)
            Session.feedExecution = true
            FeedMask.liveBlurAllowed = prefix.isEmpty()
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
            val card = try { readFeed(root).first() } finally { root.recycle() }
            snap("before")
            instrumentation.runOnMainSync {
                prefs.saveRules(card.title)
                FeedService.instance!!.invalidate()
                FeedService.instance!!.onAccessibilityEvent(android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            }
            // The screenshot blur fallback waits for one screenshot before the cover appears.
            val maskEnd = Session.now() + 3000
            while (Session.now() < maskEnd && !Session.journal().contains("小红书 · 已遮挡")) Thread.sleep(100)
            Thread.sleep(300)
            assertTrue(Session.journal(), Session.journal().contains("小红书 · 已遮挡"))
            instrumentation.sendStatus(0, Bundle().apply { putString("maskStyle", Session.journal().lines().first { "小红书 · 已遮挡" in it }) })
            fun revealNode(at: android.graphics.Rect? = null): android.view.accessibility.AccessibilityNodeInfo? {
                for (window in DeviceUi.automation.windows) {
                    val windowRoot = window.root ?: continue
                    try {
                        if (windowRoot.packageName?.toString() != instrumentation.targetContext.packageName) continue
                        val nodes = windowRoot.findAccessibilityNodeInfosByText("显示这条")
                        val found = nodes.firstOrNull { it.text?.toString() == "显示这条" && (at == null || android.graphics.Rect().also(it::getBoundsInScreen) == at) }
                        val copy = found?.let { android.view.accessibility.AccessibilityNodeInfo.obtain(it) }
                        nodes.forEach { it.recycle() }
                        if (copy != null) return copy
                    } finally { windowRoot.recycle() }
                }
                return null
            }
            snap("blurred")
            val reveal = revealNode()
            assertNotNull("Real accessibility overlay must be visible", reveal)
            val clickedBounds = android.graphics.Rect().also(reveal!!::getBoundsInScreen)
            try { assertTrue(reveal!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) } finally { reveal?.recycle() }
            Thread.sleep(300)
            assertNull("Reveal must remove the selected mask", revealNode(clickedBounds))
            assertTrue(Session.journal().contains("用户恢复显示"))
            snap("restored")
            instrumentation.runOnMainSync { FeedService.instance!!.onAccessibilityEvent(android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED)) }
            assertNull("Scrolling must leave no stale overlay", revealNode())
            instrumentation.sendStatus(0, Bundle().apply { putString("realMask", "Real card keyword mask and reveal passed") })
            fun listNode(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int = 0): android.view.accessibility.AccessibilityNodeInfo? {
                if (depth > 30) return null
                if (node.isVisibleToUser && node.isScrollable && node.className?.toString() == "androidx.recyclerview.widget.RecyclerView") return android.view.accessibility.AccessibilityNodeInfo.obtain(node)
                for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                    val found = try { listNode(child, depth + 1) } finally { child.recycle() }
                    if (found != null) return found
                }
                return null
            }
            val before = DeviceUi.automation.rootInActiveWindow ?: error("No feed before scroll")
            val oldTitles = try { readFeed(before).map { it.title } } finally { before.recycle() }
            instrumentation.runOnMainSync { prefs.saveRules(""); Session.clear() }
            val scrollRoot = DeviceUi.automation.rootInActiveWindow ?: error("No feed")
            val list = try { listNode(scrollRoot) } finally { scrollRoot.recycle() }
            try { assertTrue("Real RecyclerView must scroll", list?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) } finally { list?.recycle() }
            val scrollEnd = Session.now() + 10000
            var changed = false
            var resumed = false
            while (Session.now() < scrollEnd) {
                val latest = DeviceUi.automation.rootInActiveWindow
                if (latest != null) try {
                    val titles = readFeed(latest).map { it.title }
                    changed = titles.isNotEmpty() && titles != oldTitles
                } finally { latest.recycle() }
                instrumentation.runOnMainSync { resumed = Session.journal().contains("小红书 Clef ·") }
                if (changed && resumed) break
                Thread.sleep(100)
            }
            assertTrue("Actual scroll must reveal different cards", changed)
            assertTrue("Clef must resume after actual scroll: ${Session.journal()}", resumed)
            DeviceUi.shell("am start -W -f 0x10008000 -n io.github.pathgao.housheng/.MainActivity")
            Thread.sleep(300)
            assertNull("Leaving the feed must remove overlays", revealNode())
            instrumentation.sendStatus(0, Bundle().apply { putString("realScroll", "Real list advanced, Clef resumed, overlays cleared on app switch") })
        } finally {
            instrumentation.runOnMainSync { Session.stop(); ModelValidation.realEnabled = emptySet(); FeedMask.liveBlurAllowed = true }
            assertTrue(instrumentation.targetContext.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putString("rules", originalRules).commit())
        }
    }
}
