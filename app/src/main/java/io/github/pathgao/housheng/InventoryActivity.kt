package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class InventoryActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var apps = emptyList<InstalledApp>()
    private var icons = emptyMap<String, Drawable>()
    private var scannedAt = 0L
    private var scanning = false
    private var failed = false
    private var rows = emptyList<Any>()
    private lateinit var summary: TextView
    private lateinit var start: Button
    private lateinit var refresh: Button
    private lateinit var limits: TextView
    private val dates = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val shortDate = SimpleDateFormat("M月d日", Locale.CHINA)
    private val longDate = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    private val year = SimpleDateFormat("yyyy", Locale.CHINA)
    private val scanTime = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)

    /** Section title or the one-sentence helper under a section. */
    private data class Caption(val value: String, val helper: Boolean)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val page = ProductUi.page(this, "应用清单")
        refresh = ProductUi.appBarAction(page, "刷新") { scan() }
        val gutter = if (resources.configuration.screenWidthDp >= 600) 48 else 16
        summary = ProductUi.text(this, "", Type.SUPPORT).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        start = ProductUi.button(this, "读取应用清单", ButtonKind.TONAL) { scan() }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
        }
        limits = ProductUi.helper(this, "不含隐藏的应用，最多显示 500 个。")
        // ponytail: ListView instead of page.scroll so up to 500 rows recycle.
        val list = ListView(this).apply {
            divider = null
            selector = ColorDrawable(0)
            clipToPadding = false
            setPadding(dp(gutter), 0, dp(gutter), dp(24))
            addHeaderView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(summary); addView(start)
            }, null, false)
            addFooterView(limits, null, false)
            adapter = this@InventoryActivity.adapter
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        page.root.addView(list, page.root.indexOfChild(page.scroll))
        page.root.removeView(page.scroll)
        ProductUi.bottomBar(this, page, ProductUi.button(this, "分享清单", ButtonKind.TONAL) { preview() } to 1f)
        (lastNonConfigurationInstance as? InventoryState)?.let {
            apps = it.apps
            icons = it.icons
            scannedAt = it.scannedAt
        }
        render()
    }

    private fun dp(value: Int) = ProductUi.dp(this, value)

    private fun scan() {
        val request = ++generation
        scanning = true
        render()
        worker.execute {
            val now = System.currentTimeMillis()
            val result = runCatching {
                val found = loadInstalledApps(this)
                found to found.associate { it.packageName to ProductUi.appIcon(this, it.packageName) }
            }
            runOnUiThread {
                if (isDestroyed || request != generation) return@runOnUiThread
                scanning = false
                failed = result.isFailure
                result.onSuccess { (found, loaded) -> apps = found; icons = loaded; scannedAt = now }
                render()
            }
        }
    }

    private fun render() {
        summary.text = when {
            scanning -> "正在读取本机应用…"
            failed -> "读取失败，请重试。"
            scannedAt == 0L -> "还没读取。只看应用名和安装时间，不读取应用内容。"
            else -> "共 ${apps.size} 个应用 · ${scanTime.format(Date(scannedAt))} 读取"
        }
        start.visibility = if (scannedAt == 0L) View.VISIBLE else View.GONE
        start.isEnabled = !scanning
        refresh.isEnabled = !scanning
        limits.visibility = if (scannedAt == 0L) View.GONE else View.VISIBLE
        val (recent, other) = apps.partition { installedInLast30Days(it, scannedAt) }
        rows = buildList {
            if (recent.isNotEmpty()) {
                add(Caption("近 30 天新装 · ${recent.size} 个", false))
                addAll(recent)
                add(Caption("新装不代表有害，可以和家人一起看看用途。", true))
            }
            if (other.isNotEmpty()) {
                add(Caption("其他应用 · ${other.size} 个", false))
                addAll(other)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun installed(app: InstalledApp): String = when {
        app.firstInstalledAt <= 0 || app.firstInstalledAt > scannedAt -> "安装日期未知"
        year.format(Date(app.firstInstalledAt)) == year.format(Date(scannedAt)) -> "${shortDate.format(Date(app.firstInstalledAt))}安装"
        else -> "${longDate.format(Date(app.firstInstalledAt))}安装"
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is InstalledApp) 1 else 0
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = rows[position]
            if (item is Caption) return if (item.helper) ProductUi.helper(parent.context, item.value).apply { setPadding(paddingLeft, paddingTop, paddingRight, dp(4)) }
            else ProductUi.section(parent.context, item.value)
            val app = item as InstalledApp
            val cell = convertView as? LinearLayout ?: appCell()
            val row = cell.getChildAt(0) as LinearLayout
            val first = rows.getOrNull(position - 1) !is InstalledApp
            val last = rows.getOrNull(position + 1) !is InstalledApp
            val top = if (first) dp(16).toFloat() else 0f
            val bottom = if (last) dp(16).toFloat() else 0f
            fun shape() = GradientDrawable().apply {
                setColor(getColor(R.color.housheng_surface))
                cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            }
            cell.background = shape()
            row.background = RippleDrawable(ColorStateList.valueOf(getColor(R.color.housheng_ripple)), null, shape())
            (row.getChildAt(0) as ImageView).setImageDrawable(icons[app.packageName])
            (row.getChildAt(1) as TextView).text = ProductUi.twoLine(this@InventoryActivity, app.name, installed(app))
            row.setOnClickListener { details(app) }
            cell.getChildAt(1).visibility = if (last) View.GONE else View.VISIBLE
            return cell
        }
    }

    /** A navRow over an inset divider, rebound on recycle. */
    private fun appCell() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(ProductUi.navRow(context, "", leading = ColorDrawable(0)) {}.view)
        addView(View(context).apply {
            background = InsetDrawable(ColorDrawable(getColor(R.color.housheng_divider)), dp(72), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        })
    }

    private fun details(app: InstalledApp) {
        AlertDialog.Builder(this).setTitle(app.name).setMessage("${app.packageName}\n后生尚未判断该应用是否安全。打开系统详情后，可由你选择卸载或调整权限。")
            .setPositiveButton("打开系统详情") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) }
                    .onFailure { Toast.makeText(this, "无法打开系统详情，应用可能已卸载", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("取消", null).show()
    }

    private fun description(app: InstalledApp): String {
        val date = if (app.firstInstalledAt > 0 && app.firstInstalledAt <= scannedAt) dates.format(Date(app.firstInstalledAt)) else "未知"
        return "${app.name}${if (installedInLast30Days(app, scannedAt)) " · 近30天安装，待家人核实" else ""}\n${app.packageName}\n系统首次安装：$date"
    }

    private fun preview() {
        if (scannedAt == 0L) { Toast.makeText(this, "请先读取清单", Toast.LENGTH_SHORT).show(); return }
        val counts = "当前可见 ${apps.size} 个应用，近30天安装 ${apps.count { installedInLast30Days(it, scannedAt) }} 个。\n查询时间：${dates.format(Date(scannedAt))}。不含已卸载、隐藏或无桌面入口的应用，最多显示500个。"
        val report = "后生 · 本机应用清单\n$counts\n\n仅供家人核实；新增、名单外均不代表有害。安装日期取系统首次安装时间，不是后生首次发现时间，不能恢复已卸载历史。\n\n${apps.joinToString("\n\n") { description(it) }}"
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

    private data class InventoryState(val apps: List<InstalledApp>, val icons: Map<String, Drawable>, val scannedAt: Long)
    override fun onRetainNonConfigurationInstance(): Any = InventoryState(apps, icons, scannedAt)
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
}
