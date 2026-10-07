package com.loveadvisor.core.capture
import com.loveadvisor.core.api.VisionConversation
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class VisualMediaMergeTest {
    @Test fun readableTextKeepsItsOriginalCharactersAndMediaFitsBetweenRows() {
        val native=ChatSnapshot("com.tencent.mm",3,ConversationIdentity("c","小雨",ChatKind.DIRECT,true),listOf(ChatMessage("n0",Speaker.OTHER,null,"今天很累"),ChatMessage("n1",Speaker.ME,null,"我陪你")))
        val visual=VisionConversation("小雨",listOf(ChatMessage("v0",Speaker.OTHER,null,"今天很累"),ChatMessage("v1",Speaker.OTHER,null,"[表情包] 小猫抱抱"),ChatMessage("v2",Speaker.ME,null,"我陪你")),setOf("v1"))
        val merged=VisualMediaMerge.merge(native,visual)
        assertEquals(listOf("今天很累","[表情包] 小猫抱抱","我陪你"),merged.messages.map { it.text })
        assertEquals(native.identity,merged.identity)
        assertEquals(native.messages.first(),merged.messages.first())
    }
}
