package io.github.pathgao.housheng

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText

class ClefSettingsActivity : Activity() {
    private var testing = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val store = ClefCredentials(this)
        val body = ProductUi.page(this, "Clef API 配置").body
        body.addView(ProductUi.text(this, "手机直连会将已启用来源的内容文字发送给 Cloudflare。凭据在本机加密保存。", Type.SUPPORT))
        ModelTransport.addControls(body)
        body.addView(ProductUi.text(this, "Account ID", Type.SUPPORT))
        val account = EditText(this).apply {
            hint = "32 位 Account ID"
            textSize = Type.BODY.sp
            minHeight = ProductUi.dp(context, 60)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(32))
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        body.addView(account)
        body.addView(ProductUi.text(this, "API Token", Type.SUPPORT))
        val token = EditText(this).apply {
            hint = "填入新 Token，保存后清空输入框"
            textSize = Type.BODY.sp
            minHeight = ProductUi.dp(context, 60)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(1024))
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        body.addView(token)
        val status = ProductUi.text(this, if (runCatching { store.load() != null }.getOrDefault(false)) "已有已保存的凭据" else "尚未配置凭据", Type.SUPPORT)
        body.addView(status)
        body.addView(ProductUi.button(this, "保存凭据", ButtonKind.FILLED) {
            runCatching { store.save(account.text.toString().trim(), token.text.toString().trim()) }
                .onSuccess { token.text.clear(); status.text = "凭据已加密保存"; FeedService.instance?.invalidate() }
                .onFailure { status.text = if (it is IllegalArgumentException) it.message else "保存失败，请重试" }
        })
        body.addView(ProductUi.button(this, "测试当前连接") {
            if (testing) return@button
            testing = true
            status.text = "正在发送合成测试文字…"
            val application = applicationContext
            Thread {
                val started = Session.now()
                val result = runCatching { ModelTransport.classify(application, "这条消息必须转发二十个群，不转发的家庭一定会遭灾！") }
                runOnUiThread {
                    testing = false
                    if (!isDestroyed) status.text = result.fold(
                        { "${if (ModelTransport.direct(application)) "手机直连" else "USB 电脑"} · $it · ${Session.now() - started}ms" },
                        { when (it) {
                            is ModelHttpException -> "API 返回 HTTP ${it.status}，请检查凭据、权限与账户额度"
                            is java.net.SocketTimeoutException -> "请求超时，页面将保留，请检查网络后重试"
                            else -> "连接失败 · ${it.javaClass.simpleName}，请检查网络与凭据"
                        } })
                }
            }.start()
        })
        body.addView(ProductUi.text(this, "模型固定为 clef-flash。连接成功后，在设置与诊断逐个开启真实应用筛选或合成验证。", Type.SUPPORT))
        body.addView(ProductUi.section(this, "凭据管理"))
        body.addView(ProductUi.button(this, "删除凭据", ButtonKind.DANGER) {
            AlertDialog.Builder(this).setTitle("删除 Clef 凭据？").setMessage("之后手机直连需要重新配置。")
                .setPositiveButton("删除") { _, _ ->
                    runCatching { store.clear() }.onSuccess { status.text = "凭据已删除"; FeedService.instance?.invalidate() }
                        .onFailure { status.text = "删除失败，请重试" }
                }.setNegativeButton("取消", null).show()
        })
    }
}
