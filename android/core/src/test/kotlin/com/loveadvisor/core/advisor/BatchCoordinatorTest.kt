package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import com.loveadvisor.core.capture.WindowSnapshot
import org.junit.Assert.*
import org.junit.Test
class BatchCoordinatorTest {
    @Test fun islandReturnsToWaitingAfterLeavingACompletedConversation() {
        show();c.pauseCapture("返回后点击图标展开")
        assertEquals("待读取",com.loveadvisor.core.overlay.NativeIslandEntryPolicy.status(false,c.state,false))
    }
    @Test fun islandShowsActualFailuresButNeutralStatusClearsTheFailure() {
        val pending=c.begin(chat)!!;c.fail(pending.requestTicket,"接口超时")
        assertEquals("读取失败",com.loveadvisor.core.overlay.NativeIslandEntryPolicy.status(false,c.state,false))
        c.status("等待点击读取")
        assertEquals("待读取",com.loveadvisor.core.overlay.NativeIslandEntryPolicy.status(false,c.state,false))
    }
    private val tracker=SessionTracker()
    private val c=BatchCoordinator(tracker,RequestGate(tracker))
    private val chat=AdvisorFixtures.snapshot()
    @Test fun anotherScreenshotOfSameWindowDoesNotStarveCandidateGeneration() {
        val pending=c.begin(chat)!!
        val window=WindowSnapshot(chat.packageName,chat.windowId,RectI(0,0,1080,2400),emptyList())
        assertTrue(c.awaitVision(window,"正在更新读取"))
        assertTrue(c.state.loading)
        assertTrue(c.complete(pending.requestTicket,AdvisorFixtures.valid()))
    }
    @Test fun screenshotAfterNavigationStillCancelsOldCandidateRequest() {
        val pending=c.begin(chat)!!
        val other=WindowSnapshot(chat.packageName,chat.windowId+1,RectI(0,0,1080,2400),emptyList())
        assertFalse(c.awaitVision(other,"正在读取新窗口"))
        assertFalse(c.state.loading)
        assertFalse(c.complete(pending.requestTicket,AdvisorFixtures.valid()))
    }
    private fun show(batch:AdvisorBatch=AdvisorFixtures.valid()):VisibleBatch {
        val request=c.begin(chat)!!; assertTrue(c.complete(request.requestTicket,batch)); return c.state.current!!
    }
    @Test fun leavingWechatKeepsCopyableCandidatesButCancelsWorkAndFill() {
        val old=show(); val pending=c.begin(chat)!!
        c.pauseCapture("已暂停读取")
        assertEquals(old,c.state.current); assertFalse(c.state.loading)
        assertFalse(tracker.sameConversation(old.ticket.session))
        assertFalse(c.complete(pending.requestTicket,AdvisorFixtures.valid()))
        assertEquals("已暂停读取",c.state.error)
    }
    @Test fun returningAfterPauseDoesNotReuseStaleContactCandidates() {
        show(); c.pauseCapture("已暂停读取")
        c.observe(chat.copy(identity=chat.identity!!.copy(id="other")))
        assertNull(c.state.current)
    }
    @Test fun successAtomicallyReplacesSix() {
        val old=show(); val pending=c.begin(chat)!!
        assertEquals(old,c.state.current); assertTrue(c.state.loading)
        val next=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(text=it.text+"换个角度") })
        assertTrue(c.complete(pending.requestTicket,next)); assertEquals(next,c.state.current!!.batch); assertFalse(c.state.loading)
    }
    @Test fun failureKeepsPreviousBatchAndDoubleTapStartsOne() {
        val old=show(); val pending=c.begin(chat)!!; assertNull(c.begin(chat))
        c.fail(pending.requestTicket,"timeout"); assertEquals(old,c.state.current); assertFalse(c.state.loading); assertEquals("timeout",c.state.error)
    }
    @Test fun unchangedMessagesReuseAnalysisAndChangedMessagesReanalyze() {
        show(); val refresh=c.begin(chat)!!; assertFalse(refresh.reanalyze); assertNotNull(refresh.analysis)
        c.fail(refresh.requestTicket,"failure")
        val next=c.begin(chat.copy(messages=chat.messages+ChatMessage("m2",Speaker.OTHER,null,"后来好了")))!!
        assertTrue(next.reanalyze); assertNull(next.analysis)
    }
    @Test fun lateOldResultCannotReplaceNewConversation() {
        val first=c.begin(chat)!!
        val other=chat.copy(identity=chat.identity!!.copy(id="different"))
        val second=c.begin(other)!!; assertFalse(c.complete(first.requestTicket,AdvisorFixtures.valid()))
        assertTrue(c.complete(second.requestTicket,AdvisorFixtures.valid())); assertEquals("different",c.state.current!!.ticket.session.conversationId)
    }
    @Test fun invalidationStopsResultAndMakesVisibleFillStale() {
        val old=show(); val pending=c.begin(chat)!!; c.invalidate()
        assertFalse(tracker.isCurrent(old.ticket.session)); assertFalse(c.complete(pending.requestTicket,AdvisorFixtures.valid())); assertFalse(c.state.loading)
    }
    @Test fun historyIsBoundedAndDoesNotLeakToOtherContact() {
        for(i in 1..5) {
            val request=c.begin(chat)!!
            assertTrue(request.previous.size<=18)
            val batch=AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(text=it.text+"第${i}批") })
            assertTrue(c.complete(request.requestTicket,batch))
        }
        val last=c.begin(chat)!!; assertEquals(18,last.previous.size); c.fail(last.requestTicket,"error")
        val other=c.begin(chat.copy(identity=chat.identity!!.copy(id="other")))!!; assertTrue(other.previous.isEmpty())
    }
    @Test fun invalidOrFullyRepeatedBatchKeepsPrevious() {
        val old=show(); val p=c.begin(chat)!!
        assertFalse(c.complete(p.requestTicket,old.batch)); assertEquals(old,c.state.current); assertFalse(c.state.loading)
    }
}
