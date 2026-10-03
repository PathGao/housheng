package io.github.pathgao.housheng

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.Executors

class FeedService : AccessibilityService() {
    companion object { var instance: FeedService? = null; private set }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val gate = ActionGate()
    private var generation = 0L
    private var busy = false
    private var pending: Pair<Page, PageToken>? = null
    private var alive = false
    private var lastSignature = ""
    private var captureRequest = 0L
    internal var classifierFactory: () -> Classifier = { Preferences(this).classifier() }

    override fun onCreate() { super.onCreate(); Session.initialize(this) }
    override fun onServiceConnected() {
        instance = this; alive = true; Session.stateChanged()
        Session.record("页面服务已连接")
        Session.serviceEvent(this, "pages", "connected")
    }
    override fun onInterrupt() {
        invalidate()
        cancelCapture()
        Session.record("当前页面动作已中止")
        Session.serviceEvent(this, "pages", "interrupted")
    }
    override fun onDestroy() {
        alive = false
        invalidate()
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        if (instance === this) { instance = null; Session.feedExecution = false }
        Session.serviceEvent(this, "pages", "destroyed")
        super.onDestroy()
    }

    fun invalidate() { generation++; gate.current = null; lastSignature = ""; pending = null }
    fun cancelCapture() { captureRequest++ }

    private data class Page(val source: String, val text: String, val item: String?)
    private fun readPage(root: AccessibilityNodeInfo): Page {
        val text = StringBuilder()
        var item: String? = null
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (++count > 160 || depth > 20 || text.length >= 4000 || !node.isVisibleToUser) return
            val description = node.contentDescription?.toString().orEmpty()
            if (description.startsWith("housheng-content:")) item = description
            node.text?.let { if (it.isNotBlank()) text.append(it.take(500)).append('\n') }
            if (description.isNotBlank() && !description.startsWith("housheng-content:")) text.append(description.take(500)).append('\n')
            for (i in 0 until node.childCount.coerceAtMost(160)) node.getChild(i)?.let { child ->
                try { visit(child, depth + 1) } finally { child.recycle() }
            }
        }
        visit(root, 0)
        val source = root.packageName?.toString().orEmpty()
        return Page(source, text.take(4000).toString(), item.takeIf { source == Session.FIXTURE })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val start = Session.now()
        val root = rootInActiveWindow ?: run { invalidate(); return }
        val page = try {
            if (root.packageName?.toString() !in Preferences(this).selected("pages")) {
                invalidate()
                return
            }
            readPage(root)
        } finally { root.recycle() }
        val signature = "${page.source}\u0000${page.item}\u0000${page.text}"
        if (signature == lastSignature) return
        lastSignature = signature
        val token = PageToken(page.source, signature, ++generation, start, if (page.item != null) ContentKind.USER_CONTENT else ContentKind.UNKNOWN)
        gate.current = token
        pending = null
        Session.statistics.record(token)
        Session.latestPage = "${Session.sources[page.source] ?: page.source}\n${page.text}"
        Session.record("页面 · ${Session.sources[page.source]} · ${page.text.length}字 · ${if (page.item == null) "仅观察" else "可验证翻页"}")
        if (page.item != null) classifyPage(page, token)
    }

    private fun classifyPage(page: Page, token: PageToken) {
        if (busy) { pending = page to token; return }
        busy = true
        val classifier = ModelValidation.classifier(page.source, classifierFactory())
        worker.execute {
            val decision = runCatching { classifier.classify(page.text) }.getOrDefault(Decision.KEEP)
            main.post {
                busy = false
                try { applyDecision(page, token, decision) } finally {
                    val newest = pending
                    pending = null
                    if (alive && newest != null && gate.current == newest.second) classifyPage(newest.first, newest.second)
                }
            }
        }
    }

    private fun applyDecision(page: Page, token: PageToken, decision: Decision) {
        if (!alive) return
        gate.enabled = Session.feedExecution && page.source in Preferences(this).selected("pages")
        val currentRoot = rootInActiveWindow ?: return
        try {
            if (currentRoot.packageName?.toString() !in Preferences(this).selected("pages")) {
                invalidate(); Session.record("判定丢弃 · 当前来源未勾选"); return
            }
            val current = readPage(currentRoot)
            val unchanged = current.source == page.source && current.item == page.item && current.text == page.text
            if (!unchanged) { Session.record("判定丢弃 · 页面已变化"); return }
            val elapsed = Session.now() - token.observedAt
            if (gate.claim(token, decision, Session.now())) {
                val node = findItem(currentRoot, page.item!!)
                val acted = try { node?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true } finally { node?.recycle() }
                Session.record("翻页 · ${if (acted) "已执行" else "不支持，保留"} · ${elapsed}ms")
            } else {
                Session.record("判定 · ${if (decision == Decision.SKIP) "命中" else "保留"} · 未执行 · ${elapsed}ms")
            }
        } finally { currentRoot.recycle() }
    }

    private fun findItem(node: AccessibilityNodeInfo, item: String, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 20) return null
        if (node.contentDescription?.toString() == item) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
            val result = try { findItem(child, item, depth + 1) } finally { child.recycle() }
            if (result != null) return result
        }
        return null
    }

    fun captureAfterDelay() {
        val request = ++captureRequest
        Session.record("截图已预约：5秒内打开已勾选的应用")
        main.postDelayed({
            if (!alive || request != captureRequest) return@postDelayed
            captureSelectedPage(request)
        }, 5000)
    }

    private fun captureSelectedPage(request: Long) {
        val root = rootInActiveWindow
        val source = root?.packageName?.toString()
        root?.recycle()
        if (source !in Preferences(this).selected("pages")) { Session.record("截图取消：当前应用未勾选"); return }
        val start = Session.now()
        val capturedGeneration = generation
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hardware = result.hardwareBuffer
                try {
                    val nowRoot = rootInActiveWindow
                    val sameSource = nowRoot?.packageName?.toString() == source
                    nowRoot?.recycle()
                    if (!alive || request != captureRequest || !sameSource || generation != capturedGeneration || source !in Preferences(this@FeedService).selected("pages")) {
                        Session.record("截图取消：页面已变化"); return
                    }
                    val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace) ?: return
                    val bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    wrapped.recycle()
                    val saved = try {
                        saveSnapshot(File(cacheDir, "capture.png")) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                    Session.record(if (saved) "截图已保存到私有缓存 · ${Session.now() - start}ms" else "截图保存失败，请检查可用存储空间；上次截图未替换")
                } catch (_: RuntimeException) {
                    Session.record("截图处理失败，请重试")
                } finally { hardware.close() }
            }
            override fun onFailure(errorCode: Int) { Session.record("截图失败 · 系统代码 $errorCode") }
        })
    }
}
