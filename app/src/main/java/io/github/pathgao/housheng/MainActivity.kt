package io.github.pathgao.housheng

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val stateObserver: () -> Unit = { refreshStatus() }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: StatusPanel
    private lateinit var notifications: ProductUi.NavRow
    private lateinit var pause: Button
    private lateinit var setup: LinearLayout
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        val body = ProductUi.page(this).body
        body.addView(ProductUi.brand(this))
        status = StatusPanel(this) { connectNotifications { refreshStatus() } }
        body.addView(status.view)
        body.addView(ProductUi.spacer(this))
        setup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ProductUi.group(context, inset = true).apply {
                addView(ProductUi.navRow(context, "一键设置", R.drawable.ic_checklist, support = "回答 3 个问题，不用自己调") { open(SetupActivity::class.java) }.view)
            })
            addView(ProductUi.spacer(context))
        }
        body.addView(setup)
        body.addView(ProductUi.group(this, inset = true).apply {
            notifications = ProductUi.navRow(context, "通知清理", R.drawable.ic_notifications) { open(NotificationsActivity::class.java) }
            addView(notifications.view)
            addView(ProductUi.navRow(context, "家庭报告", R.drawable.ic_summarize) { open(ReportActivity::class.java) }.view)
            addView(ProductUi.navRow(context, "应用清单", R.drawable.ic_apps) { open(InventoryActivity::class.java) }.view)
        })
        pause = ProductUi.button(this, "暂停自动清理") {
            Session.stop()
            refreshStatus()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ProductUi.dp(context, 16) }
        body.addView(pause)
        body.addView(ProductUi.spacer(this))
        body.addView(ProductUi.group(this, inset = true).apply {
            addView(ProductUi.navRow(context, "设置与诊断", R.drawable.ic_tune) { open(DiagnosticsActivity::class.java) }.view)
            addView(ProductUi.navRow(context, "后生会做什么", R.drawable.ic_shield) { open(AboutActivity::class.java) }.view)
        })
    }

    override fun onStart() { super.onStart(); Session.observeState(stateObserver) }
    override fun onStop() { Session.removeStateObserver(stateObserver); super.onStop() }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        loadMetrics()
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val state = ServiceHealth(this).notificationStatus()
        status.show(state)
        pause.visibility = if (state == ServiceStatus.EXECUTING) View.VISIBLE else View.GONE
        val chosen = Preferences(this).selected("notifications").count { it != AppCatalog.FIXTURE }
        notifications.value.text = if (chosen == 0) "未选择" else "$chosen 个应用"
        // Before anything is chosen, setup is the first thing to do, so it goes above the status panel.
        val body = setup.parent as LinearLayout
        val wanted = if (chosen == 0) 1 else 3
        if (body.indexOfChild(setup) != wanted) { body.removeView(setup); body.addView(setup, wanted) }
    }

    private fun loadMetrics() {
        val request = ++generation
        worker.execute {
            val result = runCatching { ReportStore(this).use { it.snapshot(7) } }
            runOnUiThread {
                if (isDestroyed || request != generation) return@runOnUiThread
                status.footer.removeAllViews()
                val rows = result.getOrNull()?.notifications ?: return@runOnUiThread
                if (rows.isEmpty()) {
                    status.footer.addView(ProductUi.text(this, "最近 7 天还没有记录。", Type.SUPPORT).apply { setPadding(0, ProductUi.dp(context, 12), 0, 0) })
                    return@runOnUiThread
                }
                status.footer.addView(View(this).apply {
                    setBackgroundColor(getColor(R.color.housheng_divider))
                    layoutParams = LinearLayout.LayoutParams(-1, ProductUi.dp(context, 1)).apply { topMargin = ProductUi.dp(context, 16); bottomMargin = ProductUi.dp(context, 16) }
                })
                status.footer.addView(ProductUi.metrics(this, listOf(
                    "${rows.sumOf { it.received }}" to "7 天收到",
                    "${rows.sumOf { it.removalRequested }}" to "请求清理",
                    "${rows.sumOf { it.`protected` }}" to "受保护"
                )))
            }
        }
    }

    private fun open(activity: Class<out Activity>) = startActivity(Intent(this, activity))
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
}
