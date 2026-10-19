package com.loveadvisor.core.capture
import org.junit.Assert.*
import org.junit.Test

class CaptureSchedulingTest {
    @Test fun ownActivityEventsAreCheckedAgainstTheActualForegroundWindow() {
        assertTrue(CaptureEventPolicy.shouldObserve("com.loveadvisor.android",true,"com.loveadvisor.android"))
        assertFalse(CaptureEventPolicy.shouldObserve("com.loveadvisor.android",false,"com.loveadvisor.android"))
    }
    @Test fun overlayAndKeyboardWindowEventsNeverLookLikeActivityNavigation() {
        assertFalse(CaptureEventPolicy.shouldObserve("com.loveadvisor.android",true,"com.loveadvisor.android",transientWindow=true))
        assertFalse(CaptureEventPolicy.shouldObserve("com.example.securekeyboard",true,"com.loveadvisor.android",transientWindow=true))
    }
    @Test fun launcherAndOtherActivityImmediatelyInvalidateOldWechatRead() {
        assertTrue(CaptureEventPolicy.foregroundAppChanged("com.bbk.launcher2","com.bbk.launcher2.Launcher",emptySet()))
        assertTrue(CaptureEventPolicy.foregroundAppChanged("com.example.other","com.example.other.ChatActivity",emptySet()))
    }
    @Test fun qqAndDouyinKeepSupportedChatOverlayForeground() {
        assertFalse(CaptureEventPolicy.foregroundAppChanged("com.tencent.mobileqq","com.tencent.mobileqq.activity.SplashActivity",emptySet()))
        assertFalse(CaptureEventPolicy.foregroundAppChanged("com.ss.android.ugc.aweme","com.ss.android.ugc.aweme.main.MainActivity",emptySet()))
    }
    @Test fun focusingCustomWechatEditorDoesNotInvalidateConversation() {
        assertFalse(CaptureEventPolicy.navigationChanged("com.tencent.mm","com.tencent.mm.ui.widget.cedit.edit.CustomEditText"))
        assertTrue(CaptureEventPolicy.navigationChanged("com.tencent.mm","com.tencent.mm.ui.LauncherUI"))
        assertTrue(CaptureEventPolicy.navigationChanged("com.tencent.mobileqq","com.tencent.mobileqq.activity.SplashActivity"))
    }
    @Test fun imeSystemUiAndWechatWidgetEventsAreNotForegroundAppChanges() {
        assertFalse(CaptureEventPolicy.foregroundAppChanged("com.example.ime","com.example.ime.InputView",setOf("com.example.ime")))
        assertFalse(CaptureEventPolicy.foregroundAppChanged("com.android.systemui","com.android.systemui.NotificationShade",emptySet()))
        assertFalse(CaptureEventPolicy.foregroundAppChanged("com.tencent.mm","com.tencent.mm.ui.LauncherUI",emptySet()))
        assertFalse(CaptureEventPolicy.foregroundAppChanged("com.example.other","android.widget.Toast",emptySet()))
    }
    @Test fun touchingOverlayCanKeepUnderlyingVisibleApplicationWithoutFocus() {
        val windows=listOf(AppWindow(1,false,false,4))
        assertEquals(1,ForegroundWindowSelector.select(windows,overlayActive=true)?.id)
    }
    @Test fun focusedEditorOrKeyboardRetainsTheTopVisibleAppWithoutAnActiveFlag() {
        val windows=listOf(AppWindow(1,false,false,4))
        assertEquals(1,ForegroundWindowSelector.select(windows,overlayFocused=true)?.id)
        assertNull(ForegroundWindowSelector.select(windows))
        val switched=windows+AppWindow(2,true,false,5)
        assertEquals(2,ForegroundWindowSelector.select(switched,overlayFocused=true)?.id)
    }
    @Test fun oemKeyboardNotListedAsAnInstalledImeIsStillNotAnAppSwitch() {
        val pkg="com.example.securekeyboard"
        assertFalse(CaptureEventPolicy.foregroundAppChanged(pkg,"$pkg.KeyboardWindow",emptySet(),inputMethodWindow=true))
        assertTrue(CaptureEventPolicy.foregroundAppChanged(pkg,"$pkg.SettingsActivity",emptySet(),inputMethodWindow=false))
    }
    @Test fun overlayFallbackUsesTopApplicationInsteadOfHiddenWechat() {
        val windows=listOf(AppWindow(1,false,false,2),AppWindow(2,false,false,4))
        assertEquals(2,ForegroundWindowSelector.select(windows,overlayActive=true)?.id)
    }
    @Test fun focusedLauncherWinsOverStaleActiveWeChatWindow() {
        val windows=listOf(AppWindow(1,false,true,4),AppWindow(2,true,false,3))
        assertEquals(2,ForegroundWindowSelector.select(windows)?.id)
    }
    @Test fun noForegroundApplicationDoesNotReuseBackgroundChat() {
        assertNull(ForegroundWindowSelector.select(listOf(AppWindow(1,false,false,2))))
    }
    @Test fun overlayContentEventsNeverRequestAnotherTreeRead() {
        assertFalse(CaptureEventPolicy.shouldObserve("com.loveadvisor.android",false,"com.loveadvisor.android"))
        assertFalse(CaptureEventPolicy.shouldObserve("com.android.systemui",false,"com.loveadvisor.android"))
        assertTrue(CaptureEventPolicy.shouldObserve("com.tencent.mm",false,"com.loveadvisor.android"))
        assertTrue(CaptureEventPolicy.shouldObserve(null,true,"com.loveadvisor.android"))
    }
    @Test fun eventStormAllowsProgressAndCoalescesOneFollowup() {
        val gate=CaptureReadGate(); val first=gate.begin()!!
        repeat(100) { assertNull(gate.begin()) }
        val done=gate.complete(first)
        assertTrue("同一导航阶段的内容事件不能令所有慢读取永远被丢弃",done.accept); assertTrue(done.readAgain)
        val latest=gate.begin()!!
        assertTrue(gate.complete(latest).accept)
    }
    @Test fun leavingChatRejectsOldReadWithoutResurrectingOverlay() {
        val gate=CaptureReadGate(); val first=gate.begin()!!; gate.invalidate()
        val done=gate.complete(first)
        assertFalse(done.accept); assertFalse(done.readAgain)
        assertTrue(gate.complete(gate.begin()!!).accept)
    }
}
