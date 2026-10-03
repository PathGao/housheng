package io.github.pathgao.housheng

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs only with -e maskCheck true; -e maskPath frame forces the screenshot blur fallback. Screenshots stay in the app cache. */
class FeedMaskDeviceTest {
    @Test fun coverHidesTheFixturePageAndRevealRemovesIt() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("maskCheck") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefix = if (args.getString("maskPath") == "frame") "frame-" else ""
        fun snap(name: String) = DeviceUi.automation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.cacheDir, "$prefix$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun revealNode(): AccessibilityNodeInfo? {
            for (window in DeviceUi.automation.windows) {
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() != context.packageName) continue
                    val nodes = root.findAccessibilityNodeInfosByText("显示这条")
                    val copy = nodes.firstOrNull()?.let { AccessibilityNodeInfo.obtain(it) }
                    nodes.forEach { it.recycle() }
                    if (copy != null) return copy
                } finally { root.recycle() }
            }
            return null
        }
        DeviceUi.automation.apply {
            serviceInfo = serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }
        DevicePreparation().reconnectServicesForInstrumentation()
        Thread.sleep(1500)
        val manager = context.getSystemService(WindowManager::class.java)
        val screen = manager.maximumWindowMetrics.bounds
        val rect = Rect(screen.width() / 12, screen.height() / 8, screen.width() * 11 / 12, screen.height() / 2)
        val blurSupported = Build.VERSION.SDK_INT >= 31 && manager.isCrossWindowBlurEnabled
        var mask: FeedMask? = null
        var revealed = false
        try {
            snap("before")
            instrumentation.runOnMainSync {
                Session.clear()
                FeedMask.liveBlurAllowed = prefix.isEmpty()
                mask = FeedMask(FeedService.instance!!).also { it.show("card", rect, "检查") { revealed = true } }
            }
            val end = Session.now() + 3000
            while (Session.now() < end && !Session.journal().contains("检查 · 已遮挡")) Thread.sleep(100)
            Thread.sleep(500)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("mask", "API ${Build.VERSION.SDK_INT} crossWindowBlur=$blurSupported\n${Session.journal()}")
            })
            assertTrue(Session.journal(), Session.journal().contains("检查 · 已遮挡"))
            snap("blurred")
            val reveal = revealNode()
            assertNotNull("reveal button must be visible", reveal)
            try { assertTrue(reveal!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) } finally { reveal?.recycle() }
            Thread.sleep(500)
            assertTrue("reveal callback must run", revealed)
            assertNull("reveal must remove the cover", revealNode())
            snap("restored")
        } finally {
            instrumentation.runOnMainSync { mask?.close(); FeedMask.liveBlurAllowed = true }
        }
    }
}
