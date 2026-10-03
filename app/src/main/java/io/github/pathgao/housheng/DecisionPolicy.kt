package io.github.pathgao.housheng

enum class Decision { KEEP, SKIP }
enum class ContentKind { USER_CONTENT, AD, UNKNOWN }
fun interface Classifier { fun classify(text: String): Decision }

class KeywordClassifier(private val terms: List<String>) : Classifier {
    override fun classify(text: String) = if (terms.any { it.trim().isNotEmpty() && text.contains(it.trim(), ignoreCase = true) }) Decision.SKIP else Decision.KEEP
}

data class PageToken(val source: String, val item: String, val generation: Long, val observedAt: Long, val kind: ContentKind = ContentKind.UNKNOWN)

class ActionGate {
    var current: PageToken? = null
    var enabled = false
    private var consumed: PageToken? = null
    fun claim(token: PageToken, decision: Decision, now: Long): Boolean {
        if (!enabled || token.kind != ContentKind.USER_CONTENT || decision != Decision.SKIP || current != token || consumed == token || now - token.observedAt !in 0 until 500) return false
        consumed = token
        return true
    }
}

fun canDismissNotification(clearable: Boolean, ongoing: Boolean, summary: Boolean, category: String?): Boolean =
    clearable && !ongoing && !summary && category !in setOf("call", "alarm", "msg", "email", "event", "reminder", "transport", "service", "navigation", "sys", "err", "progress")
