package com.loveadvisor.core.input

import org.junit.Assert.*
import org.junit.Test

class FillStageTraceTest {
    @Test fun callbackFromOldAttemptCannotWriteIntoNewAttempt() {
        var now=0L
        val trace=FillStageTrace { now }
        trace.begin()
        val old=trace.recorder()
        old("focus-pending")
        now=500
        trace.begin()
        val current=trace.recorder()
        now=550
        current("ime-editor-absent")
        old("title-mismatch")
        assertEquals("50ms ime-editor-absent",trace.description())
    }
    @Test fun longOperationRetainsOnlyTheLatestTwelveStages() {
        val trace=FillStageTrace { 100L }
        trace.begin()
        val record=trace.recorder()
        repeat(20) { record("stage-$it") }
        val stages=trace.description().split(" → ")
        assertEquals(12,stages.size)
        assertEquals("0ms stage-8",stages.first())
        assertEquals("0ms stage-19",stages.last())
    }
}
