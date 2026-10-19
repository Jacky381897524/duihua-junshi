package com.loveadvisor.core.overlay
import org.junit.Assert.*
import org.junit.Test
class PanelPageStackTest {
    @Test fun nestedSelectionReturnsToFormAndRestoresItsFocusPolicy() {
        val stack=PanelPageStack()
        stack.push("settings",true);stack.push("selector",false)
        assertEquals("selector",stack.currentId);assertFalse(stack.editable)
        assertTrue(stack.pop());assertEquals("settings",stack.currentId);assertTrue(stack.editable)
        assertTrue(stack.pop());assertNull(stack.currentId);assertFalse(stack.editable);assertFalse(stack.pop())
    }
    @Test fun repeatedTapDoesNotPushTheSamePageTwiceAndClearResetsAllState() {
        val stack=PanelPageStack();stack.push("style",true);stack.push("style",true)
        assertEquals(1,stack.depth)
        stack.clear();assertEquals(0,stack.depth);assertNull(stack.currentId)
    }
}
