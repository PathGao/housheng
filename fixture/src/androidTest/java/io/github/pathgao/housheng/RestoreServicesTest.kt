package io.github.pathgao.housheng

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

// Target the fixture so finishing instrumentation does not terminate the parent's services.
@RunWith(AndroidJUnit4::class)
class RestoreServicesTest {
    @Test fun restoreObservationAfterValidation() {
        val app = requireNotNull(InstrumentationRegistry.getArguments().getString("housheng")) { "pass -e housheng <application id>" }
        val component = "$app/io.github.pathgao.housheng.NotificationService"
        DeviceUi.shell("cmd notification disallow_listener $component")
        DeviceUi.shell("cmd notification allow_listener $component")
        DevicePreparation(app).reconnectServicesForInstrumentation()
        DeviceUi.shell("am start -W -n $app/io.github.pathgao.housheng.MainActivity")
    }
}
