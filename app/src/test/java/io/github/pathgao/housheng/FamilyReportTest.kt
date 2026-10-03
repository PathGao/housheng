package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class FamilyReportTest {
    private val now = 1790985600000L
    private fun snapshot(rows: List<NoticeSummary> = emptyList()) = ReportSnapshot(
        now - 6 * 86400000L, now, if (rows.isEmpty()) null else now,
        rows, emptyList()
    )

    @Test fun reportStatesCoverageAndDoesNotTreatNoRecordsAsNoPhoneNotifications() {
        val report = formatFamilyReport(snapshot(), setOf("com.xingin.xhs"), "震惊内幕", "观察中", generatedAt = now)
        assertTrue(report.contains("小红书"))
        assertTrue(report.contains("暂无记录，不代表手机没有收到通知"))
        assertTrue(report.contains("仅覆盖已勾选来源"))
        assertTrue(report.contains("更新"))
        assertTrue(report.contains("未接入模型"))
        assertTrue(report.contains("震惊内幕"))
    }

    @Test fun reportDistinguishesRemovalRequestsFromVerifiedDeletion() {
        val report = formatFamilyReport(snapshot(listOf(NoticeSummary("com.xingin.xhs", 7, 3, 2, 1))),
            setOf("com.xingin.xhs"), "", "观察中", generatedAt = now)
        assertTrue(report.contains("收到 7 次"))
        assertTrue(report.contains("清理请求 2 次"))
        assertTrue(report.contains("受保护 1 次"))
        assertFalse(report.contains("已删除"))
    }

    @Test fun fixtureIsExplicitAndUnknownSourcesCannotAppear() {
        val report = formatFamilyReport(snapshot(listOf(
            NoticeSummary(AppCatalog.FIXTURE, 4, 1, 1, 2),
            NoticeSummary("com.android.chrome", 9, 9, 9, 0)
        )), setOf(AppCatalog.FIXTURE, "com.android.chrome"), "", "观察中", generatedAt = now)
        assertTrue(report.contains("验证场测试数据"))
        assertFalse(report.contains("com.android.chrome"))
    }

    @Test fun longRulesCannotHideAlwaysAllowedSources() {
        val report = formatFamilyReport(snapshot(), setOf("com.xingin.xhs"), "词".repeat(2000), "观察中", generatedAt = now, alwaysAllowedSources = setOf("com.xingin.xhs", "com.android.chrome"))
        assertTrue(report.contains("始终放行：小红书"))
        assertFalse(report.contains("com.android.chrome"))
    }

    @Test fun inventoryIsOptionalAndRecentInstallDoesNotImplyMalice() {
        val unscanned = formatFamilyReport(snapshot(), emptySet(), "", "观察中", generatedAt = now)
        assertTrue(unscanned.contains("尚未读取安装清单"))
        val scanned = formatFamilyReport(snapshot(), emptySet(), "", "观察中", listOf(
            InstalledApp("recent.example", "近期应用", now - 86400000),
            InstalledApp("old.example", "旧应用", now - 40L * 86400000),
            InstalledApp("future.example", "未来日期", now + 86400000)
        ), now)
        assertTrue(scanned.contains("近期应用"))
        assertTrue(scanned.contains("旧应用"))
        assertTrue(scanned.contains("未来日期"))
        assertTrue(scanned.contains("系统首次安装日期 未知"))
        assertTrue(scanned.contains("不代表恶意"))
    }
}
