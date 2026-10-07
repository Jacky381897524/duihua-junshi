package com.loveadvisor.android.integration

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.overlay.AdvisorPanelView
import com.loveadvisor.android.overlay.ReplyCardView
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.*
import org.junit.Assert.*
import org.junit.Test

class PlainReplyLayoutTest {
    @Test fun actionRowFollowsActualLineHeightAndKeepsEveryButtonInsideItsBounds() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context=instrumentation.targetContext
            fun card(text:String):ReplyCardView {
                val view=ReplyCardView(context,0,ReplyPlan("r",Strategy.ACKNOWLEDGE,text,"","","",ReplyBranches("","","")),{},{},compact=true)
                view.measure(View.MeasureSpec.makeMeasureSpec(ViewStyles.dp(context,300f),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                view.layout(0,0,view.measuredWidth,view.measuredHeight)
                return view
            }
            val single=card("好的 我知道啦");val double=card("好的 我知道啦\n你先忙 我等你")
            assertTrue(double.height>single.height)
            for(view in listOf(single,double)) {
                val body=view.getChildAt(1) as TextView
                val actions=view.getChildAt(2) as LinearLayout
                assertEquals(0,body.minimumHeight)
                assertTrue(actions.top-body.bottom in 0..ViewStyles.dp(context,5f))
                for(index in 0 until actions.childCount) {
                    val button=actions.getChildAt(index) as Button
                    assertTrue(button.top>=0);assertTrue(button.bottom<=actions.height)
                    assertTrue(actions.top+button.bottom<=view.height-view.paddingBottom)
                }
            }
        }
    }
    @Test fun ordinaryPanelHasAnOpaqueBackgroundAndCreatesNoWebView() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val panel=AdvisorPanelView(instrumentation.targetContext,{},{_,_->},{},{},{},{},{true},{true})
            fun hasWeb(view:View):Boolean=view is WebView || view is ViewGroup && (0 until view.childCount).any { hasWeb(view.getChildAt(it)) }
            assertFalse(hasWeb(panel))
            assertEquals(255,(panel.background as GradientDrawable).color!!.defaultColor ushr 24)
        }
    }
}
