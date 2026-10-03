package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class NotificationsActivity : Activity() {
    private val stateObserver: () -> Unit = { refreshStatus() }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var preferences: Preferences
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var execution: Switch
    private lateinit var rules: EditText
    private val sourceCounts = mutableMapOf<String, TextView>()
    private var refreshing = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        preferences = Preferences(this)
        val body = LegacyUi.page(this, "通知管理", "让有用的消息留下，减少不需要的打扰。")
        val stateCard = LegacyUi.card(this, "当前状态")
        status = LegacyUi.text(this, "正在检查…")
        stateCard.addView(status)
        stateCard.addView(LegacyUi.button(this, "检查通知连接") { checkConnection() })
        execution = Switch(this).apply {
            text = "按规则清理通知"
            textSize = 19f
            minHeight = LegacyUi.dp(context, 64)
            setOnCheckedChangeListener { _, checked ->
                if (!refreshing) {
                    if (checked && !ServiceHealth(this@NotificationsActivity).notificationStatus().authorized) {
                        refreshing = true; isChecked = false; refreshing = false
                        checkConnection()
                    } else {
                        Session.notificationExecution = checked
                        refreshStatus()
                    }
                }
            }
        }
        stateCard.addView(execution)
        stateCard.addView(LegacyUi.text(this, "关闭时只记录所选来源的次数。开启后仅清理命中规则、允许移除的普通通知。电话、消息、闹钟及常驻通知始终保留。", 16f))
        body.addView(stateCard)
        summary = LegacyUi.text(this, "正在读取本机统计…")
        body.addView(LegacyUi.card(this, "最近 7 天").apply { addView(summary) })
        body.addView(LegacyUi.text(this, "选择要管理的应用", 23f, true))
        body.addView(LegacyUi.text(this, "只处理你勾选的来源。取消后停止新记录，过去的统计仍保留。浏览器和聊天应用不在名单中。", 16f))
        for ((source, name) in AppCatalog.sources.filterKeys { it != AppCatalog.FIXTURE }) {
            val card = LegacyUi.card(this, name)
            card.addView(LegacyUi.text(this, "正在读取统计…", 16f).also { sourceCounts[source] = it })
            val selected = CheckBox(this).apply {
                text = "记录并管理通知"; textSize = 18f
                minHeight = LegacyUi.dp(context, 56)
                isChecked = source in preferences.selected("notifications")
                contentDescription = "$name 通知来源"
            }
            val allow = CheckBox(this).apply {
                text = "这个应用始终放行"; textSize = 18f
                minHeight = LegacyUi.dp(context, 56)
                isChecked = preferences.alwaysAllow(source)
                isEnabled = selected.isChecked
                contentDescription = "$name 始终放行"
                setOnCheckedChangeListener { _, checked -> preferences.setAlwaysAllow(source, checked) }
            }
            selected.setOnCheckedChangeListener { _, checked ->
                preferences.select("notifications", source, checked)
                allow.isEnabled = checked
            }
            card.addView(selected); card.addView(allow)
            card.addView(LegacyUi.button(this, "系统通知设置") {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, source))
            })
            body.addView(card)
        }
        val ruleCard = LegacyUi.card(this, "由家人设置清理规则", "当前使用关键词，不是 AI 判断。仅对没有选择“始终放行”的来源生效。每行一个词，最多 32 行、2000 字。")
        rules = EditText(this).apply {
            textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            hint = "填写需要清理的关键词"
            contentDescription = "通知清理关键词"
            setText(savedInstanceState?.getString("draftRules") ?: preferences.ruleText())
        }
        ruleCard.addView(rules)
        ruleCard.addView(LegacyUi.button(this, "保存规则", true) {
            val terms = rules.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val value = terms.joinToString("\n")
            if (terms.size > 32 || value.length > 2000) {
                rules.error = "请减少到 32 行、2000 字以内"
            } else {
                preferences.saveRules(value)
                rules.error = null
                Toast.makeText(this, if (value.isBlank()) "已清空规则，不会按关键词清理" else "规则已保存", Toast.LENGTH_SHORT).show()
            }
        })
        body.addView(ruleCard)
        body.addView(LegacyUi.text(this, "清理发生在通知到达之后，已经响起的声音或弹出的横幅可能仍会出现。需要彻底关闭某类通知时，请使用上方系统设置。", 16f))
    }

    override fun onStart() { super.onStart(); Session.observeState(stateObserver) }
    override fun onStop() { Session.removeStateObserver(stateObserver); super.onStop() }

    override fun onResume() { super.onResume(); refreshStatus(); loadSummary() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("draftRules", rules.text.toString()); super.onSaveInstanceState(outState) }
    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val state = ServiceHealth(this).notificationStatus()
        status.text = "${state.title}\n${state.detail}"
        refreshing = true; execution.isChecked = Session.notificationExecution; refreshing = false
    }
    private fun checkConnection() {
        if (!ServiceHealth(this).notificationStatus().authorized) {
            AlertDialog.Builder(this).setTitle("允许后生管理所选通知")
                .setMessage("系统授权允许后生读取通知。后生只处理你勾选的应用，不保存通知标题和正文。你可以随时在系统设置中关闭。")
                .setPositiveButton("去系统设置") { _, _ -> openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .setNegativeButton("暂不设置", null).show()
        } else {
            NotificationListenerService.requestRebind(ComponentName(this, NotificationService::class.java))
            refreshStatus()
            Toast.makeText(this, "授权仍在，已请求系统重新连接；稍后可返回此页查看", Toast.LENGTH_LONG).show()
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
                result.onSuccess { snapshot ->
                    sourceCounts.forEach { (source, view) ->
                        val row = snapshot.notifications.firstOrNull { it.source == source }
                        view.text = if (row == null) "最近 7 天暂无记录" else "最近 7 天：${row.received} 次到达，${row.removalRequested} 次清理请求"
                    }
                    summary.text = if (snapshot.notifications.isEmpty()) "暂时没有记录。选择来源并连接通知服务后开始统计，不代表手机没有收到通知。"
                    else "已记录 ${snapshot.notifications.sumOf { it.received }} 次通知到达\n其中 ${snapshot.notifications.sumOf { it.removalRequested }} 次已请求清理，${snapshot.notifications.sumOf { it.`protected` }} 次受保护。\n次数包含更新，仅覆盖已选择并收到回调的来源。"
                }.onFailure { summary.text = "暂时无法读取统计，请稍后返回重试。"; sourceCounts.values.forEach { it.text = "统计暂不可用" } }
            }
        }
    }
    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { Toast.makeText(this, "无法打开此入口，请在手机设置中查看通知权限", Toast.LENGTH_LONG).show() }
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
