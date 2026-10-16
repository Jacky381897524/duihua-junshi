package com.loveadvisor.core.overlay

import org.junit.Assert.*
import org.junit.Test

class FrostedPanelPolicyTest {
    @Test fun supportedWindowUsesLightBlurWithAFixedGpuBudget() {
        val phone = FrostedPanelPolicy.radiusPx(37, true, true, false, 3f)
        assertTrue("已启用的毛玻璃必须有实际模糊", phone > 0)
        assertTrue("高密度屏幕不能无限放大模糊采样半径", FrostedPanelPolicy.radiusPx(37, true, true, false, 8f) in 1..36)
        assertTrue(FrostedPanelPolicy.radiusPx(31, true, true, false, 1f) < phone)
    }
    @Test fun disabledOrUnsupportedBlurDoesNoBlurWork() {
        assertEquals(0, FrostedPanelPolicy.radiusPx(30, true, true, false, 3f))
        assertEquals(0, FrostedPanelPolicy.radiusPx(37, false, true, false, 3f))
        assertEquals(0, FrostedPanelPolicy.radiusPx(37, true, false, false, 3f))
        assertEquals(0, FrostedPanelPolicy.radiusPx(37, true, true, true, 3f))
    }
    @Test fun runtimeDisableAndReenableRestoreTheSameBoundedEffect() {
        val on = FrostedPanelPolicy.radiusPx(37, true, true, false, 3f)
        assertTrue(on > 0)
        assertEquals(0, FrostedPanelPolicy.radiusPx(37, true, false, false, 3f))
        assertEquals(on, FrostedPanelPolicy.radiusPx(37, true, true, false, 3f))
    }
}
