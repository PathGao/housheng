package io.github.pathgao.housheng.fixture

import android.Manifest
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*

class MainActivity : Activity() {
    private lateinit var body: LinearLayout
    private var index = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        showScene(intent.getStringExtra("scene"))
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); showScene(intent.getStringExtra("scene")) }
    private fun showScene(scene: String?) {
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 80, 32, 60); fitsSystemWindows = true }
        setContentView(body)
        if (scene in listOf("feed", "model-feed", "advertisement", "unknown")) { index = 0; showCard(scene!!); return }
        body.addView(TextView(this).apply { text = "后生验证场\n只含合成测试内容"; textSize = 25f })
        button("普通信息流场景") { index = 0; showCard("feed") }
        button("模型下滑验证") { index = 0; showCard("model-feed") }
        button("广告保护场景") { index = 0; showCard("advertisement") }
        button("未知类型保护场景") { index = 0; showCard("unknown") }
        button("发送4条测试通知") { sendNotifications() }
        button("清除测试通知") { getSystemService(NotificationManager::class.java).cancelAll() }
        button("返回后生") { startActivity(Intent().setClassName("io.github.pathgao.housheng", "io.github.pathgao.housheng.MainActivity")) }
        if (scene == "notifications") sendNotifications()
        if (scene == "cleanup") getSystemService(NotificationManager::class.java).cancelAll()
    }
    private fun showCard(scene: String) {
        body.removeAllViews()
        val card = TextView(this).apply {
            textSize = 28f
            setPadding(24, 60, 24, 60)
            setBackgroundColor(Color.rgb(235, 242, 237))
            text = when {
                index > 0 -> "普通内容 · 第2条\n公园散步，认识身边的树。"
                scene == "advertisement" -> "广告测试 · 第1条\n震惊内幕（合成广告，必须保留）"
                scene == "unknown" -> "未知类型 · 第1条\n震惊内幕（无法确认，必须保留）"
                scene == "model-feed" -> "这条消息必须转发二十个群，不转发的家庭一定会遭灾！"
                else -> "普通用户内容 · 第1条\n震惊内幕：测试标题党内容。"
            }
            contentDescription = if (scene == "feed" || scene == "model-feed" && index == 0) "housheng-content:$index" else "housheng-$scene:$index"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isScrollable = true
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                }
                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                        index++
                        showCard(scene)
                        return true
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
        }
        body.addView(card, LinearLayout.LayoutParams(-1, 0, 1f))
        button("手动下一条") { index++; showCard(scene) }
        button("回验证场首页") { showScene(null) }
        body.post { body.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) }
    }
    private fun button(title: String, action: () -> Unit) { body.addView(Button(this).apply { text = title; setOnClickListener { action() } }) }
    private fun sendNotifications() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("validation", "合成测试通知", NotificationManager.IMPORTANCE_LOW))
        val examples = listOf(
            Triple(1, "震惊内幕 · 合成营销通知", Notification.CATEGORY_PROMO),
            Triple(2, "普通通知 · 今天天气晴朗", Notification.CATEGORY_STATUS),
            Triple(3, "震惊内幕 · 合成消息，必须保护", Notification.CATEGORY_MESSAGE),
            Triple(4, "震惊内幕 · 常驻任务，必须保护", Notification.CATEGORY_SERVICE)
        )
        for ((id, text, category) in examples) {
            manager.notify(id, Notification.Builder(this, "validation").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(text).setContentText("这是后生验证场生成的测试数据")
                .setCategory(category).setOngoing(id == 4).build())
        }
    }
}
