package com.loveadvisor.core.capture

import org.junit.Assert.*
import org.junit.Test

class ManualReadGateTest {
    @Test fun passiveEventsCannotAuthorizeScreenReads() {
        val gate = ManualReadGate()
        repeat(100) { assertNull(gate.active) }
        assertFalse(gate.accepts(ManualReadGate.Ticket(1)))
    }
    @Test fun explicitReadAuthorizesPipelineAndCompletionStopsFurtherReads() {
        val gate = ManualReadGate()
        val ticket = gate.begin()
        assertTrue(gate.accepts(ticket))
        assertTrue(gate.finish(ticket))
        assertFalse(gate.accepts(ticket))
        assertNull(gate.active)
    }
    @Test fun lateVisionCallbackCannotCompleteNewUserRequest() {
        val gate = ManualReadGate()
        val old = gate.begin()
        gate.cancel()
        val current = gate.begin()
        assertFalse(gate.accepts(old))
        assertFalse(gate.finish(old))
        assertTrue(gate.accepts(current))
    }
    @Test fun navigationCancelsPermissionForPendingRead() {
        val gate = ManualReadGate()
        val ticket = gate.begin()
        gate.cancel()
        assertFalse(gate.accepts(ticket))
        assertFalse(gate.finish(ticket))
    }
}
