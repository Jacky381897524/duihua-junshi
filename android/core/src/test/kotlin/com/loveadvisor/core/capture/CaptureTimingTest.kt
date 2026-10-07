package com.loveadvisor.core.capture
import org.junit.Assert.*
import org.junit.Test
class CaptureTimingTest {
    @Test fun quietWindowDoesNotWaitButFastRepeatStillRespectsLimit() {
        assertEquals(0L,CaptureTiming.delay(1000,2000,false))
        assertEquals(300L,CaptureTiming.delay(1000,1100,false))
        assertEquals(400L,CaptureTiming.delay(1000,1000,false))
    }
    @Test fun legacyDetachedOverlaysNeedACompositorDelayEvenWhenQuiet() {
        assertEquals(80L,CaptureTiming.delay(1000,2000,true))
        assertEquals(300L,CaptureTiming.delay(1000,1100,true))
    }
    @Test fun mainThreadDelayRequiresAnotherGapAtActualExecutionTime() {
        assertEquals(399L,CaptureTiming.delay(900,901,false))
        assertEquals(0L,CaptureTiming.delay(900,1300,false))
        val firstActual=900L
        val secondDue=901L
        val secondActual=secondDue+CaptureTiming.delay(firstActual,secondDue,false)
        assertTrue(secondActual-firstActual>=400L)
    }
}
