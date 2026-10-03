package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class InventoryActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var apps = emptyList<InstalledApp>()
    private var scannedAt = 0L
    private lateinit var summary: TextView
    private lateinit var list: ListView
    private lateinit var scan: Button
    private val dates = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = ProductUi.page(this, "手机里的应用", "主动查询，一起核实。")
        body.addView(ProductUi.text(this, "只查看应用名称和安装时间，不读取应用内容。清单留在本页，分享前可以预览。"))
        scan = ProductUi.button(this, "读取 / 刷新清单", true) { scan() }
        body.addView(scan)
        summary = ProductUi.text(this, "尚未读取。新安装不等于有害应用，需家人核实。")
        body.addView(summary)
        body.addView(ProductUi.button(this, "预览、复制或分享给孩子") { preview() })
        body.addView(ProductUi.text(this, "点选应用可打开系统详情，由家人决定是否卸载；后生不会自动卸载。"))
        body.addView(ProductUi.button(this, "清空本页清单") { generation++; apps = emptyList(); scannedAt = 0; render() })
        (body.parent as ViewGroup).removeView(body)
        list = ListView(this).apply {
            setBackgroundColor(getColor(R.color.housheng_background))
            dividerHeight = ProductUi.dp(this@InventoryActivity, 8)
            addHeaderView(body, null, false)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
        setContentView(list)
        (lastNonConfigurationInstance as? InventoryState)?.let {
            apps = it.apps
            scannedAt = it.scannedAt
        }
        render()
        list.setOnItemClickListener { _, _, position, _ ->
            val app = apps.getOrNull(position - list.headerViewsCount) ?: return@setOnItemClickListener
            AlertDialog.Builder(this).setTitle(app.name).setMessage("${app.packageName}\n后生尚未判断该应用是否安全。打开系统详情后，可由你选择卸载或调整权限。")
                .setPositiveButton("打开系统详情") { _, _ ->
                    runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) }
                        .onFailure { Toast.makeText(this, "无法打开系统详情，应用可能已卸载", Toast.LENGTH_SHORT).show() }
                }.setNegativeButton("取消", null).show()
        }
    }

    private fun scan() {
        val request = ++generation
        scan.isEnabled = false
        summary.text = "正在查询本机应用…"
        worker.execute {
            val now = System.currentTimeMillis()
            val result = runCatching {
                loadInstalledApps(this)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                scan.isEnabled = true
                if (request != generation) return@runOnUiThread
                result.onSuccess { apps = it; scannedAt = now; render() }
                    .onFailure { summary.text = "查询失败，请重试。下方如有清单，仍是上次结果。" }
            }
        }
    }

    private fun description(app: InstalledApp): String {
        val date = if (app.firstInstalledAt > 0 && app.firstInstalledAt <= scannedAt) dates.format(Date(app.firstInstalledAt)) else "未知"
        return "${app.name}${if (installedInLast30Days(app, scannedAt)) " · 近30天安装，待家人核实" else ""}\n${app.packageName}\n系统首次安装：$date"
    }
    private fun render() {
        summary.text = if (scannedAt == 0L) "尚未读取 / 已清空" else "当前可见 ${apps.size} 个应用，近30天安装 ${apps.count { installedInLast30Days(it, scannedAt) }} 个。\n查询时间：${dates.format(Date(scannedAt))}。不含已卸载、隐藏或无桌面入口的应用，最多显示500个。"
        list.adapter = object : ArrayAdapter<InstalledApp>(this, android.R.layout.simple_list_item_1, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    text = description(apps[position])
                    textSize = 18f
                    setTextColor(getColor(R.color.housheng_text))
                    setBackgroundColor(getColor(R.color.housheng_surface))
                    setPadding(ProductUi.dp(context, 24), ProductUi.dp(context, 20), ProductUi.dp(context, 24), ProductUi.dp(context, 20))
                    minHeight = ProductUi.dp(context, 72)
                    setLineSpacing(ProductUi.dp(context, 4).toFloat(), 1f)
                }
            }
        }
    }
    private fun preview() {
        if (scannedAt == 0L) { Toast.makeText(this, "请先读取清单", Toast.LENGTH_SHORT).show(); return }
        val report = "后生 · 本机应用清单\n${summary.text}\n\n仅供家人核实；新增、名单外均不代表有害。安装日期取系统首次安装时间，不是后生首次发现时间，不能恢复已卸载历史。\n\n${apps.joinToString("\n\n") { description(it) }}"
        AlertDialog.Builder(this).setTitle("预览将分享的应用清单").setMessage(report)
            .setPositiveButton("系统分享") { _, _ ->
                runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "分享给孩子")) }
                    .onFailure { Toast.makeText(this, "没有可用的分享应用", Toast.LENGTH_SHORT).show() }
            }
            .setNeutralButton("复制") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("后生应用清单", report))
                Toast.makeText(this, "已复制应用清单", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("取消", null).show()
    }
    private data class InventoryState(val apps: List<InstalledApp>, val scannedAt: Long)
    override fun onRetainNonConfigurationInstance(): Any = InventoryState(apps, scannedAt)
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
}
