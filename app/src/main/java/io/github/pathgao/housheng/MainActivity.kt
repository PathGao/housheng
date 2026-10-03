package io.github.pathgao.housheng

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val stateObserver: () -> Unit = { refreshStatus() }
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        val body = ProductUi.page(this, "后生", "帮爸妈看一眼。")
        val status = ProductUi.card(this, "通知照看")
        statusTitle = ProductUi.text(this, "", 24f, true)
        statusDetail = ProductUi.text(this, "")
        status.addView(statusTitle)
        status.addView(statusDetail)
        status.addView(ProductUi.button(this, "管理通知", true) { open(NotificationsActivity::class.java) })
        body.addView(status)

        val family = ProductUi.card(this, "和孩子一起看", "查看通知来源、频率和设置。分享前由你预览，孩子可以帮你判断怎么调整。")
        family.addView(ProductUi.button(this, "查看家庭报告") { open(ReportActivity::class.java) })
        body.addView(family)

        val apps = ProductUi.card(this, "手机里装了什么", "主动查看本机应用和近 30 天安装记录。新安装不等于有害，交给家人一起核实。")
        apps.addView(ProductUi.button(this, "查看应用清单") { open(InventoryActivity::class.java) })
        body.addView(apps)

        body.addView(ProductUi.card(this, "信息流筛选 · 尚未开放", "真实应用的信息流还未接入。日常通知管理和应用清单不需要开启无障碍权限。"))
        body.addView(ProductUi.button(this, "暂停所有自动处理") {
            Session.stop()
            refreshStatus()
            Toast.makeText(this, "已暂停自动处理，观察记录仍会保留", Toast.LENGTH_LONG).show()
        })
        body.addView(ProductUi.button(this, "设置与诊断") { open(DiagnosticsActivity::class.java) })
        body.addView(ProductUi.text(this, "只处理你选择的通知来源。资料留在本机，分享由你发起。后生不会跳过广告，也不会自动卸载应用。").apply {
            setTextColor(getColor(R.color.housheng_secondary))
        })
    }

    override fun onStart() { super.onStart(); Session.observeState(stateObserver) }
    override fun onStop() { Session.removeStateObserver(stateObserver); super.onStop() }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        if (!::statusTitle.isInitialized) return
        val state = ServiceHealth(this).notificationStatus()
        statusTitle.text = state.title
        val chosen = Preferences(this).selected("notifications").count { it != AppCatalog.FIXTURE }
        statusDetail.text = state.detail + if (chosen == 0) "\n尚未选择日常应用，请在“管理通知”中选择。" else "\n已选择 $chosen 个日常应用。"
    }

    private fun open(activity: Class<out Activity>) = startActivity(Intent(this, activity))
}
