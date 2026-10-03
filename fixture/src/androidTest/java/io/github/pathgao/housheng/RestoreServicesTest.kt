package io.github.pathgao.housheng

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

// Target the fixture so finishing instrumentation does not terminate the parent's services.
@RunWith(AndroidJUnit4::class)
class RestoreServicesTest {
    @Test fun restoreObservationAfterValidation() {
        val component = "io.github.pathgao.housheng/io.github.pathgao.housheng.NotificationService"
        DeviceUi.shell("cmd notification disallow_listener $component")
        DeviceUi.shell("cmd notification allow_listener $component")
        DevicePreparation().reconnectServicesForInstrumentation()
        DeviceUi.shell("am start -W -n io.github.pathgao.housheng/.MainActivity")
    }
}
