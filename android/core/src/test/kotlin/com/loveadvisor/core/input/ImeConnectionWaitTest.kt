package com.loveadvisor.core.input
import org.junit.Assert.*
import org.junit.Test

class ImeConnectionWaitTest {
    @Test fun visibleKeyboardWaitsForConnectionInsteadOfFallingBackImmediately() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,0))
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,150))
        assertEquals(ImeConnectionStep.READY,wait.next(true,true,300))
    }
    @Test fun inputConnectionStartingAfterTwoSecondsIsNotRejectedEarly() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false,0))
        // The Android caller polls every 150 ms. OEM keyboard startup can span these polls.
        repeat(14) { assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,it*150L)) }
        assertEquals(ImeConnectionStep.READY,wait.next(true,true,2100))
    }
    @Test fun closedKeyboardFocusesAtMostOnceAndWaitingHasABound() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false,0))
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,false,150))
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,false,4000))
        assertEquals(ImeConnectionStep.FAIL,wait.next(false,false,4150))
    }
    @Test fun pollingFrequencyDoesNotSpendTheConnectionDeadline() {
        val wait=ImeConnectionWait()
        repeat(100) { assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,0)) }
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,3999))
        assertEquals(ImeConnectionStep.FAIL,wait.next(false,true,4000))
    }
    @Test fun titleVerificationTimeDoesNotConsumePostFocusConnectionWait() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false,0))
        // Local title verification completes before the focus callback resumes polling.
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,5000))
        assertEquals(ImeConnectionStep.READY,wait.next(true,true,7100))
    }
    @Test fun cancelledFocusStillAllowsTheLateInputConnection() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false,0))
        assertEquals(ImeConnectionStep.WAIT,wait.focusFinished(ComposerFocusResult.CANCELLED,100))
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,true,1600))
        assertEquals(ImeConnectionStep.READY,wait.next(true,true,2200))
    }
    @Test fun focusFailureIsReportedOnlyAfterTheConnectionDeadline() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false,0))
        assertEquals(ImeConnectionStep.WAIT,wait.focusFinished(ComposerFocusResult.TIMED_OUT,2100))
        assertEquals(ImeConnectionStep.WAIT,wait.next(false,false,6099))
        assertEquals(ImeConnectionStep.FAIL,wait.next(false,false,6100))
        assertEquals("输入框聚焦超时，请点聊天输入框后重试",wait.failureMessage)
    }
    @Test fun changedContextCannotResumeEvenIfAnInputConnectionExists() {
        val wait=ImeConnectionWait()
        assertEquals(ImeConnectionStep.FOCUS,wait.next(false,false,0))
        assertEquals(ImeConnectionStep.FAIL,wait.focusFinished(ComposerFocusResult.CONTEXT_CHANGED,100))
        assertEquals(ImeConnectionStep.FAIL,wait.next(true,true,150))
    }
    @Test fun failedFillFeedbackDistinguishesClipboardFallbackFromSuccessfulDraft() {
        assertEquals("填入失败：输入框未就绪；已复制，请手动粘贴",FillFeedback.failure("输入框未就绪",true))
        assertEquals("填入失败：聊天已变化",FillFeedback.failure("聊天已变化",false))
    }
}
