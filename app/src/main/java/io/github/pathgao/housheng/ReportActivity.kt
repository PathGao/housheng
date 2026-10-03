package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class ReportActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var periods: RadioGroup
    private lateinit var summary: TextView
    private lateinit var summaryContent: LinearLayout
    private lateinit var appsSection: TextView
    private lateinit var apps: LinearLayout
    private lateinit var includeApps: Switch
    private lateinit var preview: View
    private lateinit var copy: Button
    private lateinit var share: Button
    private var days = 7
    private var generation = 0
    private var report: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        days = savedInstanceState?.getInt("days", 7) ?: 7
        val page = ProductUi.page(this, "家庭报告")
        val body = page.body
        periods = ProductUi.segmented(this, listOf("7 天", "30 天"), if (days == 30) 1 else 0) { index ->
            days = if (index == 1) 30 else 7
            load()
        }
        body.addView(periods)
        summary = ProductUi.text(this, "", Type.SUPPORT)
        summaryContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(ProductUi.spacer(this))
        body.addView(ProductUi.panel(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            addView(summary); addView(summaryContent)
        })
        appsSection = ProductUi.section(this, "各应用")
        apps = ProductUi.group(this)
        body.addView(appsSection); body.addView(apps)
        body.addView(ProductUi.section(this, "分享内容"))
        includeApps = ProductUi.switchRow(this, "附上应用清单", "只含应用名和安装日期").apply {
            isChecked = savedInstanceState?.getBoolean("includeApps") ?: false
            setOnCheckedChangeListener { _, _ -> load() }
        }
        preview = ProductUi.navRow(this, "预览全文") { report?.let(::showPreview) }.apply {
            label.setTextColor(getColor(R.color.housheng_primary))
        }.view
        body.addView(ProductUi.group(this).apply { addView(includeApps); addView(preview) })
        body.addView(ProductUi.spacer(this, 32))
        body.addView(ProductUi.group(this).apply {
            addView(ProductUi.button(this@ReportActivity, "清空历史统计", ButtonKind.DANGER) {
                AlertDialog.Builder(this@ReportActivity).setTitle("清空本机历史统计？")
                    .setMessage("过去的统计无法恢复，已经分享出去的报告不会被删除。")
                    .setPositiveButton("清空记录") { _, _ -> clearHistory() }.setNegativeButton("保留", null).show()
            })
        })
        copy = ProductUi.button(this, "复制", ButtonKind.OUTLINED) {
            report?.let {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("后生家庭报告", it))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
        share = ProductUi.button(this, "分享给家人", ButtonKind.FILLED) {
            report?.let { text ->
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { startActivity(Intent.createChooser(intent, "分享后生家庭报告")) }
                    .onFailure {
                        android.util.Log.w("Housheng", "Report share unavailable: ${it.javaClass.simpleName}")
                        Toast.makeText(this, "没有可用的分享应用，可复制报告后发送", Toast.LENGTH_LONG).show()
                    }
            }
        }
        ProductUi.bottomBar(this, page, copy to 1f, share to 2f)
        load()
    }

    private fun setInputsEnabled(enabled: Boolean) {
        for (i in 0 until periods.childCount) periods.getChildAt(i).isEnabled = enabled
        includeApps.isEnabled = enabled
        preview.isEnabled = enabled
    }

    private fun setLoading() {
        setInputsEnabled(false)
        report = null; copy.isEnabled = false; share.isEnabled = false
        summary.text = "正在整理最近 $days 天的报告…"
        summaryContent.removeAllViews()
        appsSection.visibility = View.GONE; apps.visibility = View.GONE
    }

    private fun showFailure(message: String) {
        setInputsEnabled(true)
        summary.text = message
        summaryContent.removeAllViews()
        summaryContent.addView(ProductUi.button(this, "重试", ButtonKind.TONAL) { load() }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = ProductUi.dp(context, 16)
        })
    }

    private fun load() {
        if (!::preview.isInitialized) return
        val request = ++generation
        val window = days
        val withApps = includeApps.isChecked
        val preferences = Preferences(this)
        val selected = preferences.selected("notifications").filter { it != AppCatalog.FIXTURE }.toSet()
        val allowed = selected.filter { preferences.alwaysAllow(it) }.toSet()
        val rules = preferences.ruleText()
        val health = ServiceHealth(this)
        val state = "通知：${health.notificationStatus().title}；页面：${health.pageStatus().title}。${getString(R.string.report_feed_state)}"
        setLoading()
        worker.execute {
            val result = runCatching {
                val snapshot = ReportStore(this).use { it.snapshot(window) }
                snapshot to formatFamilyReport(snapshot, selected, rules, state, if (withApps) loadInstalledApps(this) else null, alwaysAllowedSources = allowed)
            }
            runOnUiThread {
                if (isDestroyed || request != generation) return@runOnUiThread
                result.onSuccess { (snapshot, text) ->
                    setInputsEnabled(true)
                    report = text; copy.isEnabled = true; share.isEnabled = true
                    showSummary(snapshot, allowed)
                }.onFailure { showFailure("报告暂时无法生成。") }
            }
        }
    }

    private fun showSummary(snapshot: ReportSnapshot, allowed: Set<String>) {
        val rows = snapshot.notifications.filter { it.source != AppCatalog.FIXTURE && AppCatalog.permits(it.source, "notifications") }
        val zone = ZoneId.systemDefault()
        val format = DateTimeFormatter.ofPattern("M月d日")
        fun date(at: Long) = Instant.ofEpochMilli(at).atZone(zone).format(format)
        summary.text = "${date(snapshot.startAt)} – ${date(snapshot.endAt)}"
        if (rows.isEmpty()) {
            summaryContent.addView(ProductUi.text(this, "这段时间还没有记录。", Type.BODY).apply {
                setPadding(0, ProductUi.dp(context, 12), 0, 0)
            })
            return
        }
        summaryContent.addView(ProductUi.metrics(this, listOf(
            "${rows.sumOf { it.received }}" to "收到",
            "${rows.sumOf { it.removalRequested }}" to "请求清理",
            "${rows.sumOf { it.`protected` }}" to "受保护"
        )).apply { setPadding(0, ProductUi.dp(context, 8), 0, 0) })
        apps.removeAllViews()
        val max = rows.maxOf { it.received }.coerceAtLeast(1)
        rows.sortedByDescending { it.received }.forEach { row -> apps.addView(appRow(row, row.source in allowed, max)) }
        appsSection.visibility = View.VISIBLE; apps.visibility = View.VISIBLE
    }

    private fun appRow(row: NoticeSummary, alwaysAllowed: Boolean, max: Int): View {
        val name = AppCatalog.sources.getValue(row.source)
        val outcome = if (alwaysAllowed) "始终放行" else "请求清理 ${row.removalRequested}"
        val dp = { value: Int -> ProductUi.dp(this, value) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(12), dp(16), dp(16))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = if (alwaysAllowed) "$name，收到 ${row.received} 次，始终放行"
            else "$name，收到 ${row.received} 次，请求清理 ${row.removalRequested} 次"
            addView(ProductUi.text(context, ProductUi.twoLine(context, name, "${row.received} 次 · $outcome"), Type.LABEL).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            addView(LinearLayout(context).apply {
                weightSum = max.toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                layoutParams = LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(12) }
                addView(View(context).apply {
                    background = ProductUi.rounded(context, R.color.housheng_primary, 4)
                    layoutParams = LinearLayout.LayoutParams(0, -1, row.received.toFloat())
                })
            })
        }
    }

    private fun showPreview(text: String) {
        val pad = ProductUi.dp(this, 24)
        AlertDialog.Builder(this)
            .setView(ScrollView(this).apply {
                addView(ProductUi.text(this@ReportActivity, text, Type.BODY).apply {
                    setTextIsSelectable(true)
                    setPadding(pad, pad, pad, 0)
                })
            })
            .setPositiveButton("关闭", null).show()
    }

    private fun clearHistory() {
        ++generation
        setLoading()
        Session.report(this, onFailure = {
            runOnUiThread { if (!isDestroyed) showFailure("清空失败，记录没有改动。") }
        }) { store ->
            store.clear()
            runOnUiThread {
                if (!isDestroyed) { Toast.makeText(this, "历史统计已清空", Toast.LENGTH_SHORT).show(); load() }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("days", days); outState.putBoolean("includeApps", includeApps.isChecked)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { generation++; report = null; worker.shutdownNow(); super.onDestroy() }
}
