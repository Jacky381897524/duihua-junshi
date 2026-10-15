package com.loveadvisor.core.overlay

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class BubbleIdlePolicyTest {
    @Test fun passiveWindowUpdatesDoNotPostponeTheIdleDeadline() {
        val policy=BubbleIdlePolicy()
        policy.resume(1000,15)
        policy.resume(12000,15)
        assertFalse(policy.hideIfDue(15999))
        assertTrue(policy.hideIfDue(16000))
        assertTrue(policy.hidden)
        assertNull(policy.deadlineAt)
        assertFalse(policy.hideIfDue(17000))
    }

    @Test fun userInteractionRestartsTheConfiguredCountdown() {
        val policy=BubbleIdlePolicy()
        policy.resume(0,15)
        policy.interact(14000,30)
        assertFalse(policy.hideIfDue(43999))
        assertTrue(policy.hideIfDue(44000))
    }

    @Test fun expandedPanelDraggingOrDetachedWindowCannotBecomeHidden() {
        val policy=BubbleIdlePolicy()
        policy.resume(0,15)
        policy.pause()
        assertFalse(policy.hideIfDue(60000))
        assertFalse(policy.hidden)
        policy.resume(60000,15)
        assertFalse(policy.hideIfDue(74999))
        assertTrue(policy.hideIfDue(75000))
    }

    @Test fun firstTapOnlyRevealsAndStartsANewIdlePeriod() {
        val policy=BubbleIdlePolicy()
        policy.resume(0,15)
        assertTrue(policy.hideIfDue(15000))
        assertTrue("first tap is consumed by revealing",policy.reveal(20000,15))
        assertFalse(policy.hidden)
        assertFalse("a second tap may open the panel",policy.reveal(21000,15))
        assertFalse(policy.hideIfDue(34999))
        assertTrue(policy.hideIfDue(35000))
    }

    @Test fun returningToForegroundDoesNotRevealAPreviouslyHiddenBubble() {
        val policy=BubbleIdlePolicy()
        policy.resume(0,15)
        policy.hideIfDue(15000)
        policy.pause()
        policy.resume(40000,15)
        assertTrue(policy.hidden)
        assertNull(policy.deadlineAt)
    }

    @Test fun invalidSavedSecondsUseTheDefaultWithoutImmediateHideOrOverflow() {
        for (seconds in listOf(0,-1,Int.MAX_VALUE)) {
            val policy=BubbleIdlePolicy()
            policy.resume(0,seconds)
            assertFalse(policy.hideIfDue(14999))
            assertTrue(policy.hideIfDue(15000))
        }
        val policy=BubbleIdlePolicy()
        policy.resume(0,3600)
        assertFalse(policy.hideIfDue(3599999))
        assertTrue(policy.hideIfDue(3600000))
    }

    @Test fun rightEdgeLeavesExactlyAQuarterVisibleAndRestoresTheFullIcon() {
        val screen=RectI(0,30,390,790)
        val bubble=PanelBounds(342,160,40,40)
        assertEquals(BubbleEdge.RIGHT,BubbleEdgeGeometry.nearest(bubble,screen))
        assertEquals(PanelBounds(380,160,40,40),BubbleEdgeGeometry.place(bubble,screen,BubbleEdge.RIGHT,true))
        assertEquals(PanelBounds(350,160,40,40),BubbleEdgeGeometry.place(bubble,screen,BubbleEdge.RIGHT,false))
    }

    @Test fun leftEdgeUsesScreenOriginAndStaysAboveTheComposer() {
        val screen=RectI(12,30,390,490)
        val bubble=PanelBounds(20,700,40,40)
        assertEquals(BubbleEdge.LEFT,BubbleEdgeGeometry.nearest(bubble,screen))
        assertEquals(PanelBounds(-18,450,40,40),BubbleEdgeGeometry.place(bubble,screen,BubbleEdge.LEFT,true))
        assertEquals(PanelBounds(12,450,40,40),BubbleEdgeGeometry.place(bubble,screen,BubbleEdge.LEFT,false))
    }

    @Test fun tinyAvailableAreaStillLeavesATappableVisiblePixel() {
        val screen=RectI(0,0,2,2)
        assertEquals(PanelBounds(1,0,2,2),BubbleEdgeGeometry.place(PanelBounds(0,100,40,40),screen,BubbleEdge.RIGHT,true))
    }
    @Test fun sideNavigationInsetsDoNotLeaveTheHiddenIconFullyOnScreen() {
        val safe=RectI(48,30,752,490)
        val physical=RectI(0,0,800,520)
        val bubble=PanelBounds(710,160,40,40)
        assertEquals(PanelBounds(790,160,40,40),BubbleEdgeGeometry.place(bubble,safe,BubbleEdge.RIGHT,true,physical))
        assertEquals(PanelBounds(-30,160,40,40),BubbleEdgeGeometry.place(bubble,safe,BubbleEdge.LEFT,true,physical))
        assertEquals(PanelBounds(712,160,40,40),BubbleEdgeGeometry.place(bubble,safe,BubbleEdge.RIGHT,false,physical))
    }
}
