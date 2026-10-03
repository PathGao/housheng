package io.github.pathgao.housheng

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatFamilyReport(
    snapshot: ReportSnapshot,
    selectedSources: Set<String>,
    rules: String,
    serviceStatus: String,
    installedApps: List<InstalledApp>? = null,
    generatedAt: Long = System.currentTimeMillis(),
    alwaysAllowedSources: Set<String> = emptySet()
): String = buildString {
    val zone = ZoneId.systemDefault()
    fun date(at: Long) = Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    fun time(at: Long) = Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    fun safe(value: String, limit: Int = 160) = value.replace(Regex("[\\p{Cntrl}\\p{Cf}]"), " ").take(limit)
    val rows = snapshot.notifications.filter { AppCatalog.permits(it.source, "notifications") }
    val sources = selectedSources.filter { AppCatalog.permits(it, "notifications") }
    appendLine("后生 · 家庭报告")
    appendLine("统计日期：${date(snapshot.startAt)} 至 ${date(snapshot.endAt)}（本机时区，含今天截至生成时）")
    appendLine("生成时间：${time(generatedAt)}")
    appendLine()
    appendLine("通知概况")
    appendLine("仅覆盖已勾选来源、通知服务连接期间收到的回调。通知更新也计数，不是手机全部通知或独立消息数。系统通知汇总不计入。")
    appendLine("重复到达的通知会尽量合并，次数可能与应用中的消息条数不同。")
    snapshot.firstRecordedAt?.let { appendLine("首次有记录：${date(it)}，更早时段没有采集保证。") }
    if (rows.isEmpty()) appendLine("暂无记录，不代表手机没有收到通知。")
    rows.forEach { row ->
        val label = if (row.source == AppCatalog.FIXTURE) "后生验证场（验证场测试数据）" else AppCatalog.sources.getValue(row.source)
        appendLine("$label：收到 ${row.received} 次，命中 ${row.matched} 次，清理请求 ${row.removalRequested} 次，受保护 ${row.`protected`} 次。")
    }
    appendLine("清理请求表示已向系统发出请求，不保证通知消失，也不能撤回已经发出的声音或横幅。")
    appendLine()
    appendLine("当前设置（不代表整个统计期间的历史设置）")
    appendLine("通知来源：${sources.joinToString("、") { AppCatalog.sources.getValue(it) }.ifEmpty { "尚未选择" }}")
    appendLine("工作状态：${safe(serviceStatus, 500)}")
    appendLine("关键词：${safe(rules.trim().lineSequence().take(32).joinToString("、"), 2000).ifEmpty { "未设置" }}")
    appendLine("始终放行：${alwaysAllowedSources.filter { it in sources }.joinToString("、") { AppCatalog.sources.getValue(it) }.ifEmpty { "无" }}")
    appendLine("电话、消息、闹钟等受保护通知不自动清理。孩子可根据频率和来源，帮助父母在本机调整规则或系统通知权限。")
    appendLine()
    appendLine("本机应用清单")
    if (installedApps == null) {
        appendLine("尚未读取安装清单。")
    } else {
        val apps = installedApps.take(500).sortedWith(compareByDescending<InstalledApp> { installedInLast30Days(it, generatedAt) }.thenBy { it.name })
        appendLine("本次查询可见 ${apps.size} 个应用，其中近 30 天首次安装 ${apps.count { installedInLast30Days(it, generatedAt) }} 个。")
        apps.forEach {
            val installed = if (it.firstInstalledAt in 1..generatedAt) date(it.firstInstalledAt) else "未知"
            appendLine("${safe(it.name)}（${safe(it.packageName)}）${if (installedInLast30Days(it, generatedAt)) " · 近30天安装" else ""}，系统首次安装日期 $installed")
        }
        appendLine("只包含本次查询可见、有桌面入口的应用。首次安装时间由系统提供，不是完整安装历史；近期安装不代表恶意，请家人核实用途后决定。")
    }
    appendLine()
    appendLine("服务连接记录（最多显示最近 50 条，不能证明期间一直在线）")
    if (snapshot.serviceEvents.isEmpty()) appendLine("暂无连接记录。")
    val services = mapOf("notifications" to "通知服务", "pages" to "页面服务")
    val events = mapOf("connected" to "连接", "disconnected" to "断开", "interrupted" to "中断", "destroyed" to "结束")
    snapshot.serviceEvents.take(50).forEach { entry ->
        val service = services[entry.service]
        val event = events[entry.event]
        if (service != null && event != null) appendLine("${time(entry.at)} $service$event")
    }
    appendLine()
    append("统计边界：本机保存最近 30 天通知汇总，不保存通知标题、正文或逐条浏览轨迹。不含信息流内容与话题统计。")
}
