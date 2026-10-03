package io.github.pathgao.housheng

import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

/** Debug builds may route the synthetic fixture through a computer over ADB reverse port 18765; release builds only call Cloudflare from the phone. */
internal object ModelTransport {
    private fun prefs(context: Context) = context.getSharedPreferences("clef", Context.MODE_PRIVATE)
    fun direct(context: Context) = prefs(context).getBoolean("direct", true)
    fun setDirect(context: Context, value: Boolean) = check(prefs(context).edit().putBoolean("direct", value).commit())

    fun classify(context: Context, text: String): String =
        if (direct(context)) ClefApiClient(context).classify(text) else LoopbackModelClient().classify(text)

    fun addControls(body: LinearLayout) {
        val context = body.context
        body.addView(ProductUi.text(context, "USB 模式由电脑调用 API，手机无需访问 Cloudflare。", Type.SUPPORT))
        val row = ProductUi.switchRow(context, "手机独立调用 API", "关闭后通过 USB 电脑调用").apply {
            isChecked = direct(context)
            setOnCheckedChangeListener { _, checked ->
                runCatching { setDirect(context, checked); FeedService.instance?.invalidate() }
                    .onFailure { Toast.makeText(context, "模式保存失败", Toast.LENGTH_LONG).show() }
            }
        }
        body.addView(ProductUi.group(context).apply { addView(row) })
    }
}

class LoopbackModelClient(private val port: Int = 18765, private val timeoutMs: Int = 2800) {
    fun classify(text: String): String {
        require(text.isNotBlank() && text.length <= 4000)
        val requestId = UUID.randomUUID().toString()
        val body = JSONObject().put("request_id", requestId).put("source", Session.FIXTURE).put("text", text)
        val result = postModelJson("http://127.0.0.1:$port/v1/classify", body, timeoutMs)
        check(result.getString("request_id") == requestId) { "mismatched request" }
        return result.getString("decision").also { check(it in setOf("keep", "filter", "uncertain")) { "unknown decision" } }
    }
}
