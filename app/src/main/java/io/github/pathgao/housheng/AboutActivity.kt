package io.github.pathgao.housheng

import android.app.Activity
import android.os.Bundle

class AboutActivity : Activity() {
    private val sections = listOf(
        "会做的事" to listOf(
            "只处理你选的应用发来的通知。",
            "按天记录通知次数，保存最近 30 天。",
            "报告先给你看，由你决定是否分享。"
        ),
        "不会做的事" to listOf(
            "不保存通知标题和正文。",
            "不读浏览器和聊天应用。",
            "不自动发送报告，没有账号和远程控制。",
            "不跳过广告，不自动卸载应用。"
        ),
        "还没开放" to listOf(
            "抖音、快手仍未开放。调试版可另行启用小红书发现页标题筛选，标题会发送给 Cloudflare Clef，图片和私信不上传。"
        ),
        "需要知道的" to listOf(
            "清理在通知到达后进行，已经响起的提示音无法撤回。",
            "想彻底关闭某个应用的通知，用系统通知设置。"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = ProductUi.page(this, "后生会做什么").body
        for ((title, lines) in sections) {
            body.addView(ProductUi.section(this, title))
            body.addView(ProductUi.group(this).apply {
                lines.forEach { line ->
                    addView(ProductUi.text(context, line, Type.BODY).apply {
                        minHeight = ProductUi.dp(context, 56)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(ProductUi.dp(context, 16), ProductUi.dp(context, 12), ProductUi.dp(context, 16), ProductUi.dp(context, 12))
                    })
                }
            })
        }
    }
}
