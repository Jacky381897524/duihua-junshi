package com.loveadvisor.core.overlay

import org.junit.Assert.*
import org.junit.Test

class WindowRegistrationTest {
    // WindowManager owns an added view before its first traversal attaches it.
    private class DeferredWindow {
        var owned = false
        var attached = false
        var additions = 0
        var removals = 0
        fun add() {
            check(!owned) { "View has already been added to the window manager" }
            owned = true
            additions++
        }
        fun remove() {
            check(owned) { "View is not registered" }
            owned = false
            attached = false
            removals++
        }
        fun firstFrame() { attached = owned }
    }

    @Test fun repeatedShowBeforeFirstFrameAddsOneWindow() {
        val window = DeferredWindow()
        val registration = WindowRegistration()
        registration.attach(window::add)
        registration.attach(window::add)
        window.firstFrame()
        assertTrue(window.attached)
        assertEquals(1, window.additions)
    }

    @Test fun hideBeforeFirstFrameRemovesWindowAndAllowsRestore() {
        val window = DeferredWindow()
        val registration = WindowRegistration()
        registration.attach(window::add)
        registration.detach(window::remove)
        assertFalse("Early hide must not leave an invisible registered window", window.owned)
        registration.detach(window::remove)
        registration.attach(window::add)
        window.firstFrame()
        assertTrue(window.attached)
        assertEquals(2, window.additions)
        assertEquals(1, window.removals)
    }

    @Test fun failedAddCanBeRetried() {
        val window = DeferredWindow()
        val registration = WindowRegistration()
        assertThrows(IllegalStateException::class.java) {
            registration.attach { error("Rejected window") }
        }
        registration.attach(window::add)
        window.firstFrame()
        assertTrue(window.attached)
        assertEquals(1, window.additions)
    }

    @Test fun failedRemoveRetainsOwnershipForCleanupRetry() {
        val window = DeferredWindow()
        val registration = WindowRegistration()
        registration.attach(window::add)
        window.firstFrame()
        assertThrows(IllegalStateException::class.java) {
            registration.detach { error("Removal failed") }
        }
        registration.detach(window::remove)
        assertFalse(window.owned)
    }
}
