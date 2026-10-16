package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import com.loveadvisor.core.advisor.ConversationTurn
import org.junit.Assert.*
import org.junit.Test

class MessageSideRegressionTest {
    @Test fun rereadingAnEarlierViewportDoesNotContinueAnUnseenCachedLaterTurn() {
        val id=ConversationIdentity("p","联系人",ChatKind.DIRECT,true)
        val earlier=ChatSnapshot("com.tencent.mm",1,id,listOf(ChatMessage("a",Speaker.OTHER,null,"对方完整的第一句话"),ChatMessage("b",Speaker.ME,null,"我打算接着说的话")))
        val state=AutomaticConversation()
        state.observe(earlier.copy(messages=earlier.messages+ChatMessage("c",Speaker.OTHER,null,"缓存里较晚但现在看不见的话")),1,true)
        state.observe(earlier,2,false)
        assertEquals("我打算接着说的话",state.current!!.messages.last().text)
        assertEquals("CONTINUE_SELF",ConversationTurn.data(state.current!!)["responseMode"])
    }
    @Test fun partialRecentRestoreCannotMoveUnseenFutureRowsAheadOfAnOlderRead() {
        val id=ConversationIdentity("p","联系人",ChatKind.DIRECT,true)
        val old=(0..4).map { ChatMessage("$it",if(it%2==0)Speaker.OTHER else Speaker.ME,null,"历史中足够长且不同的第${it}句话") }
        val state=AutomaticConversation()
        state.restore(ChatSnapshot("com.tencent.mm",1,id,old))
        val visible=listOf(ChatMessage("x",Speaker.OTHER,null,"本次新看到的更早一句话"))+old.take(3)
        state.observe(ChatSnapshot("com.tencent.mm",1,id,visible),2,true)
        assertEquals(visible,state.current!!.messages)
    }
}
