package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class GroupCaptureTest {
    @Test fun memberCountSuffixIsRemovedOnlyWhenExplicitlyConfirmed() {
        assertEquals("朋友群",GroupTitle.matchingName("朋友群(12)",12))
        assertEquals("朋友群(12)",GroupTitle.matchingName("朋友群(12)",null))
        assertEquals("年级(2026)",GroupTitle.matchingName("年级(2026)",12))
    }
    @Test fun suspectedGroupMustReachVisionInsteadOfBeingRejectedOrSilentlyClassifiedAsPrivate() {
        val window=WindowSnapshot(ChatApps.WECHAT,42,RectI(0,0,1080,2344),listOf(NodeRecord("title","朋友群(12)","TextView",false,RectI(350,100,700,180))))
        assertTrue(AutomaticChatParser().extract(window) is CaptureResult.NeedsImage)
    }
}
