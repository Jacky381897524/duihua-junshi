package com.loveadvisor.android.integration

import android.app.UiAutomation
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.overlay.OverlayController
import org.junit.Assert.*
import org.junit.Test

class OverlayRegistrationDeviceTest {
    @Test fun freshWindowsCanRepeatShowAndHideBeforeFirstFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (ChatCaptureService.instance == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100)
        }
        val service = ChatCaptureService.instance
        assertNotNull("请先启用无障碍服务并打开微信", service)
        instrumentation.runOnMainSync {
            assertTrue("微信必须在前台且悬浮窗已开启", service!!.overlayAllowed())
            val overlay = OverlayController(service)
            try {
                // All calls share a main-thread turn, so no view traversal can attach the fresh icon.
                repeat(10) { overlay.showBubble() }
                overlay.show()
                overlay.hide()
                overlay.suppress()
                overlay.suppress()
                repeat(10) { overlay.showBubble() }
                overlay.show()
                overlay.suppress()
                overlay.showBubble()
                overlay.hide()
            } finally {
                overlay.destroy()
            }
            assertSame("窗口操作不能令无障碍服务失效", service, ChatCaptureService.instance)
        }
    }
}
