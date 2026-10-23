package com.loveadvisor.core.memory

import org.junit.Assert.*
import org.junit.Test

class LearningPriorityTest {
    @Test fun foregroundCancelsLearningAndLeasesMustAllBeReleased() {
        val priority=LearningPriority();val old=priority.start()!!
        val endFirst=priority.enterForeground();val endSecond=priority.enterForeground()
        assertTrue(old.cancel.cancelled);assertFalse(priority.current(old));assertNull(priority.start())
        endFirst();endFirst();assertFalse(priority.canStart())
        endSecond();assertTrue(priority.canStart());assertNotNull(priority.start())
    }
    @Test fun lateCompletionCannotClearANewerJob() {
        val priority=LearningPriority();val old=priority.start()!!
        val release=priority.enterForeground();release()
        val newer=priority.start()!!
        assertFalse(priority.finish(old));assertTrue(priority.current(newer));assertFalse(priority.canStart())
        assertTrue(priority.finish(newer));assertTrue(priority.canStart())
    }
    @Test fun deadlineCancellationMakesTheResultIneligible() {
        val priority=LearningPriority();val ticket=priority.start()!!
        ticket.cancel.cancel()
        assertFalse(priority.current(ticket));assertTrue(priority.finish(ticket));assertTrue(priority.canStart())
    }
    @Test fun modeOffCancelsAndInvalidatesPendingResult() {
        val priority=LearningPriority();val old=priority.start()!!
        priority.cancel();assertTrue(old.cancel.cancelled);assertFalse(priority.current(old))
        val newer=priority.start()!!;assertFalse(priority.finish(old));assertTrue(priority.current(newer))
    }
}
