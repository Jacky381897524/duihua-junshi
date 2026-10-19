package com.loveadvisor.android.overlay
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class MessageAnalysisDeviceTest {
    @Test fun unreadAnalysisStillProvidesFeedbackWithoutSendingRequest() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();var feedback=0
        instrumentation.runOnMainSync {
            val panel=AdvisorPanelView(instrumentation.targetContext,{},{_,_->},{},{},{},{},{true},{true},onAnalyze={feedback++})
            panel.setAnalysisAvailable(false)
            fun find(v:android.view.View):android.view.View? {
                if(v.contentDescription=="判断分析")return v
                return (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
            }
            val button=find(panel)!!;assertTrue(button.isClickable);button.performClick();assertEquals(1,feedback)
        }
    }
}
