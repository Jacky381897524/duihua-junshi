package com.loveadvisor.android.integration
import android.view.*
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.overlay.*
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class AutomaticPanelTest {
    @Test fun closeButtonInvokesCloseWithoutRaisedSystemButtonShadow() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync {
            var closed=false
            val panel=AdvisorPanelView(i.targetContext,{},{_,_->},{},{},{},{closed=true},{true},{true})
            fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            val close=all(panel).filterIsInstance<Button>().first { it.text=="×" }
            assertTrue(close.performClick()); assertTrue(closed)
            assertEquals(0f,panel.elevation,0f)
            assertEquals(0f,close.elevation,0f); assertNull(close.stateListAnimator)
            assertTrue(close.minimumHeight<=close.layoutParams.height)
        }
    }
    @Test fun sixCompactCandidatesNeverAskForAnalysisOrIdentityConfirmation() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync {
            var reads=0
            val panel=AdvisorPanelView(i.targetContext,{},{_,_->},{},{reads++},{},{},{true},{true})
            val plans=(0..5).map { ReplyPlan("$it",Strategy.entries[it],"候选$it","","","",ReplyBranches("","",""),.8) }
            val batch=AdvisorBatch(AdvisorAnalysis(emptyList(),emptyList(),emptyList(),"轻松交流",1,"先接住分享"),plans,compact=true)
            panel.render(BatchState(VisibleBatch(RequestTicket(SessionToken("com.tencent.mm",1,"c",1,1),1),batch)))
            fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            assertEquals(6,all(panel).filterIsInstance<ReplyCardView>().size)
            val buttons=all(panel).filterIsInstance<Button>()
            assertEquals("展开和渲染候选不得自动触发读取",0,reads)
            val read=buttons.first { it.text=="读取信息" }; assertTrue(read.performClick()); assertEquals(1,reads)
            assertFalse(buttons.any { it.text in listOf("分析","重新读取","检查识别","确认身份") })
            assertTrue(buttons.filter { it.text=="复制" || it.text=="填入" }.all { it.layoutParams.width>0 && it.layoutParams.width<=80*i.targetContext.resources.displayMetrics.density })
            panel.render(BatchState(error="首次请求超时")); assertEquals(0,all(panel).filterIsInstance<ReplyCardView>().size)
            assertFalse(all(panel).filterIsInstance<Button>().first { it.text.startsWith("刷新换一批") }.isEnabled)
            assertTrue(read.isEnabled)
        }
    }
}
