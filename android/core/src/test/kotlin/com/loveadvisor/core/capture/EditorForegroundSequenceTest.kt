package com.loveadvisor.core.capture
import com.loveadvisor.core.overlay.PanelEditingFocus
import com.loveadvisor.core.overlay.WindowRegistration
import org.junit.Assert.*
import org.junit.Test

class EditorForegroundSequenceTest {
    @Test fun keyboardFocusGapsCannotDetachAndReattachTheEditingPanel() {
        val editing=PanelEditingFocus();val registration=WindowRegistration();var attachments=0;var detachments=0
        editing.pageChanged(true);assertFalse(editing.requested)
        registration.attach { attachments++ };assertTrue(editing.fieldTapped())
        repeat(8) { i ->
            val keep=ForegroundVisibility.canRetain(7,listOf(AppWindow(7,false,false,3),AppWindow(8,false,true,2)),i%2==0,false,editing.requested)
            if(!keep)registration.detach { detachments++ }
            registration.attach { attachments++ }
        }
        assertEquals(0,detachments);assertEquals(1,attachments)
    }
    @Test fun anotherSplitPaneOrSystemFocusRevokesTheEditorLease() {
        val apps=listOf(AppWindow(7,false,true,3),AppWindow(8,true,false,2))
        assertFalse(ForegroundVisibility.canRetain(7,apps,true,false,true))
        assertFalse(ForegroundVisibility.canRetain(7,listOf(AppWindow(7,false,true,3)),true,true,true))
        assertFalse(ForegroundVisibility.canRetain(7,listOf(AppWindow(8,false,false,3)),true,false,true))
    }
    @Test fun returningToRetainedPageRequiresAnotherDeliberateFieldTap() {
        val editing=PanelEditingFocus();editing.pageChanged(true);assertTrue(editing.fieldTapped());editing.release()
        assertFalse(editing.requested);assertTrue(editing.fieldTapped());editing.pageChanged(false)
        assertFalse(editing.requested);assertFalse(editing.fieldTapped())
    }
    @Test fun aStaleVisibleChatCannotKeepTheCapsuleWithoutAnEditorLease() {
        assertFalse(ForegroundVisibility.canRetain(7,listOf(AppWindow(7,false,true,3)),false,false,false))
    }
}
