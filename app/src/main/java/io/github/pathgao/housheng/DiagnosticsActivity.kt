package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.View
import android.widget.*
import java.io.File

class DiagnosticsActivity : Activity() {
    private val stateObserver: () -> Unit = { refresh() }
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var notificationSwitch: Switch
    private lateinit var feedSwitch: Switch
    private var updatingSwitches = false
    private val preferences by lazy { Preferences(this) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        body = ProductUi.page(this, "设置与诊断", "供孩子协助设置和开发验证")
        label("日常规则使用本地关键词。只有验证场开放页面读取和自动翻页；调试版可通过 USB 验证本机模型。", 18f)
        button("应用清单 / 近30天安装 / 分享给孩子") { startActivity(Intent(this, InventoryActivity::class.java)) }
        button("查看本次信息流统计") {
            fun counts(testData: Boolean): String {
                val counts = Session.statistics.counts(testData)
                if (counts.isEmpty()) return "尚无有效样本"
                return "观察到 ${counts.values.sum()} 次内容展示\n" + counts.entries.joinToString("\n") { "${it.key.label}：${it.value}次" }
            }
            AlertDialog.Builder(this).setTitle("本次信息流统计")
                .setMessage("真实应用\n${counts(false)}\n\n验证场（不计入父母统计）\n${counts(true)}\n\n模型未接入，内容记为未分类。真实应用未完成页面适配，不采集样本。次数表示观察到的展示，不代表观看时长。仅保存在本次进程，清空记录或进程结束后清除。")
                .setPositiveButton("关闭", null).show()
        }
        status = label("", 15f)
        button("停止所有自动执行") {
            Session.stop()
            notificationSwitch.isChecked = false
            feedSwitch.isChecked = false
            refresh()
        }
        label("系统授权", 21f)
        button("打开通知使用权设置") {
            AlertDialog.Builder(this).setTitle("允许读取通知")
                .setMessage("系统会授予后生读取通知的能力。后生只处理下方勾选的来源，记录中不保存通知正文。授权后请返回本页选择来源。")
                .setPositiveButton("去设置") { _, _ -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                .setNegativeButton("暂不", null).show()
        }
        button("打开无障碍设置") {
            AlertDialog.Builder(this).setTitle("允许观察选定应用")
                .setMessage("只允许读取名单中已适配的信息流页。目前仅验证场可用，真实应用页面读取暂未开放。浏览器、聊天、账号等页面不采集。截图仅在验证场手动预约。")
                .setPositiveButton("去设置") { _, _ -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                .setNegativeButton("暂不", null).show()
        }
        button("管理应用通知权限") { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, packageName) }
        label("观察来源", 21f)
        label("首批名单：抖音、快手、小红书及所列极速版。浏览器、微信、QQ和名单外应用不采集。真实应用信息流尚未完成页面适配，页面选项暂不可用；应用清单不会授权内容读取。", 15f)
        for ((source, name) in Session.sources) {
            label(name, 17f)
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for ((kind, title) in listOf("notifications" to "通知", "pages" to "页面")) {
                row.addView(CheckBox(this).apply {
                    text = title
                    textSize = 18f
                    minHeight = dp(56)
                    contentDescription = "$name$title"
                    isEnabled = AppCatalog.permits(source, kind)
                    if (!isEnabled) text = "信息流待适配"
                    isChecked = source in preferences.selected(kind)
                    setOnCheckedChangeListener { _, checked ->
                        preferences.select(kind, source, checked)
                        FeedService.instance?.invalidate()
                        FeedService.instance?.cancelCapture()
                        Session.latestPage = "来源已调整，等待新页面"
                    }
                })
            }
            row.addView(Button(this).apply { text = "通知设置"; setOnClickListener { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, source) } })
            body.addView(row)
        }
        label("筛选规则", 21f)
        label("每行一个关键词，最多32条。仅验证明确规则，不代表内容质量判断。空规则全部放行。重要、常驻和分组摘要通知不自动清理。", 15f)
        val rules = EditText(this).apply {
            setText(preferences.ruleText())
            hint = "例如：震惊内幕"
            minLines = 2
            filters = arrayOf(InputFilter.LengthFilter(2000))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        body.addView(rules)
        button("保存规则") { preferences.saveRules(rules.text.toString()); FeedService.instance?.invalidate(); toast("规则已保存") }
        notificationSwitch = Switch(this).apply {
            text = "执行通知清理（仅勾选来源）"
            textSize = 18f
            minHeight = dp(56)
            setOnCheckedChangeListener { _, checked ->
                if (updatingSwitches) return@setOnCheckedChangeListener
                if (checked && !NotificationService.connected) { isChecked = false; toast(ServiceHealth(this@DiagnosticsActivity).notificationStatus().detail) }
                else Session.notificationExecution = checked
                refresh()
            }
        }
        body.addView(notificationSwitch)
        feedSwitch = Switch(this).apply {
            text = "执行内容翻页（仅验证场）"
            textSize = 18f
            minHeight = dp(56)
            setOnCheckedChangeListener { _, checked ->
                if (updatingSwitches) return@setOnCheckedChangeListener
                if (checked && FeedService.instance == null) { isChecked = false; toast(ServiceHealth(this@DiagnosticsActivity).pageStatus().detail) }
                else { Session.feedExecution = checked; FeedService.instance?.invalidate() }
                refresh()
            }
        }
        body.addView(feedSwitch)
        label("验证与诊断", 21f)
        ModelValidation.addControls(body)
        button("打开后生验证场") {
            runCatching { startActivity(Intent().setClassName(Session.FIXTURE, "${Session.FIXTURE}.MainActivity")) }
                .onFailure { toast("请安装独立的后生验证场 APK") }
        }
        button("5秒后截图一次") {
            if (FeedService.instance == null) toast(ServiceHealth(this@DiagnosticsActivity).pageStatus().detail)
            else AlertDialog.Builder(this).setTitle("截取一次当前屏幕")
                .setMessage("5秒内打开已勾选的验证场。真实应用截图暂未开放。整屏图片可能含状态栏信息，只保存到私有缓存，不上传，返回后可删除。")
                .setPositiveButton("预约截图") { _, _ -> FeedService.instance?.captureAfterDelay(); toast("5秒内打开目标应用") }
                .setNegativeButton("取消", null).show()
        }
        button("预览最近截图") {
            val file = File(cacheDir, "capture.png")
            if (!file.exists()) toast("还没有截图") else {
                val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })
                val image = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true }
                AlertDialog.Builder(this).setTitle("仅保存在本机").setView(image).setPositiveButton("关闭", null).show()
                    .setOnDismissListener { image.setImageDrawable(null); bitmap?.recycle() }
            }
        }
        button("删除截图") { FeedService.instance?.cancelCapture(); File(cacheDir, "capture.png").delete(); toast("截图已删除") }
        button("刷新观察结果") { refresh() }
        button("清空本次记录") { Session.clear(); refresh() }
        details = label("", 14f).apply { setTextIsSelectable(true) }
        refresh()
    }

    override fun onStart() { super.onStart(); Session.observeState(stateObserver) }
    override fun onStop() { Session.removeStateObserver(stateObserver); super.onStop() }

    override fun onResume() {
        super.onResume()
        refresh()
    }
    private fun label(value: String, size: Float = 18f): TextView = ProductUi.text(this, value, maxOf(size, 18f)).also { body.addView(it) }
    private fun button(value: String, action: () -> Unit) { body.addView(ProductUi.button(this, value, action = action)) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun openSettings(action: String, source: String? = null) {
        runCatching { startActivity(Intent(action).apply { source?.let { putExtra(Settings.EXTRA_APP_PACKAGE, it) } }) }
            .onFailure { toast("此设备没有对应设置入口") }
    }
    private fun refresh() {
        updatingSwitches = true
        if (::notificationSwitch.isInitialized) notificationSwitch.isChecked = Session.notificationExecution
        if (::feedSwitch.isInitialized) feedSwitch.isChecked = Session.feedExecution
        updatingSwitches = false
        if (::status.isInitialized) status.text = "通知：${ServiceHealth(this).notificationStatus().title}\n页面：${ServiceHealth(this).pageStatus().title}"
        if (::details.isInitialized) details.text = "最近页面（最多4000字）\n${Session.latestPage}\n\n本次记录（最多50条）\n${Session.journal()}"
    }
}
