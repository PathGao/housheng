package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class ReportActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var preview: TextView
    private lateinit var includeApps: CheckBox
    private lateinit var copy: Button
    private lateinit var share: Button
    private lateinit var refresh: Button
    private val periodButtons = mutableListOf<Button>()
    private var days = 7
    private var generation = 0
    private var report: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        days = savedInstanceState?.getInt("days", 7) ?: 7
        val body = ProductUi.page(this, "给孩子看一眼", "先在这里看清楚，再决定是否分享。")
        val controls = ProductUi.card(this, "选择报告范围", "统计保存在这台手机上，最多保留最近 30 天。分享不会自动发送给任何人。")
        controls.addView(ProductUi.button(this, "最近 7 天") { days = 7; load() }.also { periodButtons.add(it) })
        controls.addView(ProductUi.button(this, "最近 30 天") { days = 30; load() }.also { periodButtons.add(it) })
        includeApps = CheckBox(this).apply {
            text = "报告中包含本机应用清单"
            textSize = 18f
            minHeight = ProductUi.dp(context, 56)
            isChecked = savedInstanceState?.getBoolean("includeApps") ?: false
            setOnCheckedChangeListener { _, _ -> load() }
        }
        controls.addView(includeApps)
        controls.addView(ProductUi.text(this, "勾选后才查询有桌面入口的应用名称和安装时间，不读取应用内容。近 30 天安装不代表有害。", 16f))
        refresh = ProductUi.button(this, "重新生成报告") { load() }
        controls.addView(refresh)
        body.addView(controls)
        preview = ProductUi.text(this, "正在整理报告…", 18f).apply { setTextIsSelectable(true) }
        body.addView(ProductUi.card(this, "报告预览").apply { addView(preview) })
        copy = ProductUi.button(this, "复制报告") {
            report?.let {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("后生家庭报告", it))
                Toast.makeText(this, "已复制，可粘贴给孩子", Toast.LENGTH_SHORT).show()
            }
        }
        share = ProductUi.button(this, "选择分享给谁", true) {
            report?.let { text ->
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { startActivity(Intent.createChooser(intent, "分享后生家庭报告")) }
                    .onFailure {
                        android.util.Log.w("Housheng", "Report share unavailable: ${it.javaClass.simpleName}")
                        Toast.makeText(this, "没有可用的分享应用，可复制报告后发送", Toast.LENGTH_LONG).show()
                    }
            }
        }
        body.addView(copy); body.addView(share)
        body.addView(ProductUi.text(this, "孩子可以根据报告帮你调整来源和关键词。当前版本需要在这台手机上修改设置，不能远程控制手机。", 16f))
        body.addView(ProductUi.card(this, "本机记录管理", "清空会删除通知汇总和连接记录，不修改系统权限或通知规则。之后收到的新通知会重新开始统计。").apply {
            addView(ProductUi.button(this@ReportActivity, "清空历史统计") {
                AlertDialog.Builder(this@ReportActivity).setTitle("清空本机历史统计？")
                    .setMessage("过去的统计无法恢复，已经分享出去的报告不会被删除。")
                    .setPositiveButton("清空记录") { _, _ -> clearHistory() }.setNegativeButton("保留", null).show()
            })
        })
        load()
    }

    private fun setLoading() {
        periodButtons.forEach { it.isEnabled = false }
        report = null; copy.isEnabled = false; share.isEnabled = false
        refresh.isEnabled = false; includeApps.isEnabled = false
        preview.text = "正在整理最近 $days 天的报告…"
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
        val state = "通知：${health.notificationStatus().title}；页面：${health.pageStatus().title}。真实信息流尚未开放。"
        setLoading()
        worker.execute {
            val result = runCatching {
                val snapshot = ReportStore(this).use { it.snapshot(window) }
                formatFamilyReport(snapshot, selected, rules, state, if (withApps) loadInstalledApps(this) else null, alwaysAllowedSources = allowed)
            }
            runOnUiThread {
                if (isDestroyed || request != generation) return@runOnUiThread
                refresh.isEnabled = true; includeApps.isEnabled = true; periodButtons.forEach { it.isEnabled = true }
                result.onSuccess {
                    report = it; preview.text = it; copy.isEnabled = true; share.isEnabled = true
                }.onFailure { preview.text = "报告暂时无法生成，请点击“重新生成报告”重试。" }
            }
        }
    }
    private fun clearHistory() {
        ++generation
        setLoading()
        Session.report(this, onFailure = {
            runOnUiThread {
                if (!isDestroyed) {
                    preview.text = "清空失败，请重新查询后重试。"
                    refresh.isEnabled = true; includeApps.isEnabled = true; periodButtons.forEach { it.isEnabled = true }
                }
            }
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
