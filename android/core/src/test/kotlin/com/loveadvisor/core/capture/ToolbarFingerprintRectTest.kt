package com.loveadvisor.core.capture
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test
class ToolbarFingerprintRectTest {
    @Test fun fullScreenProofStartsBelowTheRecordingTimerAndEndsBeforeChatRows() {
        val rect=ToolbarFingerprintRect.resolve(1080,2344,RectI(0,0,1080,2344),120,3f)!!
        assertEquals(120,rect.top);assertEquals(288,rect.bottom)
    }
    @Test fun appWindowWithoutStatusBarAndSplitScreenUseWindowRelativeCoordinates() {
        val rect=ToolbarFingerprintRect.resolve(1080,1100,RectI(0,120,1080,1220),120,3f)!!
        assertEquals(0,rect.top);assertEquals(168,rect.bottom)
        assertNull(ToolbarFingerprintRect.resolve(0,1100,RectI(0,120,1080,1220),120,3f))
    }
}
