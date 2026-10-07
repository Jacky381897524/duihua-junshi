package com.loveadvisor.core.advisor
import org.junit.Assert.*
import org.junit.Test
class PracticeSessionTest {
    @Test fun restartCancelsOldRequestAndLateSuccessCannotOverwriteNewScenario() {
        val session=PracticeSession(); session.start(PracticeState("旧情境")); val old=session.begin()!!
        session.start(PracticeState("新情境")); val latest=session.begin()!!
        assertTrue(old.cancel.cancelled)
        assertFalse(session.complete(old,PracticeTurn(old.state,listOf("旧回复"),"旧建议")))
        assertEquals("新情境",session.state?.scenario); assertTrue(session.loading)
        assertTrue(session.complete(latest,PracticeTurn(latest.state,listOf("新回复"),"新建议"))); assertFalse(session.loading)
    }
    @Test fun oldFailureCannotClearNewPendingRequestAndDestroyCancels() {
        val session=PracticeSession(); session.start(PracticeState("旧情境")); val old=session.begin()!!
        session.start(PracticeState("新情境")); val latest=session.begin()!!
        assertFalse(session.fail(old)); assertTrue(session.loading); assertNull(session.begin())
        session.cancel(); assertTrue(latest.cancel.cancelled); assertFalse(session.complete(latest,PracticeTurn(latest.state,listOf("迟到"),"迟到")))
    }
}
