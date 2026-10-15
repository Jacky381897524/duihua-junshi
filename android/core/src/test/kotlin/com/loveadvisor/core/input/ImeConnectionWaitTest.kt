package com.loveadvisor.core.input
import org.junit.Assert.*
import org.junit.Test

class ImeConnectionWaitTest {
    @Test fun visibleKeyboardWaitsForConnectionInsteadOfFallingBackImmediately() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true))
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true))
        assertEquals(ImeConnectionStep.READY,wait.next(true,true))
    }
    @Test fun closedKeyboardFocusesAtMostOnceAndWaitingHasABound() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false))
        repeat(8) { assertEquals(ImeConnectionStep.WAIT,wait.next(false,false)) }
        assertEquals(ImeConnectionStep.FAIL,wait.next(false,false))
    }
    @Test fun failedFillFeedbackDistinguishesClipboardFallbackFromSuccessfulDraft() {
        assertEquals("填入失败：输入框未就绪；已复制，请手动粘贴",FillFeedback.failure("输入框未就绪",true))
        assertEquals("填入失败：聊天已变化",FillFeedback.failure("聊天已变化",false))
    }
}
