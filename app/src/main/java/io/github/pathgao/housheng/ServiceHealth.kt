package io.github.pathgao.housheng

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

enum class ServiceStatus(
    val title: String, val detail: String,
    val authorized: Boolean, val connected: Boolean, val executing: Boolean
) {
    UNAUTHORIZED("尚未授权", "请在系统设置中允许后生使用此功能。", false, false, false),
    DISCONNECTED("已授权，暂未连接", "系统权限仍在，服务暂未连接。连接恢复前不会自动处理，请查看服务状态。", true, false, false),
    OBSERVING("正在观察", "记录所选来源，保留内容，不自动清理或翻页。", true, true, false),
    EXECUTING("正在处理", "按已保存的设置处理所选来源，重要通知和受保护页面始终保留。", true, true, true);

    companion object {
        fun resolve(authorized: Boolean, connected: Boolean, requested: Boolean): ServiceStatus = when {
            !authorized -> UNAUTHORIZED
            !connected -> DISCONNECTED
            requested -> EXECUTING
            else -> OBSERVING
        }
    }
}

internal class ExecutionChoice(initial: Boolean, private val save: (Boolean) -> Unit) {
    var requested = initial
        set(value) { field = value; save(value) }
}

class ServiceHealth(context: Context) {
    private val context = context.applicationContext
    fun pageStatus(): ServiceStatus {
        val component = ComponentName(context, FeedService::class.java)
        val enabled = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == component }
        return ServiceStatus.resolve(enabled, FeedService.instance != null, Session.feedExecution)
    }

    fun notificationStatus(): ServiceStatus {
        val enabled = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, NotificationService::class.java))
        return ServiceStatus.resolve(enabled, NotificationService.connected, Session.notificationExecution)
    }
}
