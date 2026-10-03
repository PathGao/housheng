package io.github.pathgao.housheng

import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.concurrent.Executors

internal data class FeedCard(val title: String, val bounds: Rect)

internal object XiaohongshuFeed {
    const val PACKAGE = "com.xingin.xhs"
    fun read(root: AccessibilityNodeInfo): List<FeedCard> {
        if (root.packageName?.toString() != PACKAGE) return emptyList()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        var complete = true
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= 300 || depth > 30) { complete = false; return }
            if (!node.isVisibleToUser) return
            nodes.add(AccessibilityNodeInfo.obtain(node))
            for (i in 0 until node.childCount.coerceAtMost(100)) node.getChild(i)?.let {
                try { walk(it, depth + 1) } finally { it.recycle() }
            }
        }
        try {
            walk(root, 0)
            if (!complete || nodes.any { it.className?.toString() in setOf("android.webkit.WebView", "android.widget.EditText") }) return emptyList()
            if (!listOf("首页", "发现").all { label -> nodes.any { it.isSelected && it.contentDescription?.toString() == label } }) return emptyList()
            val lists = nodes.filter { it.className?.toString() == "androidx.recyclerview.widget.RecyclerView" && it.isScrollable }
            if (lists.size != 1) return emptyList()
            val area = Rect().also(lists.single()::getBoundsInScreen)
            return nodes.mapNotNull { node ->
                val title = publicFeedTitle(node.contentDescription?.toString().orEmpty()) ?: return@mapNotNull null
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!area.contains(bounds) || bounds.width() !in area.width() / 3..area.width() * 2 / 3 || bounds.height() < bounds.width() / 2) return@mapNotNull null
                val protected = nodes.any { child ->
                    val rect = Rect().also(child::getBoundsInScreen)
                    bounds.contains(rect) && listOf(child.text, child.contentDescription).any { value ->
                        value != null && listOf("广告", "赞助", "推广", "品牌合作").any { value.contains(it) }
                    }
                }
                if (protected) null else FeedCard(title, bounds)
            }.distinct().take(4)
        } finally { nodes.forEach { it.recycle() } }
    }
}

internal class RealFeedController(private val service: FeedService) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private val manager = service.getSystemService(WindowManager::class.java)
    private val masks = mutableMapOf<FeedCard, List<android.view.View>>()
    private val answers = linkedMapOf<String, String>()
    private val revealed = mutableSetOf<String>()
    private var current = emptyList<FeedCard>()
    private var pending = emptyList<FeedCard>()
    private var busy = 0
    private var generation = 0L
    private var closed = false
    private var observedAt = 0L
    private var refresh: Runnable? = null
    private val supportedVersion by lazy {
        runCatching { service.packageManager.getPackageInfo(XiaohongshuFeed.PACKAGE, 0).versionName == "9.49.0" }.getOrDefault(false)
    }

    fun observe(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        if (root.packageName?.toString() != XiaohongshuFeed.PACKAGE || !ModelValidation.realEnabled || !supportedVersion) { clear(); return false }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            clear()
            refresh = Runnable {
                refresh = null
                val latest = service.rootInActiveWindow ?: return@Runnable
                val settled = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                try { observe(latest, settled) } finally { latest.recycle(); settled.recycle() }
            }.also { main.postDelayed(it, 120) }
            return true
        }
        val cards = XiaohongshuFeed.read(root)
        if (cards == current) return true
        removeMasks()
        current = cards
        pending = cards
        observedAt = Session.now()
        generation++
        if (cards.isEmpty()) return true
        Session.record("小红书发现页 · ${cards.size} 张可读取卡片")
        val rules = Preferences(service).classifier()
        val localMatches = cards.filter { rules.classify(it.title) == Decision.SKIP }
        if (localMatches.isNotEmpty()) Session.record("小红书 · 本地规则命中 ${localMatches.size} 张")
        pending = pending.filterNot { it in localMatches }
        cards.filter { it in localMatches || answers[it.title] == "filter" }.forEach(::mask)
        next()
        return true
    }

    private fun next() {
        if (busy >= 2 || closed) return
        val card = pending.firstOrNull { it.title !in answers } ?: return
        pending = pending.filterNot { it == card }
        val token = generation
        val started = Session.now()
        busy++
        worker.execute {
            val answer = runCatching { ClefApiClient(service).classify(card.title) }.getOrDefault("uncertain")
            main.post {
                busy--
                if (closed) return@post
                if (generation == token && ModelValidation.realEnabled) {
                    if (answers.size >= 32) answers.remove(answers.keys.first())
                    answers[card.title] = answer
                    Session.record("小红书 Clef · $answer · ${Session.now() - started}ms")
                    if (answer == "filter" && Session.now() - observedAt < 3000) mask(card)
                } else Session.record("小红书 · 页面变化，丢弃旧判断")
                next()
            }
        }
        next()
    }

    private fun mask(card: FeedCard) {
        if (!Session.feedExecution || card.title in revealed || card in masks || !ModelValidation.realEnabled) return
        val root = service.rootInActiveWindow ?: return
        val valid = try { card in XiaohongshuFeed.read(root) } finally { root.recycle() }
        if (!valid) return
        val cover = TextView(service).apply {
            text = "后生已遮挡\n命中你的筛选规则"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(service.getColor(R.color.housheng_text))
            setBackgroundColor(service.getColor(R.color.housheng_primary_container))
        }
        val show = ProductUi.button(service, "显示这条") {
            if (revealed.size >= 32) revealed.remove(revealed.first())
            revealed.add(card.title)
            masks.remove(card)?.forEach { manager.removeView(it) }
            Session.record("小红书 · 用户恢复显示")
        }
        val height = ProductUi.dp(service, 60)
        fun params(rect: Rect, touchable: Boolean) = WindowManager.LayoutParams(
            rect.width(), rect.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = rect.left; y = rect.top }
        val added = mutableListOf<android.view.View>()
        try {
            manager.addView(cover, params(card.bounds, false)); added.add(cover)
            val button = Rect(card.bounds.left + 16, card.bounds.bottom - height - 16, card.bounds.right - 16, card.bounds.bottom - 16)
            manager.addView(show, params(button, true)); added.add(show)
            masks[card] = added
            Session.record("小红书 · 卡片已遮挡")
        } catch (_: RuntimeException) {
            added.forEach { manager.removeView(it) }
            Session.record("小红书 · 遮挡失败，保留原内容")
        }
    }

    private fun removeMasks() { masks.values.flatten().forEach { manager.removeView(it) }; masks.clear() }
    fun clear() { refresh?.let(main::removeCallbacks); refresh = null; generation++; current = emptyList(); pending = emptyList(); removeMasks() }
    fun close() { closed = true; clear(); answers.clear(); revealed.clear(); worker.shutdownNow() }
}
