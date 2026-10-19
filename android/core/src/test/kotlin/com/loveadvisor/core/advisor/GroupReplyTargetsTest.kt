package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
import com.loveadvisor.core.capture.AutomaticConversation
import org.junit.Assert.*
import org.junit.Test
class GroupReplyTargetsTest {
    private fun snapshot()=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("g","群",ChatKind.GROUP,true),listOf(ChatMessage("a",Speaker.OTHER,"甲","晚上吃饭"),ChatMessage("b",Speaker.OTHER,"乙","几点开会"),ChatMessage("c",Speaker.ME,null,"六点吧")))
    @Test fun groupAcceptsSameStrategyAndRequiresReadTarget() {
        val input=AdvisorInput(com.loveadvisor.core.api.ProviderPresets.OPENROUTER.reply(),snapshot(),replyTarget=ReplyTarget("a"))
        val plans=(1..6).map { AdvisorFixtures.valid().replies.first().copy(id="$it",strategy=Strategy.ACKNOWLEDGE,text="我接着说第${it}句",targetMessageId="a",topic="吃饭") }
        val batch=AdvisorFixtures.valid().copy(replies=plans,compact=true,kind=ChatKind.GROUP)
        assertTrue(AdvisorValidator().validate(batch).valid)
        assertTrue(GroupReplyTargets.valid(input,plans))
        assertFalse(GroupReplyTargets.valid(input,plans.map { it.copy(targetMessageId="b") }))
        assertFalse(GroupReplyTargets.valid(input.copy(replyTarget=ReplyTarget("c")),plans))
        assertFalse(GroupReplyTargets.valid(input,plans.map { it.copy(targetMessageId="missing") }))
    }
    @Test fun switchingTargetRejectsEarlierRequest() {
        val tracker=com.loveadvisor.core.session.SessionTracker();val coordinator=BatchCoordinator(tracker,com.loveadvisor.core.session.RequestGate(tracker))
        val first=coordinator.begin(snapshot(),ReplyTarget("a"))!!
        val second=coordinator.begin(snapshot(),ReplyTarget("b"))!!
        assertFalse(coordinator.complete(first.requestTicket,AdvisorFixtures.valid()))
        assertNotEquals(first.requestTicket,second.requestTicket)
    }
    @Test fun mergedViewportsNeverRepeatMessageIds() {
        val s=snapshot();val conv=AutomaticConversation();conv.observe(s,1,true)
        conv.observe(s.copy(messages=listOf(s.messages.last(),ChatMessage("a",Speaker.OTHER,"丙","新的话题来了"))),2,false)
        val rows=conv.current!!.messages
        assertEquals(rows.size,rows.map { it.id }.distinct().size)
    }
}
