package io.github.pathgao.housheng

import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal class ModelHttpException(val status: Int) : java.io.IOException("HTTP $status")

internal fun postModelJson(url: String, body: JSONObject, timeoutMs: Int, token: String? = null, maxBytes: Int = 4096): JSONObject {
    val bytes = body.toString().toByteArray(Charsets.UTF_8)
    val connection = URL(url).openConnection() as HttpURLConnection
    val started = Session.now()
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = minOf(1000, timeoutMs)
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        if (connection.responseCode != 200) throw ModelHttpException(connection.responseCode)
        val result = ByteArray(maxBytes + 1)
        var size = 0
        connection.inputStream.use { input ->
            while (size < result.size) {
                val remaining = timeoutMs - (Session.now() - started)
                check(remaining > 0) { "expired result" }
                connection.readTimeout = remaining.toInt()
                val count = input.read(result, size, result.size - size)
                if (count < 0) break
                size += count
            }
        }
        check(size <= maxBytes && Session.now() - started < timeoutMs) { "oversized or expired result" }
        return JSONObject(String(result, 0, size, Charsets.UTF_8))
    } finally { connection.disconnect() }
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

object ModelValidation {
    var realEnabled = false
        set(value) { field = value; FeedService.instance?.invalidate() }

    var enabled = false
        set(value) { field = value; FeedService.instance?.invalidate() }

    fun maxAgeMs(source: String): Long = if (enabled && source == Session.FIXTURE) 3000 else 500

    fun classify(context: android.content.Context, text: String): String =
        if (ClefCredentials(context).direct) ClefApiClient(context).classify(text) else LoopbackModelClient().classify(text)

    fun classifier(source: String, fallback: Classifier): Classifier {
        if (!enabled || source != Session.FIXTURE) return fallback
        return Classifier { text ->
            val started = Session.now()
            try {
                val result = classify(FeedService.instance ?: error("page service disconnected"), text)
                Session.record("模型 · $result · ${Session.now() - started}ms")
                if (result == "filter") Decision.SKIP else Decision.KEEP
            } catch (error: Exception) {
                Session.record("模型不可用 · ${error.javaClass.simpleName} · 保留 · ${Session.now() - started}ms")
                Decision.KEEP
            }
        }
    }

    fun addControls(body: LinearLayout) {
        body.addView(ProductUi.switchRow(body.context, "小红书真实信息流", "仅发现页卡片标题，发送给 Clef 判断").apply {
            isChecked = realEnabled
            setOnCheckedChangeListener { _, checked ->
                if (!checked) realEnabled = false
                else android.app.AlertDialog.Builder(context).setTitle("启用小红书筛选？")
                    .setMessage("将发现页公开卡片标题发送给 Cloudflare Clef。不会上传图片、作者、评论或私信。开启内容处理后，命中卡片会遮挡，可点按恢复。当前适配小红书 9.49.0，其他版本保留原页。")
                    .setPositiveButton("启用") { _, _ -> realEnabled = true }
                    .setNegativeButton("取消") { _, _ -> isChecked = false }
                    .setOnCancelListener { isChecked = false }.show()
            }
        })
        body.addView(ProductUi.button(body.context, "配置 Clef API / 测试连接") {
            body.context.startActivity(android.content.Intent(body.context, ClefSettingsActivity::class.java))
        })
        body.addView(ProductUi.switchRow(body.context, "验证场使用 Clef 模型").apply {
            isChecked = enabled
            setOnCheckedChangeListener { _, checked -> enabled = checked }
        })
    }
}
