package com.loveadvisor.core.capture

import org.junit.Assert.*
import org.junit.Test

class ForegroundVisibilityTest {
    @Test fun staleActiveChatWithoutFocusDoesNotKeepTheOverlayOnTheDesktop() {
        assertFalse(ForegroundVisibility.canRetain(1,listOf(AppWindow(1,false,true,5)),false,false))
    }
    @Test fun focusedDesktopWinsEvenIfOldChatIsStillActive() {
        val apps=listOf(AppWindow(1,false,true,5),AppWindow(2,true,false,6))
        assertFalse(ForegroundVisibility.canRetain(1,apps,false,false))
    }
    @Test fun focusedSystemWindowHidesOverlayDespiteStaleChatFocus() {
        assertFalse(ForegroundVisibility.canRetain(1,listOf(AppWindow(1,true,true,5)),false,true))
    }
    @Test fun missingRootCanKeepTheSameActuallyFocusedApplication() {
        assertTrue(ForegroundVisibility.canRetain(1,listOf(AppWindow(1,true,false,5)),false,false))
    }
    @Test fun keyboardOrEditablePanelFocusKeepsItsUnderlyingKnownApp() {
        assertTrue(ForegroundVisibility.canRetain(1,listOf(AppWindow(1,false,false,5)),true,false))
    }
    @Test fun keyboardFocusCannotKeepOldChatBehindAnotherActiveApplication() {
        val apps=listOf(AppWindow(1,false,false,5),AppWindow(2,false,true,6))
        assertFalse(ForegroundVisibility.canRetain(1,apps,true,false))
    }
    @Test fun transientFocusWithNoActiveAppUsesTheTopApplicationOnly() {
        val apps=listOf(AppWindow(1,false,false,5),AppWindow(2,false,false,6))
        assertFalse(ForegroundVisibility.canRetain(1,apps,true,false))
        assertTrue(ForegroundVisibility.canRetain(2,apps,true,false))
    }
    @Test fun transientFocusCannotKeepAnAppWhoseWindowHasGone() {
        assertFalse(ForegroundVisibility.canRetain(1,listOf(AppWindow(2,false,false,5)),true,false))
        assertFalse(ForegroundVisibility.canRetain(null,listOf(AppWindow(2,true,false,5)),true,false))
    }
    @Test fun lateRootResultCannotRestoreOverlayToAnUnfocusedOldApp() {
        val apps=listOf(AppWindow(1,false,true,5),AppWindow(2,true,false,6))
        assertFalse(ForegroundVisibility.canRetain(1,apps,true,false))
        assertTrue(ForegroundVisibility.canRetain(2,apps,true,false))
    }
}
