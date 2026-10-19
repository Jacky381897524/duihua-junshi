package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class RetainedReadContextTest {
    @Test fun backgroundPauseKeepsTheReadTokenSelectionAnalysisAndRefreshHistoryForDirectAndGroup() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP)) {
            val tracker=SessionTracker();val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
            val snapshot=AdvisorFixtures.snapshot().copy(identity=AdvisorFixtures.snapshot().identity!!.copy(kind=kind))
            val target=ReplyTarget(snapshot.messages.last { it.speaker==Speaker.OTHER }.id)
            val first=coordinator.begin(snapshot,target)!!
            assertTrue(coordinator.complete(first.requestTicket,AdvisorFixtures.valid()))
            val visible=coordinator.state.current;val token=tracker.currentToken
            val pending=coordinator.begin(snapshot,target)!!
            coordinator.pauseCapture("已保留已读内容")
            assertEquals(snapshot,tracker.lastSnapshot)
            assertEquals(token,tracker.currentToken)
            assertEquals(visible,coordinator.state.current)
            assertEquals(target,coordinator.targetFor(snapshot))
            assertFalse(coordinator.state.loading)
            assertFalse(coordinator.complete(pending.requestTicket,AdvisorFixtures.valid()))
            val resumed=coordinator.begin(snapshot,coordinator.targetFor(snapshot))!!
            assertFalse(resumed.reanalyze)
            assertEquals(6,resumed.previous.size)
            assertNotNull(resumed.analysis)
        }
    }

    @Test fun aNewManualReadOfAnotherObjectReplacesTheOldContextAndRejectsOldResults() {
        val tracker=SessionTracker();val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
        val first=AdvisorFixtures.snapshot();val request=coordinator.begin(first)!!
        coordinator.pauseCapture("暂离前台")
        val next=first.copy(identity=first.identity!!.copy(id="another-object",displayTitle="另一个对象"))
        coordinator.observe(next)
        assertEquals("another-object",tracker.currentToken!!.conversationId)
        assertNull(coordinator.state.current)
        assertNull(coordinator.targetFor(next))
        assertFalse(coordinator.complete(request.requestTicket,AdvisorFixtures.valid()))
    }
}
