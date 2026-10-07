package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class WeChatNodeParserTest {
    private fun node(text:String,r:RectI,id:String?=null,edit:Boolean=false,role:String?=null)=NodeRecord(id,text,if(edit) "android.widget.EditText" else "android.widget.TextView",edit,r,roleHint=role)
    private val screen=RectI(0,0,1000,2000)
    private val input=node("草稿",RectI(120,1700,760,1820),edit=true)
    private fun window(nodes:List<NodeRecord>)=WindowSnapshot("com.tencent.mm",1,screen,nodes)
    @Test fun changedIdsAndLongBubblesUseEdgesAndExcludeDraft() {
        val r=WeChatNodeParser().extract(window(listOf(node("小雨",RectI(400,90,600,145)),input,node("长左侧消息",RectI(130,400,790,650),"new:id"),node("长右侧消息",RectI(210,700,870,950))))) as CaptureResult.NeedsPreview
        assertEquals(listOf(Speaker.OTHER,Speaker.ME),r.draft.messages.map { it.speaker }); assertFalse(r.draft.messages.any { it.text=="草稿" })
    }
    @Test fun searchAndProfilePagesAreNotChat() {
        assertEquals(CaptureResult.NotChat,WeChatNodeParser().extract(window(listOf(node("搜索",RectI(100,100,900,200),edit=true)))))
        assertEquals(CaptureResult.NotChat,WeChatNodeParser().extract(window(listOf(node("微信号",RectI(200,500,800,560))))))
    }
    @Test fun oldBubbleWithoutTextAllowsImageAndUnknownTitleNeedsConfirmation() {
        assertTrue(WeChatNodeParser().extract(window(listOf(node("",RectI(130,300,800,600),"com.tencent.mm:id/bkl")))) is CaptureResult.NeedsImage)
        val result=WeChatNodeParser().extract(window(listOf(input,node("你好",RectI(130,500,400,560))))) as CaptureResult.NeedsPreview
        assertNull(result.draft.title)
    }
    @Test fun groupRetainsSenderAndAnnouncementIsExcluded() {
        val result=WeChatNodeParser().extract(window(listOf(input,node("小组(3)",RectI(300,90,700,150)),node("群公告：活动安排",RectI(100,220,900,280),role="announcement"),node("成员甲",RectI(130,430,300,460),role="sender"),node("晚上见",RectI(130,480,500,550))))) as CaptureResult.NeedsPreview
        assertEquals(ChatKind.GROUP,result.draft.kind); assertEquals("成员甲",result.draft.messages.single().sender)
    }
    @Test fun sameNicknameDoesNotCreateConfirmedIdentity() {
        val r=WeChatNodeParser().extract(window(listOf(input,node("同名",RectI(400,90,600,145)),node("你好",RectI(130,400,500,500)))))
        assertTrue(r is CaptureResult.NeedsPreview)
    }
}
