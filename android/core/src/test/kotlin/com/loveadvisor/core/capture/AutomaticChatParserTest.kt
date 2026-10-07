package com.loveadvisor.core.capture
import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test
class AutomaticChatParserTest {
    @Test fun qqAndDouyinRequestManualVisionInsteadOfWechatNodeHeuristics() {
        for(pkg in listOf(ChatApps.QQ,ChatApps.DOUYIN))assertTrue(AutomaticChatParser().extract(WindowSnapshot(pkg,1,RectI(0,0,1080,2344),emptyList())) is CaptureResult.NeedsImage)
    }
    @Test fun unsupportedAppIsNeverCaptured() {
        assertTrue(AutomaticChatParser().extract(WindowSnapshot("com.browser",1,RectI(0,0,1080,2344),emptyList())) is CaptureResult.NotChat)
    }
}
