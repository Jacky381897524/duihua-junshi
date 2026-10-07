package com.loveadvisor.core.capture
import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class PreviewBindingPolicyTest {
    private val snapshot=AdvisorFixtures.snapshot()
    private fun window(pkg:String=snapshot.packageName,id:Int=snapshot.windowId)=WindowSnapshot(pkg,id,RectI(0,0,1080,2400),emptyList())
    private fun draft(s:ChatSnapshot)=ConversationDraft(s.packageName,s.windowId,s.identity?.displayTitle,s.identity!!.kind,s.messages,true,s.source)
    @Test fun ocrCannotRebindBySameWindowIdEvenIfTitleAndTextsCoincide() {
        val ocr=snapshot.copy(source=CaptureSource.OCR)
        assertFalse(PreviewBindingPolicy.canBind(ocr,window(),draft(ocr)))
        assertFalse(PreviewBindingPolicy.canBind(ocr,window(),draft(ocr).copy(title="另一个同窗口聊天",messages=ocr.messages.map { it.copy(text="新消息") })))
        val imported=PreviewBindingPolicy.independent(ocr,"com.loveadvisor.android")
        assertEquals("com.loveadvisor.android",imported.packageName); assertEquals(-1,imported.windowId); assertEquals(CaptureSource.IMPORT,imported.source); assertEquals(ocr.messages,imported.messages)
    }
    @Test fun readableNodesRequireFreshPackageWindowTitleAndSpeakerContent() {
        assertTrue(PreviewBindingPolicy.canBind(snapshot,window(),draft(snapshot)))
        assertFalse(PreviewBindingPolicy.canBind(snapshot,window("com.tencent.mobileqq"),draft(snapshot)))
        assertFalse(PreviewBindingPolicy.canBind(snapshot,window(id=snapshot.windowId+1),draft(snapshot)))
        assertFalse(PreviewBindingPolicy.canBind(snapshot,window(),draft(snapshot).copy(messages=snapshot.messages.map { it.copy(speaker=Speaker.UNKNOWN) })))
    }
    @Test fun disablingExternalOverlayCannotCancelIndependentConsultation() {
        val app=SessionToken("com.loveadvisor.android",-1,"consult",1,1)
        assertFalse(CapturePolicy.invalidateForDisabledCapture(app)); assertFalse(CapturePolicy.invalidateForDisabledCapture(null))
        assertTrue(CapturePolicy.invalidateForDisabledCapture(app.copy(packageName="com.tencent.mm",windowId=7)))
    }
}
