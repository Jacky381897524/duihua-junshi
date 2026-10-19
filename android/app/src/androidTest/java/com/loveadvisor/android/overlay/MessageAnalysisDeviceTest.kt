package com.loveadvisor.android.overlay
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class MessageAnalysisDeviceTest {
    private fun all(v:android.view.View):List<android.view.View> = listOf(v)+if(v is android.view.ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun originalAndInlineJudgmentKeepAnIndependentReplyActionWithoutShowingHistory() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            val context=test.targetContext
            val message=com.loveadvisor.core.model.ChatMessage("target",com.loveadvisor.core.model.Speaker.OTHER,"甲","这是一条会换行的原话，按钮需要独立留出间距")
            var replies=0
            val card=MessageJudgmentView(context,message,0,true,{replies++},{})
            card.show(com.loveadvisor.core.analysis.MessageJudgment("target",.7,emptyList(),emptyList(),emptyList(),1,listOf(message.copy(text="不应在页面展示的参考原文")),"test"))
            val width=(280*context.resources.displayMetrics.density).toInt()
            card.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED))
            card.layout(0,0,width,card.measuredHeight)
            val views=all(card);val action=views.filterIsInstance<android.widget.Button>().first { it.text=="指定回复" }
            val original=views.filterIsInstance<android.widget.TextView>().first { it.text==message.text }
            val rect=android.graphics.Rect();original.getDrawingRect(rect);(action.parent as android.view.ViewGroup).offsetDescendantRectToMyCoords(original,rect)
            assertTrue(rect.right<action.left);assertTrue(action.width<width*.4)
            assertFalse(views.filterIsInstance<android.widget.TextView>().any { it.text.contains("不应在页面展示") || it.text=="参考原文" })
            action.performClick();assertEquals(1,replies)
        }
    }
    @Test fun listContainsOnlyOtherMessagesAndReplyDoesNotNeedASecondPage() {
        val test=InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            val context=test.targetContext
            val snapshot=com.loveadvisor.core.model.ChatSnapshot("com.tencent.mm",1,com.loveadvisor.core.model.ConversationIdentity("c","群聊",com.loveadvisor.core.model.ChatKind.GROUP,true),listOf(
                com.loveadvisor.core.model.ChatMessage("a",com.loveadvisor.core.model.Speaker.OTHER,"甲","然后呢"),
                com.loveadvisor.core.model.ChatMessage("me",com.loveadvisor.core.model.Speaker.ME,null,"我在听"),
                com.loveadvisor.core.model.ChatMessage("b",com.loveadvisor.core.model.Speaker.OTHER,"乙","明天见")))
            var selected="";var requests=0
            val list=MessageAnalysisListView(context,snapshot,{selected=it.id},{},onAnalyze={_,_->requests++;{} })
            assertEquals(0,requests)
            val actions=all(list).filterIsInstance<android.widget.Button>().filter { it.text=="指定回复" }
            assertEquals(2,actions.size);actions.first().performClick();assertEquals("a",selected);actions.last().performClick();assertEquals("b",selected)
            list.close()
        }
    }
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
