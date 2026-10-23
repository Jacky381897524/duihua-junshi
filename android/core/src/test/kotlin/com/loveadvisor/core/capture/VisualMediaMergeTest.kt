package com.loveadvisor.core.capture
import com.loveadvisor.core.api.VisionConversation
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class VisualMediaMergeTest {
    @Test fun nativeTextCanReceiveVisibleTimeFromVisionWithoutBeingRewritten() {
        val native=ChatSnapshot("com.tencent.mm",3,ConversationIdentity("c","小雨",ChatKind.DIRECT,true),listOf(ChatMessage("n",Speaker.OTHER,null,"原始文字")))
        val time=MessageTime("前天 18:00",123000L)
        val visual=VisionConversation("小雨",listOf(ChatMessage("v",Speaker.OTHER,null,"原始文字",time=time)),readAtMillis=456000)
        val merged=VisualMediaMerge.merge(native,visual)
        assertEquals("n",merged.messages.single().id)
        assertEquals("原始文字",merged.messages.single().text)
        assertEquals(time,merged.messages.single().time)
        assertEquals(456000L,merged.readAtMillis)
    }
    @Test fun readableTextKeepsItsOriginalCharactersAndMediaFitsBetweenRows() {
        val native=ChatSnapshot("com.tencent.mm",3,ConversationIdentity("c","小雨",ChatKind.DIRECT,true),listOf(ChatMessage("n0",Speaker.OTHER,null,"今天很累"),ChatMessage("n1",Speaker.ME,null,"我陪你")))
        val visual=VisionConversation("小雨",listOf(ChatMessage("v0",Speaker.OTHER,null,"今天很累"),ChatMessage("v1",Speaker.OTHER,null,"[表情包] 小猫抱抱"),ChatMessage("v2",Speaker.ME,null,"我陪你")),setOf("v1"))
        val merged=VisualMediaMerge.merge(native,visual)
        assertEquals(listOf("今天很累","[表情包] 小猫抱抱","我陪你"),merged.messages.map { it.text })
        assertEquals(native.identity,merged.identity)
        assertEquals(native.messages.first(),merged.messages.first())
    }
}
