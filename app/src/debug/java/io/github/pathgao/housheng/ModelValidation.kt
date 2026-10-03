package io.github.pathgao.housheng

import android.widget.LinearLayout
import android.widget.Switch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class LoopbackModelClient(private val port: Int = 18765) {
    fun classify(text: String): String {
        require(text.isNotBlank() && text.length <= 4000)
        val requestId = UUID.randomUUID().toString()
        val body = JSONObject().put("request_id", requestId).put("source", Session.FIXTURE)
            .put("text", text).toString().toByteArray(Charsets.UTF_8)
        val connection = URL("http://127.0.0.1:$port/v1/classify").openConnection() as HttpURLConnection
        val started = Session.now()
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 100
            connection.readTimeout = 350
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = ByteArray(4097)
            var size = 0
            connection.inputStream.use { input ->
                while (size < bytes.size) {
                    val remaining = 500 - (Session.now() - started)
                    check(remaining > 0) { "expired result" }
                    connection.readTimeout = minOf(350, remaining.toInt())
                    val count = input.read(bytes, size, bytes.size - size)
                    if (count < 0) break
                    size += count
                }
            }
            check(size <= 4096 && Session.now() - started < 500) { "oversized or expired result" }
            val result = JSONObject(String(bytes, 0, size, Charsets.UTF_8))
            check(result.getString("request_id") == requestId) { "mismatched request" }
            val decision = result.getString("decision")
            check(decision in setOf("keep", "filter", "uncertain")) { "unknown decision" }
            return decision
        } finally { connection.disconnect() }
    }
}

object ModelValidation {
    var enabled = false
        set(value) { field = value; FeedService.instance?.invalidate() }

    fun classifier(source: String, fallback: Classifier): Classifier {
        if (!enabled || source != Session.FIXTURE) return fallback
        return Classifier { text ->
            val started = Session.now()
            try {
                val result = LoopbackModelClient().classify(text)
                Session.record("模型 · $result · ${Session.now() - started}ms")
                if (result == "filter") Decision.SKIP else Decision.KEEP
            } catch (error: Exception) {
                Session.record("模型不可用 · ${error.javaClass.simpleName} · 保留 · ${Session.now() - started}ms")
                Decision.KEEP
            }
        }
    }

    fun addControls(body: LinearLayout) {
        body.addView(Switch(body.context).apply {
            text = "验证场使用本机模型（需 USB 连接）"
            textSize = 18f
            minHeight = (56 * resources.displayMetrics.density).toInt()
            isChecked = enabled
            setOnCheckedChangeListener { _, checked -> enabled = checked }
        })
    }
}
