package io.github.pathgao.housheng

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ClefCredentials(context: Context) {
    private val prefs = context.getSharedPreferences("clef", Context.MODE_PRIVATE)
    var direct: Boolean
        get() = prefs.getBoolean("direct", true)
        set(value) { check(prefs.edit().putBoolean("direct", value).commit()) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey("housheng-clef", null)
        if (existing != null) return existing as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("housheng-clef", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun save(account: String, token: String) {
        require(account.matches(Regex("[a-fA-F0-9]{32}"))) { "Account ID 应为32位十六进制字符" }
        require(token.isNotBlank() && token.length <= 1024 && token.all { it.code in 33..126 }) { "API Token 格式无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val raw = JSONObject().put("account", account).put("token", token).toString().toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(raw), Base64.NO_WRAP)
        check(prefs.edit().putString("credentials", encoded).commit()) { "凭据保存失败" }
    }

    fun load(): Pair<String, String>? {
        val encoded = prefs.getString("credentials", null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        val value = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        return value.getString("account") to value.getString("token")
    }

    fun clear() { check(prefs.edit().remove("credentials").commit()) }
}

class ClefApiClient(private val context: Context) {
    fun classify(text: String): String {
        require(text.isNotBlank() && text.length <= 4000)
        val (account, token) = ClefCredentials(context).load() ?: error("请先配置 Clef API")
        val questions = context.assets.open("clef-questions.json").bufferedReader().use { JSONObject(it.readText()) }
        val body = JSONObject().put("model", "clef-flash").put("state", text).put("questions", questions)
        val response = postModelJson("https://api.cloudflare.com/client/v4/accounts/$account/ai/run/@cf/cloudflare/clef-flash", body, 2800, token, 65536)
        check(response.get("success") == true) { "Clef API 返回失败" }
        val result = response.getJSONObject("result")
        check(result.getString("model") == "clef-flash") { "模型不匹配" }
        check(result.optJSONObject("usage")?.optBoolean("truncated") != true) { "输入被截断" }
        val answer = result.getJSONObject("answers").getJSONObject("decision")
        val choice = answer.getString("choice")
        val probabilities = answer.getJSONObject("probabilities")
        val labels = setOf("keep", "filter", "uncertain")
        check(choice in labels && probabilities.keys().asSequence().toSet() == labels) { "决策格式无效" }
        val values = labels.associateWith { label ->
            val raw = probabilities.get(label)
            check(raw is Number) { "概率格式无效" }
            raw.toDouble().also { check(it.isFinite() && it in 0.0..1.0) { "概率无效" } }
        }
        check(kotlin.math.abs(values.values.sum() - 1) <= .002 && values[choice] == values.values.max()) { "概率与决策不一致" }
        return choice
    }
}
