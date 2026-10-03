package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class AppCatalogTest {
    @Test fun browserAndChatAppsAreDeniedEvenIfSelected() {
        for (source in listOf("com.android.chrome", "com.android.browser", "com.tencent.mtt", "com.UCMobile", "com.mi.globalbrowser", "com.tencent.mm", "com.tencent.mobileqq", "unknown.package")) {
            for (kind in listOf("pages", "notifications")) assertFalse(source, AppCatalog.permits(source, kind))
        }
    }
    @Test fun unadaptedAppsDoNotExposePrivatePagesOrScreenshots() {
        assertFalse(AppCatalog.permits("com.xingin.xhs", "pages"))
        assertFalse(AppCatalog.permits("com.ss.android.ugc.aweme", "pages"))
        assertTrue(AppCatalog.permits("com.xingin.xhs", "notifications"))
        assertTrue(AppCatalog.permits(AppCatalog.FIXTURE, "pages"))
        assertFalse(AppCatalog.permits(AppCatalog.FIXTURE, "unknown"))
    }
    @Test fun recentInstallUsesFirstInstallTimeNotDiscoveryOrUpdates() {
        val now = 90L * 86400000
        fun app(age: Long) = InstalledApp("example", "Example", now - age)
        assertTrue(installedInLast30Days(app(0), now))
        assertTrue(installedInLast30Days(app(30L * 86400000 - 1), now))
        assertFalse(installedInLast30Days(app(30L * 86400000), now))
        assertFalse(installedInLast30Days(app(-1), now))
        assertFalse(installedInLast30Days(InstalledApp("x", "X", 0), now))
    }
}
