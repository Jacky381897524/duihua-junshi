package com.loveadvisor.android.capture

import android.app.UiAutomation
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.core.capture.WindowSnapshot
import org.junit.Assert.*
import org.junit.Test

/** Uses window metadata only. No chat reads, screenshots, model requests, fill or send. */
class ForegroundAcceptanceDeviceTest {
    @Test fun rejectedSamePackageWindowCannotBeAcceptedAsCurrentRead() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val service=ChatCaptureService.instance ?: throw AssertionError("请开启服务并停在真实聊天页面")
        val update=ChatCaptureService::class.java.getDeclaredMethod("updateForeground",WindowSnapshot::class.java).apply { isAccessible=true }
        instrumentation.runOnMainSync {
            val current=service.glassWindow ?: throw AssertionError("尚未取得真实聊天前台")
            assertEquals(true,update.invoke(service,current))
            assertEquals(false,update.invoke(service,current.copy(windowId=-1000)))
            assertEquals("拒绝迟到窗口时仍应保留已证实的当前面板",current.windowId,service.glassWindow?.windowId)
            assertEquals(false,update.invoke(service,null))
            assertEquals(current.windowId,service.glassWindow?.windowId)
        }
    }
}
