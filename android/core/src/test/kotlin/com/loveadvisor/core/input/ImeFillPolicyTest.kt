package com.loveadvisor.core.input
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test
class ImeFillPolicyTest {
    @Test fun defaultInputConnectionUnknownOffsetCanStillReturnCompleteDraft() {
        assertTrue(ImeFillPolicy.completeDraft("",-1,0,0))
        assertTrue(ImeFillPolicy.completeDraft("原稿",-1,2,2))
        assertFalse(ImeFillPolicy.completeDraft("x".repeat(4096),-1,4096,4096))
        assertFalse(ImeFillPolicy.completeDraft("x".repeat(4096),-1,0,0))
        assertFalse(ImeFillPolicy.completeDraft("原稿",-1,-1,-1))
        assertFalse(ImeFillPolicy.completeDraft("原稿",5,2,2))
    }
    @Test fun onlyWechatMultilineEditorIsAccepted() {
        assertTrue(ImeFillPolicy.editorAllowed("com.tencent.mm",0x24001,0x40000006))
        assertFalse(ImeFillPolicy.editorAllowed("com.example.app",0x24001,6))
        assertFalse(ImeFillPolicy.editorAllowed("com.tencent.mm",1,3))
        assertFalse(ImeFillPolicy.editorAllowed("com.tencent.mm",0x20081,6))
    }
    @Test fun composerMustBeInsideBottomPartOfActualScreen() {
        assertTrue(ImeFillPolicy.composerAllowed(RectI(120,2150,850,2260),RectI(0,0,1080,2344)))
        assertFalse(ImeFillPolicy.composerAllowed(RectI(120,100,850,200),RectI(0,0,1080,2344)))
        assertFalse(ImeFillPolicy.composerAllowed(RectI(120,2150,1100,2260),RectI(0,0,1080,2344)))
    }
    @Test fun incompleteOrChangingDraftMustNeverBeReplaced() {
        assertTrue(ImeFillPolicy.completeDraft("",0,0,0))
        assertTrue(ImeFillPolicy.completeDraft("原稿",0,2,2))
        assertFalse(ImeFillPolicy.completeDraft("片段",5,0,0))
        assertFalse(ImeFillPolicy.completeDraft("x".repeat(8192),0,8192,8192))
        assertFalse(ImeFillPolicy.completeDraft("x".repeat(4096),0,0,0))
    }
    @Test fun supportedChatEditorsCanBeUsedButSearchAndOtherAppsCannot() {
        for(pkg in listOf("com.tencent.mobileqq","com.ss.android.ugc.aweme")) {
            assertTrue(ImeFillPolicy.editorAllowed(pkg,0x24001,6))
            assertFalse(ImeFillPolicy.editorAllowed(pkg,1,3))
        }
    }
}
