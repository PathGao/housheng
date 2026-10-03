package io.github.pathgao.housheng

import android.app.Notification
import android.os.Bundle
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class DeviceValidationTest {
    companion object {
        @JvmStatic @BeforeClass fun connectServices() {
            val component = ComponentName(InstrumentationRegistry.getInstrumentation().targetContext, NotificationService::class.java).flattenToString()
            DeviceUi.shell("cmd notification disallow_listener $component")
            DeviceUi.shell("cmd notification allow_listener $component")
            DevicePreparation().reconnectServicesForInstrumentation()
        }
        @JvmStatic @AfterClass fun cleanFixture() {
            DeviceUi.shell("am start -W -f 0x10008000 -n ${Session.FIXTURE}/.MainActivity --es scene cleanup")
        }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = Preferences(context)
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(message: String, timeout: Long = 5000, condition: () -> Boolean) {
        val deadline = Session.now() + timeout
        while (Session.now() < deadline) {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            Thread.sleep(50)
        }
        val notifications = NotificationService.instance?.activeNotifications?.filter { it.packageName == Session.FIXTURE }?.map { "id=${it.id}, flags=${it.notification.flags}, category=${it.notification.category}" }
        fail("$message\n$notifications\n${Session.journal()}")
    }
    private fun scene(name: String) {
        if (name != "notifications") await("页面服务必须已连接", 10000) { FeedService.instance != null }
        DeviceUi.shell("am start -W -f 0x10008000 -n ${Session.FIXTURE}/.MainActivity --es scene $name")
    }
    @Before fun prepare() {
        onMain { NotificationListenerService.requestRebind(ComponentName(context, NotificationService::class.java)) }
        await("通知服务必须已授权并连接", 10000) { NotificationService.connected }
        onMain {
            Session.stop(); Session.clear()
            for (source in Session.sources.keys) {
                preferences.select("pages", source, source == Session.FIXTURE)
                preferences.select("notifications", source, source == Session.FIXTURE)
            }
            preferences.saveRules("震惊内幕")
        }
    }
    @After fun stop() { onMain { Session.stop(); FeedService.instance?.classifierFactory = { preferences.classifier() } } }

    @Test fun observationDoesNotAdvance() {
        scene("feed")
        await("应读取第1条") { Session.latestPage.contains("普通用户内容 · 第1条") }
        Thread.sleep(700)
        onMain { assertFalse(Session.journal().contains("翻页 · 已执行")) }
    }
    @Test fun executesOneRealAccessibilityScroll() {
        onMain { Session.feedExecution = true }
        scene("feed")
        await("应真实翻到第2条") { Session.latestPage.contains("普通内容 · 第2条") }
        Thread.sleep(700)
        onMain { assertEquals(1, Session.journal().lines().count { it.contains("翻页 · 已执行") }) }
        instrumentation.sendStatus(0, Bundle().apply { putString("scrollTiming", Session.journal().lines().single { it.contains("翻页 · 已执行") }) })
    }
    @Test fun adsAndUnknownPagesStayEvenWhenRuleMatches() {
        onMain { Session.feedExecution = true }
        for (name in listOf("advertisement", "unknown")) {
            scene(name)
            await("应读取保护页面") { Session.latestPage.contains(if (name == "advertisement") "广告测试" else "未知类型") }
            Thread.sleep(700)
            onMain { assertFalse(Session.journal().contains("翻页 · 已执行")) }
        }
    }
    @Test fun notificationsClearOnlyMatchedUnprotectedItem() {
        onMain { Session.notificationExecution = true }
        scene("notifications")
        await("应收到并处理测试通知") { Session.journal().contains("已请求移除") }
        await("应保留普通、消息和常驻通知") {
            NotificationService.instance?.activeNotifications?.filter { it.packageName == Session.FIXTURE && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }?.map { it.id }?.toSet() == setOf(2, 3, 4)
        }
    }
    @Test fun notificationObservationPreservesAllFour() {
        scene("notifications")
        await("观察模式应保留4条") {
            NotificationService.instance?.activeNotifications?.filter { it.packageName == Session.FIXTURE && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }?.map { it.id }?.toSet() == setOf(1, 2, 3, 4)
        }
    }
    @Test fun alwaysAllowedSourcePreservesMatchedNotification() {
        onMain { preferences.setAlwaysAllow(Session.FIXTURE, true); Session.notificationExecution = true }
        try {
            scene("notifications")
            await("始终放行来源应保留4条原始通知") {
                NotificationService.instance?.activeNotifications?.filter { it.packageName == Session.FIXTURE && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }?.map { it.id }?.toSet() == setOf(1, 2, 3, 4)
            }
            await("应通过不读正文的放行路径") { Session.journal().contains("始终放行，未读取正文") }
            onMain { assertFalse(Session.journal().contains("已请求移除")) }
        } finally { onMain { preferences.setAlwaysAllow(Session.FIXTURE, false) } }
    }

    @Test fun explicitScreenshotIsPrivateAndDeletable() {
        val file = File(context.cacheDir, "capture.png")
        file.delete()
        scene("feed")
        await("应读取页面") { Session.latestPage.contains("普通用户内容 · 第1条") }
        onMain { FeedService.instance!!.captureAfterDelay() }
        await("截图应写入私有缓存", 10000) { file.length() > 0 }
        assertTrue(file.delete())
        assertFalse(file.exists())
    }
    @Test fun stoppingCancelsPendingCapture() {
        val file = File(context.cacheDir, "capture.png")
        file.delete()
        scene("feed")
        await("应读取页面") { Session.latestPage.contains("普通用户内容 · 第1条") }
        onMain { FeedService.instance!!.captureAfterDelay(); Session.stop() }
        Thread.sleep(5700)
        assertFalse(file.exists())
    }
    @Test fun unselectedSourceIsNotReadOrOperated() {
        onMain {
            preferences.select("pages", Session.FIXTURE, false)
            preferences.select("notifications", Session.FIXTURE, false)
            Session.feedExecution = true
            Session.notificationExecution = true
        }
        scene("feed")
        Thread.sleep(1000)
        scene("notifications")
        Thread.sleep(1000)
        onMain {
            assertFalse(Session.journal().contains("页面 ·"))
            assertFalse(Session.journal().contains("通知 ·"))
            assertEquals("已清除", Session.latestPage)
        }
    }
    private fun delayedDecision(change: () -> Unit, expected: String) {
        await("页面服务必须已连接", 10000) { FeedService.instance != null }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        onMain {
            Session.feedExecution = true
            FeedService.instance!!.classifierFactory = { Classifier {
                if (first.compareAndSet(true, false)) {
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    Decision.SKIP
                } else Decision.KEEP
            } }
        }
        try {
            scene("feed")
            assertTrue("分类器应启动", entered.await(5, TimeUnit.SECONDS))
            change()
        } finally { release.countDown() }
        await("旧结果应被丢弃") { Session.journal().contains(expected) || Session.journal().contains("翻页 · 已执行") }
        onMain { assertFalse(Session.journal(), Session.journal().contains("翻页 · 已执行")) }
    }
    @Test fun stoppingWhileClassifierRunsRejectsItsResult() {
        delayedDecision({ onMain { Session.stop() } }, "未执行")
    }
    @Test fun changingPageWhileClassifierRunsRejectsItsResult() {
        delayedDecision({
            scene("unknown")
            await("应切换到新页面") { Session.latestPage.contains("未知类型") }
        }, "页面已变化")
    }
    @Test fun deselectingSourceWhileClassifierRunsRejectsItsResult() {
        delayedDecision({ onMain { preferences.select("pages", Session.FIXTURE, false) } }, "当前来源未勾选")
    }
    @Test fun lateClassifierResultIsNotExecuted() {
        delayedDecision({ Thread.sleep(550) }, "未执行")
    }
    @Test fun newestPageIsClassifiedAfterBusyResult() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val newest = CountDownLatch(1)
        val first = AtomicBoolean(true)
        var eventTypes = 0
        onMain {
            FeedService.instance!!.classifierFactory = { Classifier { text ->
                if (first.compareAndSet(true, false)) {
                    entered.countDown()
                    release.await(20, TimeUnit.SECONDS)
                } else if (text.contains("必须转发二十个群")) newest.countDown()
                Decision.KEEP
            } }
        }
        try {
            scene("feed")
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            scene("model-feed")
            await("新页应已观察到") { Session.latestPage.contains("必须转发二十个群") }
            assertEquals("新页此时应仍等待在途请求", 1L, newest.count)
            onMain {
                val service = FeedService.instance!!
                val info = service.serviceInfo
                eventTypes = info.eventTypes
                info.eventTypes = 0
                service.serviceInfo = info
            }
            Thread.sleep(500)
            release.countDown()
            assertTrue("忙时收到的新页面也必须得到分类\n${Session.journal()}", newest.await(3, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            onMain {
                if (eventTypes != 0) FeedService.instance?.let { service ->
                    service.serviceInfo = service.serviceInfo.apply { this.eventTypes = eventTypes }
                }
            }
        }
    }
}
