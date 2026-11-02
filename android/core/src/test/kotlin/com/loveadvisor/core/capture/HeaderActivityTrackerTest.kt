package com.loveadvisor.core.capture
import org.junit.Assert.*
import org.junit.Test
class HeaderActivityTrackerTest {
    @Test fun repeatedFocusIsNotNavigationButAnotherWindowOrPageIs() {
        val tracker=HeaderActivityTracker()
        assertFalse(tracker.observe(ChatApps.WECHAT,1,"com.tencent.mm.ui.ChattingUI"))
        repeat(20) { assertFalse(tracker.observe(ChatApps.WECHAT,1,"com.tencent.mm.ui.ChattingUI")) }
        assertFalse(tracker.observe(ChatApps.WECHAT,1,"android.widget.ListView"))
        assertTrue(tracker.observe(ChatApps.WECHAT,1,"com.tencent.mm.ui.LauncherUI"))
        assertTrue(tracker.observe(ChatApps.WECHAT,2,"com.tencent.mm.ui.ChattingUI"))
    }
}
