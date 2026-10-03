package io.github.pathgao.housheng

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

enum class Tone(val content: Int, val container: Int) {
    OK(R.color.housheng_ok, R.color.housheng_ok_container),
    NEUTRAL(R.color.housheng_neutral, R.color.housheng_neutral_container),
    WARNING(R.color.housheng_warning, R.color.housheng_warning_container),
    ERROR(R.color.housheng_error, R.color.housheng_error_container)
}

enum class ServiceStatus(
    val title: String, val detail: String, val label: String, val tone: Tone, val icon: Int,
    val authorized: Boolean, val connected: Boolean, val executing: Boolean
) {
    UNAUTHORIZED("尚未授权", "请在系统设置中允许后生使用此功能。", "未授权", Tone.WARNING, R.drawable.ic_error_fill1, false, false, false),
    DISCONNECTED("已授权，暂未连接", "系统权限仍在，服务暂未连接。连接恢复前不会自动处理。", "已断开", Tone.ERROR, R.drawable.ic_link_off_fill1, true, false, false),
    OBSERVING("正在观察", "记录所选来源，保留内容，不自动清理或翻页。", "只记录", Tone.NEUTRAL, R.drawable.ic_visibility, true, true, false),
    EXECUTING("正在处理", "按已保存的设置处理所选来源，重要通知和受保护页面始终保留。", "执行中", Tone.OK, R.drawable.ic_check_circle_fill1, true, true, true);

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

class StatusCopy(val label: String, val headline: String, val detail: String, val action: String?)

fun notificationCopy(status: ServiceStatus): StatusCopy = when (status) {
    ServiceStatus.UNAUTHORIZED -> StatusCopy(status.label, "还不能清理通知", "需要在系统设置里允许一次。后生不保存通知内容。", "去系统设置允许")
    ServiceStatus.DISCONNECTED -> StatusCopy(status.label, "通知服务已断开", "授权还在。连上之前不会清理。", "重新连接")
    ServiceStatus.OBSERVING -> StatusCopy(status.label, "只记录，不清理", "打开“按规则清理通知”后开始清理。", null)
    ServiceStatus.EXECUTING -> StatusCopy("清理中", "正在按规则清理通知", "电话、短信、闹钟不会被清理。", null)
}

/** Explains the grant once, then opens system settings; when already granted, asks the system to rebind. */
fun Activity.connectNotifications(onRebind: () -> Unit) {
    if (!ServiceHealth(this).notificationStatus().authorized) {
        AlertDialog.Builder(this).setTitle("允许后生读取通知")
            .setMessage("后生只处理你选的应用，不保存通知标题和正文。可以随时在系统设置里关闭。")
            .setPositiveButton("去系统设置") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    .onFailure { Toast.makeText(this, "无法打开此入口，请在手机设置中查看通知权限", Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("取消", null).show()
    } else {
        NotificationListenerService.requestRebind(ComponentName(this, NotificationService::class.java))
        onRebind()
    }
}
