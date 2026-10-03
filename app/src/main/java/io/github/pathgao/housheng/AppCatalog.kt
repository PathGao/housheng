package io.github.pathgao.housheng

object AppCatalog {
    const val FIXTURE = "io.github.pathgao.housheng.fixture"
    val sources = linkedMapOf(
        FIXTURE to "后生验证场",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.ss.android.ugc.aweme.lite" to "抖音极速版",
        "com.smile.gifmaker" to "快手",
        "com.kuaishou.nebula" to "快手极速版",
        "com.xingin.xhs" to "小红书"
    )
    fun permits(source: String, kind: String): Boolean = when (kind) {
        "notifications" -> source in sources
        // A package allowlist cannot distinguish a feed from private messages in the same app.
        "pages" -> source == FIXTURE
        else -> false
    }
}

data class InstalledApp(val packageName: String, val name: String, val firstInstalledAt: Long)

fun installedInLast30Days(app: InstalledApp, now: Long): Boolean =
    app.firstInstalledAt > 0 && app.firstInstalledAt <= now && now - app.firstInstalledAt < 30L * 86400000
