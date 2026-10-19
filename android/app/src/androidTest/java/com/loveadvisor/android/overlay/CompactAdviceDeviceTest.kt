package com.loveadvisor.android.overlay

import android.view.*
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CompactAdviceDeviceTest {
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun oneAdviceOpensAtATimeBranchesSwitchLocallyAndReopeningUsesCachedResult() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            var fetches=0
            val panel=AdvisorPanelView(test.targetContext,{},{_,_->},{},{},{},{},{true},{true},onDetails={_,_,done->
                fetches++;done(ReplyDetail("先接住分享","随意聊天","不连问","顺着补充的信息聊","给对方一点空间","尊重拒绝不追问"),null)
            })
            val plans=(0..5).map { ReplyPlan("$it",Strategy.entries[it],"候选$it","","","",ReplyBranches("","",""),.8) }
            val visible=VisibleBatch(RequestTicket(SessionToken("com.tencent.mm",1,"c",1,1),1),AdvisorBatch(AdvisorAnalysis(emptyList(),emptyList(),emptyList(),"交流",1,"接住分享"),plans,compact=true))
            panel.render(BatchState(visible));val width=(320*test.targetContext.resources.displayMetrics.density).toInt()
            val initial=panel.preferredHeight(width)
            val cards=all(panel).filterIsInstance<ReplyCardView>()
            fun advice(card:View)=all(card).filterIsInstance<Button>().first { it.text=="建议" }
            advice(cards[0]).performClick();assertEquals(1,fetches);assertEquals(initial,panel.preferredHeight(width));assertTrue(cards[0].expandedAdviceHeight>0)
            val reject=all(cards[0]).filterIsInstance<Button>().first { it.text=="拒绝" };reject.performClick()
            assertTrue(reject.isSelected);assertEquals(1,fetches)
            assertTrue(all(cards[0]).filterIsInstance<TextView>().any { it.text=="尊重拒绝不追问" })
            assertFalse(all(cards[0]).filterIsInstance<TextView>().any { it.text=="顺着补充的信息聊" })
            advice(cards[1]).performClick();assertEquals(2,fetches);assertEquals(0,cards[0].expandedAdviceHeight)
            advice(cards[0]).performClick();assertEquals(2,fetches);assertEquals(0,cards[1].expandedAdviceHeight);assertEquals(initial,panel.preferredHeight(width))
            panel.release()
        }
    }
}
