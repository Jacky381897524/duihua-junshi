package com.loveadvisor.android.overlay

import android.app.UiAutomation
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ChatCaptureService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FrostedPanelWindowDeviceTest {
    /** Opt-in: own test surface only, no chat reads, screenshots, model calls, drafts or sends. */
    @Test @SdkSuppress(minSdkVersion=31)
    fun nativeSurfaceKeepsGeometryAndEditorFocusAcrossRepeatedShowAndHide() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeFrostAudit")=="true")
        val i=InstrumentationRegistry.getInstrumentation()
        i.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val service=ChatCaptureService.instance
        assertNotNull("需已连接无障碍服务",service)
        i.runOnMainSync {
            val panel=AdvisorPanelView(service!!,{},{_,_->},{},{},{},{},{true},{true},frosted=true)
            val host=FrostedPanelWindow(service,panel)
            val p=WindowManager.LayoutParams(320,420,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {
                gravity=Gravity.TOP or Gravity.LEFT;x=30;y=120;title="LoveAdvisorFrostedProbe";setFitInsetsTypes(0)
            }
            try {
                repeat(5) { turn ->
                    host.show(p)
                    p.x=30+turn*3;p.height=420+turn
                    host.update(p)
                    val actual=panel.rootView.layoutParams as WindowManager.LayoutParams
                    assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,actual.type)
                    assertTrue(actual.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE!=0)
                    assertEquals(0,actual.flags and (WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND))
                    assertEquals(p.x,actual.x);assertEquals(p.height,actual.height)
                    host.hide()
                }
            } finally { host.release() }
            assertNull("释放后内容不能仍挂在旧窗口上",panel.parent)
        }
    }
}
