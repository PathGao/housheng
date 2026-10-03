package io.github.pathgao.housheng

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {
    companion object {
        var connected = false; private set
        var instance: NotificationService? = null; private set
    }
    override fun onCreate() { super.onCreate(); Session.initialize(this) }
    override fun onListenerConnected() {
        connected = true; instance = this
        Session.stateChanged()
        Session.record("通知监听已连接")
        Session.serviceEvent(this, "notifications", "connected")
    }
    override fun onListenerDisconnected() {
        if (instance === this) { connected = false; instance = null; Session.stateChanged() }
        Session.record("通知监听暂未连接，已保留原设置")
        Session.serviceEvent(this, "notifications", "disconnected")
    }
    override fun onDestroy() {
        if (instance === this) { connected = false; instance = null; Session.stateChanged() }
        Session.serviceEvent(this, "notifications", "destroyed")
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val preferences = Preferences(this)
        if (sbn.packageName !in preferences.selected("notifications")) return
        val start = Session.now()
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val source = sbn.packageName
        val eventId = "${sbn.key}:${sbn.postTime}"
        val at = System.currentTimeMillis()
        fun report(outcome: NoticeOutcome) {
            Session.report(this) { it.recordNotification(source, eventId, outcome, at) }
        }
        val protected = !canDismissNotification(
            sbn.isClearable, sbn.isOngoing,
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0, notification.category
        )
        if (protected) {
            Session.record("通知 · ${Session.sources[sbn.packageName]} · 保护保留，未读取正文")
            report(NoticeOutcome.PROTECTED)
            return
        }
        if (preferences.alwaysAllow(source)) {
            Session.record("通知 · ${Session.sources[source]} · 始终放行，未读取正文")
            report(NoticeOutcome.ALLOWED)
            return
        }
        val text = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .joinToString("\n") { notification.extras.getCharSequence(it)?.take(2000)?.toString().orEmpty() }
        val decision = preferences.classifier().classify(text)
        val action = decision == Decision.SKIP && ServiceHealth(this).notificationStatus().executing &&
            instance === this && Session.now() - start in 0 until 500
        var outcome = if (decision == Decision.SKIP) NoticeOutcome.MATCHED else NoticeOutcome.ALLOWED
        var result = if (decision == Decision.SKIP) "规则命中，观察保留" else "未命中，保留"
        if (action && sbn.packageName in preferences.selected("notifications")) {
            try {
                cancelNotification(sbn.key)
                outcome = NoticeOutcome.REMOVAL_REQUESTED
                result = "已请求移除"
            } catch (_: SecurityException) {
                if (instance === this) { connected = false; instance = null; Session.stateChanged() }
                outcome = NoticeOutcome.FAILED
                result = "权限不可用，已暂停处理并保留设置"
            }
        }
        Session.record("通知 · ${Session.sources[sbn.packageName] ?: sbn.packageName} · $result · ${Session.now() - start}ms")
        report(outcome)
    }
}
