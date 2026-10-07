package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticConversationTest {
    @Test fun olderDisjointViewportIsNeverAppendedAsANewRound() {
        val c=AutomaticConversation(); val base=com.loveadvisor.core.advisor.AdvisorFixtures.snapshot()
        val recent=base.copy(messages=listOf(ChatMessage("c",Speaker.OTHER,null,"最近的具体事情C"),ChatMessage("d",Speaker.ME,null,"最近的回应D")))
        c.observe(recent,1,true)
        val old=base.copy(messages=listOf(ChatMessage("a",Speaker.OTHER,null,"以前的事情A"),ChatMessage("b",Speaker.OTHER,null,"以前的事情B")))
        assertFalse(c.observe(old,2,false)); assertEquals(recent.messages,c.current!!.messages)
    }
    @Test fun unrelatedSameNameViewportHasNoContinuityEvidence() {
        val c=AutomaticConversation(); c.observe(snapshot(Speaker.OTHER to "甲的这件事情有特别细节",Speaker.ME to "甲的具体回复"),1,true)
        assertFalse(c.continues(snapshot(Speaker.OTHER to "乙的另外一件事",Speaker.ME to "乙的回应")))
    }
    private fun snapshot(vararg texts:Pair<Speaker,String>,id:String="person-a")=ChatSnapshot("com.tencent.mm",42,ConversationIdentity(id,"小雨",ChatKind.DIRECT,true),texts.mapIndexed { i,t -> ChatMessage("$i",t.first,null,t.second) })

    @Test fun enteringGeneratesWithoutWaitingForAnotherIncomingMessage() {
        assertTrue(AutomaticConversation().observe(snapshot(Speaker.OTHER to "晚上好",Speaker.ME to "你好"),0,entered=true))
    }
    @Test fun outgoingMessageUpdatesContextWithoutGenerating() {
        val state=AutomaticConversation()
        state.observe(snapshot(Speaker.OTHER to "晚上好"),0,true)
        assertFalse(state.observe(snapshot(Speaker.OTHER to "晚上好",Speaker.ME to "你好"),20,false))
        assertEquals(listOf("晚上好","你好"),state.current!!.messages.map { it.text })
    }
    @Test fun repeatedScreenDoesNotAppendDuplicatesOrGenerate() {
        val state=AutomaticConversation(); val screen=snapshot(Speaker.OTHER to "晚上好")
        state.observe(screen,0,true)
        assertFalse(state.observe(screen,100,false))
        assertEquals(1,state.current!!.messages.size)
    }
    @Test fun overlappingViewportAccumulatesInOrderAndKeepsOnlyFifty() {
        val state=AutomaticConversation()
        for(i in 0..60) {
            val visible=(maxOf(0,i-3)..i).map { Speaker.OTHER to "第${it}条" }.toTypedArray()
            state.observe(snapshot(*visible),i.toLong(),i==0)
        }
        assertEquals(50,state.current!!.messages.size)
        assertEquals("第11条",state.current!!.messages.first().text)
        assertEquals("第60条",state.current!!.messages.last().text)
        assertFalse(state.observe(snapshot(Speaker.OTHER to "第60条"),999999999,false))
        assertEquals(50,state.current!!.messages.size)
    }
    @Test fun sameNamedDifferentPersonNeverInheritsMessages() {
        val state=AutomaticConversation()
        state.observe(snapshot(Speaker.OTHER to "甲的秘密"),0,true)
        state.observe(snapshot(Speaker.OTHER to "乙的新消息",id="person-b"),10,true)
        assertEquals(listOf("乙的新消息"),state.current!!.messages.map { it.text })
    }
}
