package com.loveadvisor.core.overlay

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class OverlayViewportTest {
    @Test fun landscapeNotchAndSideNavigationBarAreBothExcluded() {
        assertEquals(RectI(96, 0, 2328, 1080), OverlayViewport.available(
            RectI(0, 0, 2400, 1080), InsetsI(right=72), InsetsI(left=96)))
    }

    @Test fun keyboardStillReservesTheComposerAndPortraitCutout() {
        assertEquals(RectI(0, 120, 1080, 1391), OverlayViewport.available(
            RectI(0, 0, 1080, 2344), InsetsI(top=106, bottom=54), InsetsI(top=120), 1580, 2.625f))
    }

    @Test fun overlappingSystemAndCutoutInsetsAreNotAddedTwice() {
        assertEquals(RectI(0, 90, 1080, 2224), OverlayViewport.available(
            RectI(0, 0, 1080, 2344), InsetsI(top=90, bottom=120), InsetsI(top=60)))
    }

    @Test fun translatedWindowBoundsAndKeyboardUseScreenCoordinates() {
        assertEquals(RectI(280, 140, 1370, 728), OverlayViewport.available(
            RectI(200, 100, 1400, 1000), InsetsI(20, 40, 30, 50), InsetsI(left=80), 800, 1f))
    }

    @Test fun crampedWindowKeepsANonEmptyReachableArea() {
        assertEquals(RectI(19, 9, 20, 10), OverlayViewport.available(
            RectI(0, 0, 20, 10), InsetsI(30, 30, 30, 30), InsetsI(), 5, 3f))
    }
}
