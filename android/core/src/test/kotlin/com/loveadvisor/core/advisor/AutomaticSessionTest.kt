package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test
class AutomaticSessionTest {
    @Test fun oldBatchRemainsUsableOnlyInsideSameConversationEpoch() {
        val tracker=SessionTracker(); val c=BatchCoordinator(tracker,RequestGate(tracker)); val s=AdvisorFixtures.snapshot()
        val p=c.begin(s)!!; c.complete(p.requestTicket,AdvisorFixtures.valid()); val old=c.state.current!!
        val next=s.copy(messages=s.messages+ChatMessage("m2",Speaker.OTHER,null,"又遇到一件事"))
        c.observe(next,"对方正在说话")
        assertEquals(old,c.state.current); assertTrue(tracker.sameConversation(old.ticket.session)); assertFalse(tracker.isCurrent(old.ticket.session))
        val pending=c.begin(next)!!; c.observe(next.copy(messages=next.messages+ChatMessage("m3",Speaker.ME,null,"稍等")),"等待对方")
        assertFalse(c.complete(pending.requestTicket,AdvisorFixtures.valid()))
        c.observe(next.copy(identity=s.identity!!.copy(id="other")),"新聊天")
        assertNull(c.state.current); assertFalse(tracker.sameConversation(old.ticket.session))
    }
    @Test fun fullInvalidationRemovesCandidatesInsteadOfKeepingCrossContactText() {
        val t=SessionTracker(); val c=BatchCoordinator(t,RequestGate(t)); val p=c.begin(AdvisorFixtures.snapshot())!!
        c.complete(p.requestTicket,AdvisorFixtures.valid()); c.invalidate(); assertNull(c.state.current)
    }
}
