package io.github.pathgao.housheng

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

internal object DeviceUi {
    val automation get() = InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).apply {
        serviceInfo = serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
    }
    fun shell(command: String): String {
        val output = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString() }
        check(!output.contains("Exception") && !output.contains("Error:")) { output }
        return output
    }
    fun click(text: String): Boolean {
        val root = automation.rootInActiveWindow ?: return false
        try {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            try {
                for (node in nodes) {
                    if (node.text?.toString() != text) continue
                    var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                    for (depth in 0..5) {
                        val target = current ?: break
                        if (target.isClickable && target.isEnabled) {
                            val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            target.recycle()
                            if (result) return true
                            break
                        }
                        current = target.parent
                        target.recycle()
                    }
                }
            } finally { nodes.forEach { it.recycle() } }
        } finally { root.recycle() }
        return false
    }
    fun waitClick(text: String): Boolean {
        repeat(60) { if (click(text)) return true; Thread.sleep(250) }
        return false
    }
    fun settingsText(): String {
        val root = automation.rootInActiveWindow ?: return "No active window"
        try {
            if (root.packageName?.toString() !in setOf("com.android.settings", "com.miui.securitycenter", "com.lbe.security.miui", "com.android.permissioncontroller")) return "Unexpected window: ${root.packageName}"
            val texts = mutableListOf<String>()
            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 15 || texts.size > 60) return
                if (!node.text.isNullOrBlank()) texts.add("${node.text} [enabled=${node.isEnabled}, checked=${node.isChecked}, id=${node.viewIdResourceName}]")
                for (i in 0 until node.childCount) node.getChild(i)?.let { child -> try { walk(child, depth + 1) } finally { child.recycle() } }
            }
            walk(root, 0)
            return texts.joinToString("\n")
        } finally { root.recycle() }
    }

}

internal class DevicePreparation {
    private fun bound(): Boolean = DeviceUi.shell("dumpsys accessibility").lineSequence()
        .any { it.contains("Bound services:") && it.contains("后生页面观察") }

    private fun waitBound(): Boolean {
        repeat(40) { if (bound()) return true; Thread.sleep(250) }
        return false
    }

    /** Re-enables the service without reading any Settings text; false where shell may not write secure settings. */
    private fun reconnectThroughSecureSettings(): Boolean {
        val key = "enabled_accessibility_services"
        val own = android.content.ComponentName("io.github.pathgao.housheng", "io.github.pathgao.housheng.FeedService")
        val others = DeviceUi.shell("settings get secure $key").trim().split(':')
            .filter { it.isNotBlank() && it != "null" && android.content.ComponentName.unflattenFromString(it) != own }
        return try {
            DeviceUi.shell(if (others.isEmpty()) "settings delete secure $key" else "settings put secure $key ${others.joinToString(":")}")
            DeviceUi.shell("settings put secure $key ${(others + own.flattenToString()).joinToString(":")}")
            DeviceUi.shell("settings put secure accessibility_enabled 1")
            waitBound()
        } catch (_: IllegalStateException) { false }
    }

    fun reconnectServicesForInstrumentation() {
        if (reconnectThroughSecureSettings()) {
            DeviceUi.shell("am start -W -n io.github.pathgao.housheng.fixture/.MainActivity")
            return
        }
        // MIUI denies shell secure-settings writes unless "USB 调试（安全设置）" is on, so toggle the service in
        // Settings instead. The texts below, including the risk dialog, are zh-CN MIUI strings seen on Xiaomi 10S, Android 13.
        if (DeviceUi.settingsText().contains("要停用“后生页面观察”吗")) DeviceUi.click("取消")
        DeviceUi.shell("am start -W -f 0x10008000 -a android.settings.ACCESSIBILITY_SETTINGS")
        if (!DeviceUi.waitClick("已下载的应用")) throw AssertionError(DeviceUi.settingsText())
        if (!DeviceUi.waitClick("后生页面观察")) throw AssertionError(DeviceUi.settingsText())
        assertTrue(DeviceUi.waitClick("使用“后生页面观察”"))
        var handledStop = false
        var handledWarning = false
        val deadline = android.os.SystemClock.elapsedRealtime() + 30000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val text = DeviceUi.settingsText()
            if (!handledStop && text.contains("要停用“后生页面观察”吗")) {
                assertTrue(DeviceUi.waitClick("停止"))
                Thread.sleep(500)
                assertTrue(DeviceUi.waitClick("使用“后生页面观察”"))
                handledStop = true
            } else if (!handledWarning && text.contains("高度敏感权限")) {
                assertTrue(DeviceUi.waitClick("我已知晓可能存在的风险，并自愿承担可能导致的后果"))
                assertTrue(DeviceUi.waitClick("确定"))
                handledWarning = true
            } else if (bound()) {
                DeviceUi.shell("am start -W -n io.github.pathgao.housheng.fixture/.MainActivity")
                return
            }
            Thread.sleep(250)
        }
        throw AssertionError("Own accessibility service did not bind: ${DeviceUi.settingsText()}\n" +
            DeviceUi.shell("dumpsys accessibility").lineSequence().filter { it.contains(" services:") }.joinToString("\n"))
    }
}
