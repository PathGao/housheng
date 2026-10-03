package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import java.util.concurrent.Executors

class NotificationsActivity : Activity() {
    private val stateObserver: () -> Unit = { refreshStatus() }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var preferences: Preferences
    private lateinit var status: StatusPanel
    private lateinit var execution: Switch
    private lateinit var rules: EditText
    private val rows = linkedMapOf<String, ProductUi.NavRow>()
    private var stats: List<NoticeSummary>? = null
    private var statsFailed = false
    private var refreshing = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        preferences = Preferences(this)
        val body = ProductUi.page(this, "通知清理").body
        status = StatusPanel(this) { reconnect() }
        status.view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ProductUi.dp(this@NotificationsActivity, 16) }
        body.addView(status.view)

        execution = ProductUi.switchRow(this, "按规则清理通知", "关闭时只记录次数").apply {
            setOnCheckedChangeListener { _, checked ->
                if (!refreshing) {
                    if (checked && !ServiceHealth(this@NotificationsActivity).notificationStatus().authorized) {
                        refreshing = true; isChecked = false; refreshing = false
                        reconnect()
                    } else {
                        Session.notificationExecution = checked
                        refreshStatus()
                    }
                }
            }
        }
        body.addView(ProductUi.group(this).apply { addView(execution) })
        body.addView(ProductUi.helper(this, "电话、短信、闹钟和常驻通知始终保留。"))

        body.addView(ProductUi.section(this, "管理的应用"))
        body.addView(ProductUi.group(this, inset = true).apply {
            for ((source, name) in AppCatalog.sources.filterKeys { it != AppCatalog.FIXTURE }) {
                val row = ProductUi.navRow(context, name, leading = ProductUi.appIcon(context, source)) { showApp(source, name) }
                rows[source] = row
                addView(row.view)
            }
        })
        body.addView(ProductUi.helper(this, "点应用可设置始终放行或系统通知。"))
        rows.keys.forEach(::updateRow)

        body.addView(ProductUi.section(this, "清理关键词"))
        val pad = ProductUi.dp(this, 16)
        rules = EditText(this).apply {
            textSize = Type.BODY.sp
            background = ProductUi.rounded(context, R.color.housheng_surface, 12, R.color.housheng_outline)
            setPadding(pad, pad, pad, pad)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            hint = "每行一个关键词"
            contentDescription = "通知清理关键词"
            setText(savedInstanceState?.getString("draftRules") ?: preferences.ruleText())
        }
        body.addView(ProductUi.group(this).apply {
            showDividers = LinearLayout.SHOW_DIVIDER_NONE
            addView(rules, LinearLayout.LayoutParams(-1, -2).apply { setMargins(pad, pad, pad, pad) })
            addView(ProductUi.button(context, "保存关键词", ButtonKind.TONAL) { saveRules() }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(pad, 0, pad, pad) }
            })
        })
        body.addView(ProductUi.helper(this, "每行一个词。只对没有始终放行的应用生效。"))
        body.addView(ProductUi.helper(this, "清理在通知到达后进行，已经响起的提示音无法撤回。"))
    }

    override fun onStart() { super.onStart(); Session.observeState(stateObserver) }
    override fun onStop() { Session.removeStateObserver(stateObserver); super.onStop() }

    override fun onResume() { super.onResume(); refreshStatus(); loadSummary() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("draftRules", rules.text.toString()); super.onSaveInstanceState(outState) }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val state = ServiceHealth(this).notificationStatus()
        status.show(state)
        status.view.visibility = if (state.connected) View.GONE else View.VISIBLE
        refreshing = true; execution.isChecked = Session.notificationExecution; refreshing = false
    }

    private fun reconnect() = connectNotifications {
        refreshStatus()
        Toast.makeText(this, "已请求重新连接", Toast.LENGTH_SHORT).show()
    }

    private fun updateRow(source: String) {
        val row = rows[source] ?: return
        val managed = source in preferences.selected("notifications")
        val support = when {
            !managed -> "未管理"
            statsFailed -> "统计暂不可用"
            else -> stats?.let { all ->
                val stat = all.firstOrNull { it.source == source }
                when {
                    stat == null -> "7 天暂无记录"
                    preferences.alwaysAllow(source) -> "7 天 ${stat.received} 次 · 始终放行"
                    else -> "7 天 ${stat.received} 次 · 请求清理 ${stat.removalRequested} 次"
                }
            }
        }
        row.label.text = ProductUi.twoLine(this, AppCatalog.sources.getValue(source), support)
    }

    private fun showApp(source: String, name: String) {
        val managed = ProductUi.switchRow(this, "管理这个应用", "记录次数，按规则清理").apply {
            isChecked = source in preferences.selected("notifications")
        }
        val allow = ProductUi.switchRow(this, "始终放行", "这个应用的通知不清理").apply {
            isChecked = preferences.alwaysAllow(source)
            isEnabled = managed.isChecked
            setOnCheckedChangeListener { _, checked -> preferences.setAlwaysAllow(source, checked) }
        }
        managed.setOnCheckedChangeListener { _, checked ->
            preferences.select("notifications", source, checked)
            allow.isEnabled = checked
        }
        val content = LinearLayout(this).apply {
            val pad = ProductUi.dp(context, 16)
            setPadding(pad, ProductUi.dp(context, 8), pad, 0)
            addView(ProductUi.group(context).apply {
                addView(managed); addView(allow)
                addView(ProductUi.navRow(context, "系统通知设置") {
                    if (!openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, source)) Toast.makeText(this@NotificationsActivity, "无法打开此入口，请在手机设置中查看通知权限", Toast.LENGTH_LONG).show()
                }.view)
            })
        }
        AlertDialog.Builder(this).setTitle(name).setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("完成", null)
            .setOnDismissListener { if (!isDestroyed) updateRow(source) }
            .show()
    }

    private fun saveRules() {
        val terms = rules.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val value = terms.joinToString("\n")
        if (terms.size > 32 || value.length > 2000) {
            rules.error = "请减少到 32 行、2000 字以内"
        } else {
            preferences.saveRules(value)
            rules.error = null
            Toast.makeText(this, if (value.isBlank()) "已清空规则，不会按关键词清理" else "规则已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSummary() {
        if (loading) return
        loading = true
        worker.execute {
            val result = runCatching { ReportStore(this).use { it.snapshot(7) } }
            runOnUiThread {
                loading = false
                if (isDestroyed) return@runOnUiThread
                stats = result.getOrNull()?.notifications
                statsFailed = result.isFailure
                rows.keys.forEach(::updateRow)
            }
        }
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
