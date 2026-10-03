package io.github.pathgao.housheng

import android.content.Context
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

object Session {
    const val FIXTURE = AppCatalog.FIXTURE
    val sources get() = AppCatalog.sources
    private val stateListeners = CopyOnWriteArraySet<() -> Unit>()
    fun observeState(listener: () -> Unit) { stateListeners.add(listener) }
    fun removeStateObserver(listener: () -> Unit) { stateListeners.remove(listener) }
    internal fun stateChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) stateListeners.forEach { it() }
        else Handler(Looper.getMainLooper()).post { stateListeners.forEach { it() } }
    }
    private var notificationChoice = ExecutionChoice(false) {}
    private var initialized = false
    var notificationExecution: Boolean
        get() = notificationChoice.requested
        set(value) { notificationChoice.requested = value; stateChanged() }
    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        notificationChoice = ExecutionChoice(prefs.getBoolean("notificationExecution", false)) {
            prefs.edit().putBoolean("notificationExecution", it).apply()
        }
        initialized = true
    }
    var feedExecution = false
        set(value) { field = value; stateChanged() }
    var latestPage = "尚未读取页面"
    val statistics = FeedStatistics()
    private val entries = ArrayDeque<String>()
    fun record(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        synchronized(entries) {
            entries.addFirst("$time  $message")
            while (entries.size > 50) entries.removeLast()
        }
    }
    fun journal(): String = synchronized(entries) { entries.joinToString("\n") }
    fun clear() { synchronized(entries) { entries.clear() }; latestPage = "已清除"; statistics.clear() }
    fun stop() {
        notificationExecution = false
        feedExecution = false
        FeedService.instance?.invalidate()
        FeedService.instance?.cancelCapture()
        record("已停止所有自动执行")
    }
    fun now() = SystemClock.elapsedRealtime()

    private val reportWriter = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(128)).apply {
        allowCoreThreadTimeOut(true)
    }
    private var lastReportFailure: Long? = null
    private fun reportFailure() = synchronized(reportWriter) {
        val time = now()
        if (lastReportFailure == null || time - lastReportFailure!! >= 60_000) {
            record("部分报告记录未保存，请检查手机存储空间或稍后重试")
            lastReportFailure = time
        }
    }
    internal fun report(context: Context, onFailure: () -> Unit = {}, write: (ReportStore) -> Unit) {
        val application = context.applicationContext
        try {
            reportWriter.execute {
                try { ReportStore(application).use(write) } catch (_: Exception) { reportFailure(); onFailure() }
            }
        } catch (_: RejectedExecutionException) { reportFailure(); onFailure() }
    }
    internal fun serviceEvent(context: Context, service: String, event: String) {
        val at = System.currentTimeMillis()
        report(context) { it.recordService(service, event, at) }
    }
}

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun selected(kind: String): Set<String> = prefs.getStringSet(kind, emptySet())!!.filter { AppCatalog.permits(it, kind) }.toSet()
    fun select(kind: String, source: String, enabled: Boolean) {
        if (enabled && !AppCatalog.permits(source, kind)) return
        val chosen = selected(kind).toMutableSet()
        if (enabled) chosen.add(source) else chosen.remove(source)
        prefs.edit().putStringSet(kind, chosen).apply()
    }
    fun ruleText(): String = prefs.getString("rules", "震惊内幕")!!
    fun saveRules(text: String) { prefs.edit().putString("rules", text.take(2000)).apply() }
    fun alwaysAllow(source: String): Boolean = AppCatalog.permits(source, "notifications") &&
        source in prefs.getStringSet("alwaysAllow", emptySet()).orEmpty()
    fun setAlwaysAllow(source: String, enabled: Boolean) {
        if (!AppCatalog.permits(source, "notifications")) return
        val allowed = prefs.getStringSet("alwaysAllow", emptySet()).orEmpty().toMutableSet()
        if (enabled) allowed.add(source) else allowed.remove(source)
        prefs.edit().putStringSet("alwaysAllow", allowed).apply()
    }
    fun classifier(): Classifier = KeywordClassifier(ruleText().lines().take(32))
}
