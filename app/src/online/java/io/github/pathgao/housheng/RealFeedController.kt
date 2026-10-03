package io.github.pathgao.housheng

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

/** Visible nodes in pre-order, root first; null when the tree is too large to read completely. */
private fun visibleNodes(root: AccessibilityNodeInfo): List<FeedNode>? {
    val nodes = mutableListOf<FeedNode>()
    var complete = true
    fun walk(node: AccessibilityNodeInfo, depth: Int) {
        if (nodes.size >= 300 || depth > 30) { complete = false; return }
        if (!node.isVisibleToUser) return
        val r = Rect().also(node::getBoundsInScreen)
        nodes.add(FeedNode(node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty(), node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(), Box(r.left, r.top, r.right, r.bottom), node.isSelected, node.isScrollable))
        for (i in 0 until node.childCount.coerceAtMost(100)) node.getChild(i)?.let {
            try { walk(it, depth + 1) } finally { it.recycle() }
        }
    }
    walk(root, 0)
    return nodes.takeIf { complete }
}

internal fun readFeed(root: AccessibilityNodeInfo): List<FeedCard> =
    RealFeeds.read(root.packageName?.toString().orEmpty(), visibleNodes(root) ?: emptyList())

internal class RealFeedController(private val service: FeedService) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private val masks = FeedMask(service)
    private val answers = linkedMapOf<String, String>()
    private val revealed = mutableSetOf<String>()
    private var current = emptyList<FeedCard>()
    private var pending = emptyList<FeedCard>()
    private var busy = 0
    private var generation = 0L
    private var closed = false
    private var observedAt = 0L
    private var refresh: Runnable? = null
    private var source = ""
    private val label get() = AppCatalog.sources[source] ?: source
    private val versions = mutableMapOf<String, Boolean>()
    private fun supported(pkg: String) = versions.getOrPut(pkg) {
        runCatching { RealFeeds.supports(pkg, service.packageManager.getPackageInfo(pkg, 0).versionName) }.getOrDefault(false)
    }

    fun observe(root: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg !in ModelValidation.realEnabled || !supported(pkg)) { clear(); return false }
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
        val cards = readFeed(root)
        if (cards == current && pkg == source) return true
        masks.clear()
        source = pkg
        current = cards
        pending = cards
        observedAt = Session.now()
        generation++
        if (cards.isEmpty()) return true
        Session.record("$label · ${cards.size} 条可读取内容")
        val rules = Preferences(service).classifier()
        val localMatches = cards.filter { rules.classify(it.title) == Decision.SKIP }
        if (localMatches.isNotEmpty()) Session.record("$label · 本地规则命中 ${localMatches.size} 条")
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
                if (generation == token && source in ModelValidation.realEnabled) {
                    if (answers.size >= 32) answers.remove(answers.keys.first())
                    answers[card.title] = answer
                    Session.record("$label Clef · $answer · ${Session.now() - started}ms")
                    if (answer == "filter" && Session.now() - observedAt < 3000) mask(card)
                } else Session.record("$label · 页面变化，丢弃旧判断")
                next()
            }
        }
        next()
    }

    private fun mask(card: FeedCard) {
        if (!Session.feedExecution || card.title in revealed || card in masks || source !in ModelValidation.realEnabled) return
        val root = service.rootInActiveWindow ?: return
        val valid = try { card in readFeed(root) } finally { root.recycle() }
        if (!valid) return
        val label = label
        masks.show(card, card.box.let { Rect(it.left, it.top, it.right, it.bottom) }, label) {
            if (revealed.size >= 32) revealed.remove(revealed.first())
            revealed.add(card.title)
            Session.record("$label · 用户恢复显示")
        }
    }

    fun clear() { refresh?.let(main::removeCallbacks); refresh = null; generation++; current = emptyList(); pending = emptyList(); masks.clear() }
    fun close() { closed = true; clear(); answers.clear(); revealed.clear(); worker.shutdownNow(); masks.close() }
}
