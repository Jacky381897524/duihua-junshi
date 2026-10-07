package com.loveadvisor.core.capture
import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class CapturePolicyTest {
    @Test fun backgroundWechatNeverDisplaysOverlay() {
        assertFalse(CapturePolicy.overlayVisible(true,false,false,wechatForeground=false))
        assertTrue(CapturePolicy.overlayVisible(true,false,false,wechatForeground=true))
    }
    @Test fun applicationFrameResolvesEmptyOrChildSizedRootBounds() {
        val frame=RectI(0,80,1080,2280)
        assertEquals(frame,CapturePolicy.viewport(frame,RectI(0,0,0,0)))
        assertEquals(frame,CapturePolicy.viewport(frame,RectI(0,80,1080,280)))
        assertEquals(frame,CapturePolicy.viewport(RectI(0,0,0,0),frame))
    }
    @Test fun browsingOldNativeMessagesWaitsUnlessExplicitlyRefreshed() {
        val result=CaptureResult.Readable(AdvisorFixtures.snapshot())
        assertTrue(CapturePolicy.pauseForHistory(result,browsingHistory=true,forced=false))
        assertFalse(CapturePolicy.pauseForHistory(result,browsingHistory=true,forced=true))
        assertFalse(CapturePolicy.pauseForHistory(result,browsingHistory=false,forced=false))
    }
    @Test fun missingComposerStillWaitsBeforeRequestingVisionForOldMessages() {
        val result=AutomaticWeChatParser().extract(WindowSnapshot("com.tencent.mm",42,RectI(0,0,1000,2000),listOf(
            NodeRecord(null,"小雨","android.widget.TextView",false,RectI(400,80,600,145)),
            NodeRecord(null,"上周说的旧消息","android.widget.TextView",false,RectI(130,400,600,500)))))
        assertTrue(result is CaptureResult.NeedsImage)
        assertTrue(CapturePolicy.pauseForHistory(result,browsingHistory=true,forced=false))
        assertFalse(CapturePolicy.pauseForHistory(result,browsingHistory=true,forced=true))
        assertFalse(CapturePolicy.pauseForHistory(CaptureResult.NotChat,browsingHistory=true,forced=false))
    }
    @Test fun bubbleIsVisibleAsSoonAsEnabledWithoutARecognizedChat() {
        assertTrue(CapturePolicy.overlayVisible(enabled=true,locked=false,projecting=false))
    }
    @Test fun lockDisableAndProjectionTemporarilyHideBubble() {
        assertFalse(CapturePolicy.overlayVisible(enabled=false,locked=false,projecting=false))
        assertFalse(CapturePolicy.overlayVisible(enabled=true,locked=true,projecting=false))
        assertFalse(CapturePolicy.overlayVisible(enabled=true,locked=false,projecting=true))
    }
    @Test fun ownForegroundConsultationIsNotCancelledByWechatObserver() {
        val own=SessionToken("com.loveadvisor.android",-1,"consult",1,1)
        assertFalse(CapturePolicy.invalidateForForeground(own,"com.loveadvisor.android"))
        assertTrue(CapturePolicy.invalidateForForeground(own.copy(packageName="com.tencent.mm"),"com.loveadvisor.android"))
        assertTrue(CapturePolicy.invalidateForForeground(own.copy(packageName="com.tencent.mobileqq"),"com.loveadvisor.android"))
    }
    @Test fun confirmedOcrCanSurviveOwnOverlayButCannotTransferWindowOrIdentity() {
        val snapshot=AdvisorFixtures.snapshot().copy(source=CaptureSource.OCR)
        val window=WindowSnapshot(snapshot.packageName,snapshot.windowId,RectI(0,0,1000,2000),emptyList(),snapshot.identity)
        assertTrue(CapturePolicy.retainOcr(snapshot,window,snapshot.identity))
        assertFalse(CapturePolicy.retainOcr(snapshot,window.copy(windowId=2),snapshot.identity))
        assertFalse(CapturePolicy.retainOcr(snapshot,window,snapshot.identity!!.copy(id="other")))
    }
}
