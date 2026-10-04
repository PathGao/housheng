package io.github.pathgao.housheng

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** Keyword groups offered by the setup questions. Words not in any group are the user's own and survive a re-run. */
object SetupPresets {
    class Pack(val title: String, val words: List<String>)
    val packs = listOf(
        Pack("促销红包", listOf("红包", "领取", "秒杀", "限时", "优惠券", "免费领", "抽奖", "补贴", "低价")),
        Pack("标题党", listOf("震惊", "内幕", "速看", "突发", "曝光", "惊呆", "太可怕", "万万没想到", "紧急扩散")),
        Pack("八卦和家庭争吵", listOf("出轨", "小三", "离婚", "婆媳", "彩礼", "啃老", "不孝", "白眼狼", "养儿防老"))
    )

    /** A group counts as chosen only when all its words are in the rules; with none of them present, every group starts chosen. */
    fun chosen(rules: String): Set<Int> {
        val lines = rules.lines().map { it.trim() }.toSet()
        if (packs.none { p -> p.words.any { it in lines } }) return packs.indices.toSet()
        return packs.indices.filter { lines.containsAll(packs[it].words) }.toSet()
    }

    fun merge(rules: String, chosen: Set<Int>): String {
        val preset = packs.flatMap { it.words }.toSet()
        val own = rules.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in preset }
        return (own + chosen.sorted().flatMap { packs[it].words }).distinct().take(32).joinToString("\n")
    }
}

class SetupActivity : Activity() {
    private lateinit var preferences: Preferences
    private lateinit var body: LinearLayout
    private lateinit var back: Button
    private lateinit var next: Button
    private lateinit var installed: List<String>
    private lateinit var apps: MutableSet<String>
    private lateinit var packs: MutableSet<Int>
    private var clean = false
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.initialize(this)
        preferences = Preferences(this)
        installed = AppCatalog.sources.keys.filter { it != AppCatalog.FIXTURE && isInstalled(it) }
        val current = preferences.selected("notifications").filter { it in installed }
        apps = savedInstanceState?.getStringArrayList("apps")?.toMutableSet() ?: current.ifEmpty { installed }.toMutableSet()
        packs = savedInstanceState?.getIntegerArrayList("packs")?.toMutableSet() ?: SetupPresets.chosen(preferences.ruleText()).toMutableSet()
        clean = savedInstanceState?.getBoolean("clean") ?: Session.notificationExecution
        step = savedInstanceState?.getInt("step") ?: 0
        val page = ProductUi.page(this, "一键设置")
        body = page.body
        back = ProductUi.button(this, "上一步") { step--; show() }
        next = ProductUi.button(this, "", ButtonKind.FILLED) { if (step < 2) { step++; show() } else finishSetup() }
        ProductUi.bottomBar(this, page, back to 1f, next to 1f)
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("apps", ArrayList(apps))
        outState.putIntegerArrayList("packs", ArrayList(packs))
        outState.putBoolean("clean", clean)
        outState.putInt("step", step)
        super.onSaveInstanceState(outState)
    }

    private fun isInstalled(source: String) =
        try { packageManager.getPackageInfo(source, 0); true } catch (_: PackageManager.NameNotFoundException) { false }

    private fun show() {
        body.removeAllViews()
        back.isEnabled = step > 0
        next.text = if (step < 2) "下一步" else "完成"
        body.addView(ProductUi.text(this, "第 ${step + 1} 问，共 3 问", Type.SUPPORT).apply { setPadding(ProductUi.dp(context, 4), 0, 0, 0) })
        when (step) {
            0 -> {
                question("哪些应用的通知太多？")
                if (installed.isEmpty()) {
                    body.addView(ProductUi.helper(this, "手机上没有找到可管理的应用。现在支持：${AppCatalog.sources.filterKeys { it != AppCatalog.FIXTURE }.values.joinToString("、")}。"))
                    next.isEnabled = false
                    return
                }
                body.addView(ProductUi.group(this).apply {
                    for (source in installed) addView(ProductUi.switchRow(context, AppCatalog.sources.getValue(source), leading = ProductUi.appIcon(context, source)).apply {
                        isChecked = source in apps
                        setOnCheckedChangeListener { _, checked -> if (checked) apps += source else apps -= source; next.isEnabled = apps.isNotEmpty() }
                    })
                })
                body.addView(ProductUi.helper(this, "打开的应用会被记录次数。"))
                next.isEnabled = apps.isNotEmpty()
            }
            1 -> {
                question("想清理哪类通知？")
                body.addView(ProductUi.group(this).apply {
                    SetupPresets.packs.forEachIndexed { index, pack ->
                        addView(ProductUi.switchRow(context, pack.title, pack.words.take(4).joinToString("、") + "…").apply {
                            isChecked = index in packs
                            setOnCheckedChangeListener { _, checked -> if (checked) packs += index else packs -= index }
                        })
                    }
                })
                body.addView(ProductUi.helper(this, "通知里有这些词就清理。电话、短信、闹钟始终保留。"))
                next.isEnabled = true
            }
            else -> {
                question("现在就开始清理吗？")
                body.addView(ProductUi.group(this).apply {
                    addView(ProductUi.switchRow(context, "现在清理", "关闭时只记录次数").apply {
                        isChecked = clean
                        setOnCheckedChangeListener { _, checked -> clean = checked }
                    })
                })
                body.addView(ProductUi.helper(this, "不确定就先只记录，过几天看家庭报告再决定。之后可以在首页暂停。"))
                if (!ServiceHealth(this).notificationStatus().authorized)
                    body.addView(ProductUi.helper(this, "点完成后会打开系统设置，请打开“后生”的开关。后生只处理你选的应用，不保存通知内容。"))
                next.isEnabled = true
            }
        }
    }

    private fun question(value: String) {
        body.addView(ProductUi.text(this, value, Type.TITLE).apply {
            isAccessibilityHeading = true
            setPadding(ProductUi.dp(context, 4), ProductUi.dp(context, 8), 0, ProductUi.dp(context, 16))
        })
    }

    private fun finishSetup() {
        for (source in AppCatalog.sources.keys) if (source != AppCatalog.FIXTURE) preferences.select("notifications", source, source in apps)
        // Picking an app here means "clean it"; an older always-allow would silently cancel that.
        apps.forEach { preferences.setAlwaysAllow(it, false) }
        preferences.saveRules(SetupPresets.merge(preferences.ruleText(), packs))
        Session.notificationExecution = clean
        Toast.makeText(this, "设置好了", Toast.LENGTH_SHORT).show()
        if (!ServiceHealth(this).notificationStatus().authorized && !openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "无法打开此入口，请在手机设置中查看通知权限", Toast.LENGTH_LONG).show()
        finish()
    }
}
