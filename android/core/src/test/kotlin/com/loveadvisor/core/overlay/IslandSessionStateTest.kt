package com.loveadvisor.core.overlay
import org.junit.Assert.*
import org.junit.Test

class IslandSessionStateTest {
    @Test fun onlyTheCurrentSuccessfulNativeClickCanRetireTheFallback() {
        val s=IslandSessionState();s.enter("wechat:7","one",0);s.published=true
        assertFalse(s.confirmed)
        assertFalse(s.confirm("ordinary",true));assertFalse(s.confirm("one",false))
        assertTrue(s.confirm("one",true));assertTrue(s.confirmed)
        s.enter("qq:8","two",100)
        assertFalse(s.confirmed);assertFalse(s.confirm("one",true))
    }
    @Test fun dismissedNotificationAndTimeoutKeepTheFallbackUntilTheSessionChanges() {
        val s=IslandSessionState();s.enter("wechat:7","one",0);s.published=true
        assertFalse(s.expired(59999));assertTrue(s.expired(60000))
        s.fail();assertTrue(s.failed);assertFalse(s.confirm("one",true))
        assertFalse(s.enter("wechat:7","ignored",65000));assertTrue(s.failed)
        assertTrue(s.enter("qq:8","two",65000));assertFalse(s.failed)
    }
    @Test fun stoppingRevokesQueuedClicksAndTemporaryReentryNeedsFreshProof() {
        val s=IslandSessionState();s.enter("wechat:7","one",0);s.published=true;s.confirm("one",true)
        s.stop();assertFalse(s.confirm("one",true));assertFalse(s.published)
        s.enter("wechat:7","two",1000);assertFalse(s.confirmed)
    }
}
