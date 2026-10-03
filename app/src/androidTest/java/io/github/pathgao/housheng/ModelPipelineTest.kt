package io.github.pathgao.housheng

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelPipelineTest {
    companion object {
        @JvmStatic @BeforeClass fun connect() { DevicePreparation().reconnectServicesForInstrumentation() }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(message: String, condition: () -> Boolean) {
        val end = Session.now() + 8000
        while (Session.now() < end) {
            var done = false
            onMain { done = condition() }
            if (done) return
            Thread.sleep(40)
        }
        fail("$message\n${Session.journal()}")
    }
    private fun scene(name: String) { DeviceUi.shell("am start -W -f 0x10008000 -n ${Session.FIXTURE}/.MainActivity --es scene $name") }
    @Before fun prepare() {
        await("page service connected") { FeedService.instance != null }
        onMain {
            Session.stop(); Session.clear()
            Preferences(instrumentation.targetContext).select("pages", Session.FIXTURE, true)
            ModelValidation.enabled = true
        }
    }
    @After fun stop() { onMain { Session.stop(); ModelValidation.enabled = false } }

    @Test fun observationReceivesModelResultWithoutScrolling() {
        scene("model-feed")
        await("model result must return") { Session.journal().contains("模型 ·") }
        Thread.sleep(600)
        onMain {
            assertTrue(Session.latestPage.contains("必须转发二十个群"))
            assertFalse(Session.journal().contains("翻页 · 已执行"))
        }
    }
    @Test fun modelFilterActuallyScrollsOnce() {
        onMain { Session.feedExecution = true }
        scene("model-feed")
        await("actual next card must appear") { Session.latestPage.contains("普通内容 · 第2条") }
        Thread.sleep(600)
        onMain {
            assertTrue(Session.journal(), Session.journal().contains("模型 · filter"))
            assertEquals(1, Session.journal().lines().count { it.contains("翻页 · 已执行") })
            instrumentation.sendStatus(0, Bundle().apply { putString("modelScroll", Session.journal()) })
        }
    }
    @Test fun protectedPagesNeverReachModelOrScroll() {
        onMain { Session.feedExecution = true }
        for (name in listOf("advertisement", "unknown")) {
            scene(name)
            await("protected card observed") { Session.latestPage.contains(if (name == "advertisement") "广告测试" else "未知类型") }
            Thread.sleep(600)
            onMain {
                assertFalse(Session.journal().contains("模型 ·"))
                assertFalse(Session.journal().contains("翻页 · 已执行"))
            }
        }
    }
}
