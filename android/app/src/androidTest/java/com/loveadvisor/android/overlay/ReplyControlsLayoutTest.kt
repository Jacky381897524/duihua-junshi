package com.loveadvisor.android.overlay

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.ui.CapsuleScaleControl
import com.loveadvisor.android.ui.ViewStyles
import org.junit.Assert.*
import org.junit.Test

class ReplyControlsLayoutTest {
    private fun descendants(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun pastePageKeepsReachableActionsAndCentersPasteBesideField() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context=instrumentation.targetContext
            val page=PastedReplyView(context,{}, {}, {})
            val host=PanelPageHost(context,{}, {})
            host.push("pasted",page,editable=true)
            assertTrue(host.getChildAt(0) is ScrollView)
            page.measure(View.MeasureSpec.makeMeasureSpec(ViewStyles.dp(context,320f),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            page.layout(0,0,page.measuredWidth,page.measuredHeight)
            val buttons=descendants(page).filterIsInstance<Button>()
            listOf("开始分析","返回候选面板").forEach { text -> assertTrue(buttons.single { it.text==text }.height>=ViewStyles.dp(context,48f)) }
            val paste=buttons.single { it.text=="粘贴" }
            val field=descendants(page).filterIsInstance<EditText>().single()
            assertTrue(kotlin.math.abs(paste.top+paste.height/2-field.top-field.height/2)<=1)
        }
    }
    @Test fun scaleButtonsHaveSmallRoundVisualsWithLargerTouchAreasAndOnePercentSteps() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context=instrumentation.targetContext;var scale=1.2f
            val view=CapsuleScaleControl(context,scale) { value,_ -> scale=value }
            val minus=descendants(view).filterIsInstance<FrameLayout>().single { it.contentDescription=="缩小胶囊1%" }
            val visual=minus.getChildAt(0)
            assertEquals(ViewStyles.dp(context,48f),minus.layoutParams.width)
            assertEquals(ViewStyles.dp(context,32f),visual.layoutParams.width)
            assertEquals(visual.layoutParams.width,visual.layoutParams.height)
            minus.performClick();assertEquals(1.19f,scale,.0001f)
        }
    }
}
