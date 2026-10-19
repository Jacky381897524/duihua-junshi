package com.loveadvisor.core.analysis
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class MessageJudgmentTest {
    private val s=ChatSnapshot("wechat",1,ConversationIdentity("a","她",ChatKind.DIRECT,true),listOf(ChatMessage("0",Speaker.ME,null,"我记得"),ChatMessage("1",Speaker.OTHER,null,"那你说"),ChatMessage("2",Speaker.ME,null,"后来想起了具体安排")))
    @Test fun selectedSentenceUsesAllReadMessagesWithoutReordering() { assertEquals(listOf("0","1","2"),MessageJudgmentService.context(s,"1").messages.map { it.id }) }
    @Test fun rejectsSelfMissingAndDuplicateIds() {
        for(id in listOf("0","missing"))try { MessageJudgmentService.context(s,id);fail() } catch(_:IllegalArgumentException) {}
        try { MessageJudgmentService.context(s.copy(messages=s.messages+s.messages[1]),"1");fail() } catch(_:IllegalArgumentException) {}
    }
    @Test fun readsOnlyActualProbabilities() {
        val raw="""{"literal_question":{"noul":0.07},"true_intent":{"choice":"confirm_you_care","probabilities":{"confirm_you_care":0.72,"vent_anger":0.2,"casual_chat":0.08}},"best_action":{"choice":"check_history","confidence":0.91},"danger_level":{"score":8}}"""
        val result=JevDecisionCodec.decode(raw,s.copy(messages=s.messages.take(2)),"Jev")
        assertEquals(0.07,result.literal!!,0.0);assertEquals(0.72,result.intents.first().weight!!,0.0)
        assertNull(result.actions.first().weight);assertEquals(9,result.level)
        assertEquals(listOf("0","1"),result.evidence.map { it.id })
    }
    @Test fun invalidOrMixedWeightsStayUnknown() {
        for(value in listOf("-0.1","1.01","NaN","72")) {
            val raw="""{"true_intent":{"choice":"casual_chat","probabilities":{"casual_chat":$value}},"literal_question":{"noul":$value}}"""
            val result=JevDecisionCodec.decode(raw,s.copy(messages=s.messages.take(2)),"Jev")
            assertNull(result.literal);assertNull(result.intents.first().weight)
        }
    }
    @Test fun staleJudgmentCannotReturnAcrossTargetsOrModel() {
        val session=SessionToken("wechat",1,"a",1,1);val gate=JudgmentRequestGate()
        val a=JudgmentKey(session,"1","model-a");val first=gate.begin(a)
        val b=a.copy(judgeRevision="model-b");val second=gate.begin(b)
        assertFalse(gate.accept(a,first));assertTrue(gate.accept(b,second));gate.invalidate();assertFalse(gate.accept(b,second))
        for(changed in listOf(a.copy(targetMessageId="2"),a.copy(session=session.copy(revision=2)),a.copy(session=session.copy(conversationId="b")))) {
            val old=gate.begin(a);gate.begin(changed);assertFalse(gate.accept(a,old))
        }
    }
    @Test fun pageCancellationKeepsSameKeyCacheButChangedSessionsNeverHit() {
        val key=JudgmentKey(SessionToken("wechat",1,"a",1,1),"1","model")
        val cache=JudgmentCache<Int>();val gate=JudgmentRequestGate()
        gate.begin(key);cache.put(key,42);gate.invalidate()
        assertEquals(42,cache.get(key));assertNull(cache.get(key.copy(session=key.session.copy(revision=2))))
        for(i in 0..50)cache.put(key.copy(targetMessageId="$i"),i)
        assertEquals(50,cache.size);assertNull(cache.get(key.copy(targetMessageId="0")))
    }
}
