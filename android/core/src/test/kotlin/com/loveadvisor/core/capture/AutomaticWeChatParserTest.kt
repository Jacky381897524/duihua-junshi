package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticWeChatParserTest {
    @Test fun customComposerWithoutEditableFlagGoesToAutomaticVision() {
        val customComposer=NodeRecord("com.tencent.mm:id/new_composer",null,"android.view.View",false,RectI(120,1800,800,1900))
        val result=AutomaticWeChatParser().extract(WindowSnapshot("com.tencent.mm",42,screen,listOf(
            title,customComposer,text("你不开心就说出来嘛",RectI(400,320,870,430)),
            text("我只是很平静地过日子",RectI(130,520,760,650)))))
        assertTrue("认不出输入框必须自动转视觉，不能声称已离开单聊",result is CaptureResult.NeedsImage)
    }
    @Test fun missingComposerNodesDoNotStopAVisibleChat() {
        val result=AutomaticWeChatParser().extract(WindowSnapshot("com.tencent.mm",42,screen,listOf(
            title,NodeRecord(null,null,"android.widget.ImageView",false,RectI(20,400,100,480),contentDescription="头像"),
            text("今天怎么样",RectI(130,400,500,500)))))
        assertTrue(result is CaptureResult.NeedsImage)
    }
    @Test fun emptyWechatTreeStillUsesAutomaticVisionForClassification() {
        assertTrue(AutomaticWeChatParser().extract(WindowSnapshot("com.tencent.mm",42,screen,emptyList())) is CaptureResult.NeedsImage)
    }
    @Test fun groupWithoutEditableComposerIsStillExcluded() {
        val w=WindowSnapshot("com.tencent.mm",42,screen,listOf(text("测试群(3)",RectI(350,80,650,145))))
        assertEquals(CaptureResult.NotChat,AutomaticWeChatParser().extract(w))
    }
    @Test fun wechatTabBarIsNotAnUnknownChat() {
        val w=WindowSnapshot("com.tencent.mm",42,screen,listOf(
            text("微信",RectI(50,1800,200,1900)),text("通讯录",RectI(270,1800,430,1900)),
            text("发现",RectI(520,1800,680,1900)),text("我",RectI(770,1800,920,1900))))
        assertEquals(CaptureResult.NotChat,AutomaticWeChatParser().extract(w))
    }
    @Test fun voiceComposerStillAutomaticallyReadsTextBubblesWithoutOldResourceIds() {
        val voice=text("按住 说话",RectI(120,1700,760,1820))
        val w=WindowSnapshot("com.tencent.mm",42,screen,listOf(title,voice,text("今天过得怎么样 😊",RectI(130,400,500,500))))
        assertTrue(AutomaticWeChatParser().extract(w) is CaptureResult.Readable)
    }
    @Test fun firstVisibleBubbleJustBelowHeaderIsIncluded() {
        val result=AutomaticWeChatParser().extract(window(text("导航下的第一条消息",RectI(130,200,600,280)))) as CaptureResult.Readable
        assertEquals("导航下的第一条消息",result.snapshot.messages.single().text)
    }
    private val screen=RectI(0,0,1000,2000)
    private fun text(value:String,bounds:RectI,editable:Boolean=false,role:String?=null)=
        NodeRecord(null,value,if(editable) "android.widget.EditText" else "android.widget.TextView",editable,bounds,roleHint=role)
    private val title=text("小雨",RectI(400,80,600,145))
    private val input=text("还没发送的草稿",RectI(120,1700,800,1800),true)
    private fun window(vararg bodies:NodeRecord)=WindowSnapshot("com.tencent.mm",42,screen,listOf(title,input)+bodies)

    @Test fun readsBothSidesAutomaticallyWithoutUserConfirmation() {
        val result=AutomaticWeChatParser().extract(window(
            text("今天过得怎么样 😊",RectI(130,400,780,600)),
            text("还好，你呢？",RectI(600,700,870,790))))
        assertTrue(result is CaptureResult.Readable)
        val snapshot=(result as CaptureResult.Readable).snapshot
        assertEquals(listOf(Speaker.OTHER,Speaker.ME),snapshot.messages.map { it.speaker })
        assertEquals(listOf("今天过得怎么样 😊","还好，你呢？"),snapshot.messages.map { it.text })
        assertEquals(ChatKind.DIRECT,snapshot.identity!!.kind)
    }

    @Test fun avatarPositionDisambiguatesWideMultilineBubble() {
        val avatar=NodeRecord(null,null,"android.widget.ImageView",false,RectI(920,400,990,470),contentDescription="头像")
        val result=AutomaticWeChatParser().extract(window(avatar,text("宽气泡里的多行文字\n第二行也是我的话",RectI(140,400,870,650)))) as CaptureResult.Readable
        assertEquals(Speaker.ME,result.snapshot.messages.single().speaker)
    }

    @Test fun unreadableDirectionRequestsAutomaticVisionInsteadOfPreview() {
        val result=AutomaticWeChatParser().extract(window(text("无法确定归属",RectI(200,400,800,500))))
        assertTrue(result is CaptureResult.NeedsImage)
    }

    @Test fun timeAndDraftAreNotConversationMessages() {
        val result=AutomaticWeChatParser().extract(window(text("18:59",RectI(430,300,570,350)),text("好的",RectI(130,500,270,560)))) as CaptureResult.Readable
        assertEquals(listOf("好的"),result.snapshot.messages.map { it.text })
    }

    @Test fun groupsAndContactPagesAreExcluded() {
        val parser=AutomaticWeChatParser()
        assertEquals(CaptureResult.NotChat,parser.extract(WindowSnapshot("com.tencent.mm",42,screen,listOf(text("测试群(3)",RectI(350,80,650,145)),input,text("你好",RectI(130,400,500,500))))))
        val profileButton=text("发消息",RectI(150,1300,850,1400)).copy(className="android.widget.Button")
        assertEquals(CaptureResult.NotChat,parser.extract(WindowSnapshot("com.tencent.mm",42,screen,listOf(title,text("微信号",RectI(130,400,500,500)),profileButton))))
    }
    @Test fun groupSenderNamesAreNotReadAsDirectChatMessages() {
        val result=AutomaticWeChatParser().extract(window(text("你好",RectI(130,400,500,500)),text("群成员",RectI(130,360,360,390)).copy(roleHint="sender")))
        assertFalse(result is CaptureResult.Readable)
    }
    @Test fun oldSenderResourceHintCannotRejectAChatBeforeVisionClassifiesIt() {
        val result=AutomaticWeChatParser().extract(window(text("我是很平静地过日子",RectI(130,400,760,540)).copy(roleHint="sender")))
        assertTrue("旧资源提示不能直接断定当前页面是群聊",result is CaptureResult.NeedsImage)
    }

    @Test fun sameNicknameDoesNotCreateSameAutomaticIdentity() {
        val parser=AutomaticWeChatParser()
        val a=(parser.extract(window(text("你好甲",RectI(130,400,500,500)))) as CaptureResult.Readable).snapshot
        val b=(parser.extract(window(text("你好乙",RectI(130,400,500,500)))) as CaptureResult.Readable).snapshot
        assertNotEquals(a.identity!!.id,b.identity!!.id)
        val reread=parser.extract(window(text("你好甲",RectI(130,400,500,500))).copy(confirmedIdentity=a.identity)) as CaptureResult.Readable
        assertEquals(a.identity!!.id,reread.snapshot.identity!!.id)
    }
}
