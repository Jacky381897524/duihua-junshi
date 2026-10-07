package com.loveadvisor.core.overlay
import org.junit.Assert.*
import org.junit.Test
class GlassSamplingPolicyTest {
    @Test fun reopeningPanelCannotResumeSamplingDuringManualRead() {
        assertFalse(GlassSamplingPolicy.allowed(true,true,true))
        assertTrue(GlassSamplingPolicy.allowed(true,true,false))
        assertFalse(GlassSamplingPolicy.allowed(false,true,false))
        assertFalse(GlassSamplingPolicy.allowed(true,false,false))
    }
}
